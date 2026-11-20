package com.alis.core.upnp

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.concurrent.Executors
import kotlin.concurrent.thread

/** Renderer'ın oynatıcıya bağlandığı yer (uygulama doldurur; çağrılar ana iş parçacığına taşınmalı). */
interface RendererHost {
    fun setUri(uri: String, title: String?, mime: String?)
    fun play()
    fun pause()
    fun stop()
    fun seek(ms: Long)
    /** PLAYING | PAUSED_PLAYBACK | STOPPED | TRANSITIONING | NO_MEDIA_PRESENT */
    fun state(): String
    fun positionMs(): Long
    fun durationMs(): Long
    fun volume(): Int
    fun setVolume(v: Int)
}

/**
 * UPnP MediaRenderer (DMR): AVTransport + RenderingControl + ConnectionManager.
 * Bağımlılıksız küçük HTTP sunucusu ve SSDP duyurusu. Windows "Cihaza yayınla", BubbleUPnP gibi denetleyiciler kullanır.
 */
class Renderer(private val name: String, private val uuid: String, private val host: RendererHost, private val ip: () -> String?) {
    private var server: ServerSocket? = null
    private var ssdp: MulticastSocket? = null
    @Volatile private var running = false
    private val pool = Executors.newCachedThreadPool()
    private var currentUri = ""
    private var currentMeta = ""
    val port: Int get() = server?.localPort ?: 0

    fun start() {
        if (running) return
        running = true
        val ss = ServerSocket()
        ss.reuseAddress = true
        ss.bind(InetSocketAddress(0))
        server = ss
        thread(name = "alis-dmr-http", isDaemon = true) {
            while (running) {
                val s = try { ss.accept() } catch (_: Exception) { break }
                pool.execute { handle(s) }
            }
        }
        thread(name = "alis-dmr-ssdp", isDaemon = true) { ssdpLoop() }
        thread(name = "alis-dmr-notify", isDaemon = true) {
            var n = 0
            while (running) {
                notify("ssdp:alive")
                // İlk başta sık, sonra dakikada bir duyur
                Thread.sleep(if (n++ < 3) 3000 else 60_000)
            }
        }
    }

    fun stop() {
        if (!running) return
        runCatching { notify("ssdp:byebye") }
        running = false
        runCatching { server?.close() }
        runCatching { ssdp?.close() }
        server = null
        ssdp = null
    }

    private fun location(): String? = ip()?.let { "http://$it:$port/dmr.xml" }

    // ---------------- SSDP ----------------

    private val types get() = listOf(
        "upnp:rootdevice", "uuid:$uuid", "urn:schemas-upnp-org:device:MediaRenderer:1",
        "urn:schemas-upnp-org:service:AVTransport:1", "urn:schemas-upnp-org:service:RenderingControl:1",
        "urn:schemas-upnp-org:service:ConnectionManager:1",
    )

    private fun usn(t: String) = if (t == "uuid:$uuid") t else "uuid:$uuid::$t"

    private fun notify(nts: String) {
        val loc = location() ?: return
        val group = InetAddress.getByName("239.255.255.250")
        val sock = java.net.DatagramSocket()
        try {
            for (t in types) {
                val msg = "NOTIFY * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nCACHE-CONTROL: max-age=1800\r\nLOCATION: $loc\r\n" +
                    "NT: $t\r\nNTS: $nts\r\nSERVER: Android/10 UPnP/1.0 Alis/1.0\r\nUSN: ${usn(t)}\r\n\r\n"
                val b = msg.toByteArray()
                sock.send(DatagramPacket(b, b.size, group, 1900))
            }
        } catch (_: Exception) {
        } finally {
            sock.close()
        }
    }

    private fun ssdpLoop() {
        try {
            val ms = MulticastSocket(null)
            ms.reuseAddress = true
            ms.bind(InetSocketAddress(1900))
            val group = InetSocketAddress(InetAddress.getByName("239.255.255.250"), 1900)
            val nif = ip()?.let { a -> runCatching { NetworkInterface.getByInetAddress(InetAddress.getByName(a)) }.getOrNull() }
            ms.joinGroup(group, nif)
            ssdp = ms
            val buf = ByteArray(2048)
            while (running) {
                val p = DatagramPacket(buf, buf.size)
                try { ms.receive(p) } catch (_: Exception) { if (!running) break else continue }
                val text = String(p.data, 0, p.length)
                if (!text.startsWith("M-SEARCH")) continue
                val st = text.lineSequence().firstOrNull { it.startsWith("ST:", true) }?.substringAfter(':')?.trim() ?: continue
                val loc = location() ?: continue
                val matches = if (st == "ssdp:all") types else types.filter { it.equals(st, true) }
                for (t in matches) {
                    val msg = "HTTP/1.1 200 OK\r\nCACHE-CONTROL: max-age=1800\r\nEXT:\r\nLOCATION: $loc\r\nSERVER: Android/10 UPnP/1.0 Alis/1.0\r\n" +
                        "ST: $t\r\nUSN: ${usn(t)}\r\n\r\n"
                    val b = msg.toByteArray()
                    runCatching { ms.send(DatagramPacket(b, b.size, p.address, p.port)) }
                }
            }
        } catch (_: Exception) {
        }
    }

    // ---------------- HTTP ----------------

    private fun readLine(i: InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val c = i.read()
            if (c < 0) return if (sb.isEmpty()) null else sb.toString()
            if (c == '\n'.code) return sb.toString().trimEnd('\r')
            sb.append(c.toChar())
        }
    }

    private fun handle(s: Socket) {
        s.use { sock ->
            sock.soTimeout = 10_000
            val input = BufferedInputStream(sock.getInputStream())
            val out = sock.getOutputStream()
            val first = readLine(input) ?: return
            val parts = first.split(' ')
            if (parts.size < 2) return
            val method = parts[0]
            val path = parts[1].substringBefore('?')
            val headers = HashMap<String, String>()
            while (true) {
                val l = readLine(input) ?: break
                if (l.isEmpty()) break
                headers[l.substringBefore(':').trim().lowercase()] = l.substringAfter(':').trim()
            }
            val len = headers["content-length"]?.toIntOrNull() ?: 0
            val body = if (len > 0) {
                val b = ByteArray(len)
                var r = 0
                while (r < len) { val n = input.read(b, r, len - r); if (n < 0) break; r += n }
                String(b, 0, r, Charsets.UTF_8)
            } else ""
            when {
                method == "GET" && path == "/dmr.xml" -> send(out, 200, "text/xml; charset=\"utf-8\"", deviceXml())
                method == "GET" && path == "/avt.xml" -> send(out, 200, "text/xml; charset=\"utf-8\"", AVT_SCPD)
                method == "GET" && path == "/rc.xml" -> send(out, 200, "text/xml; charset=\"utf-8\"", RC_SCPD)
                method == "GET" && path == "/cm.xml" -> send(out, 200, "text/xml; charset=\"utf-8\"", CM_SCPD)
                method == "SUBSCRIBE" -> send(out, 200, null, "", mapOf("SID" to "uuid:" + UUID.randomUUID(), "TIMEOUT" to "Second-1800"))
                method == "UNSUBSCRIBE" -> send(out, 200, null, "")
                method == "POST" -> {
                    val action = (headers["soapaction"] ?: "").trim('"').substringAfter('#')
                    val result = runCatching { control(path, action, body) }.getOrNull()
                    if (result == null) send(out, 500, "text/xml; charset=\"utf-8\"", FAULT)
                    else send(out, 200, "text/xml; charset=\"utf-8\"", soap(action, if (path == "/ctl/rc") "RenderingControl" else if (path == "/ctl/cm") "ConnectionManager" else "AVTransport", result))
                }
                else -> send(out, 404, "text/plain", "not found")
            }
        }
    }

    private fun send(out: OutputStream, code: Int, type: String?, body: String, extra: Map<String, String> = emptyMap()) {
        val b = body.toByteArray(Charsets.UTF_8)
        val sb = StringBuilder("HTTP/1.1 $code ${if (code == 200) "OK" else "Error"}\r\n")
        if (type != null) sb.append("Content-Type: ").append(type).append("\r\n")
        sb.append("Content-Length: ").append(b.size).append("\r\nConnection: close\r\nServer: Android/10 UPnP/1.0 Alis/1.0\r\n")
        extra.forEach { (k, v) -> sb.append(k).append(": ").append(v).append("\r\n") }
        sb.append("\r\n")
        out.write(sb.toString().toByteArray())
        out.write(b)
        out.flush()
    }

    private fun arg(xml: String, n: String) = ContentDirectory.unescape(ContentDirectory.arg(xml, n)).trim()

    private fun hms(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        return "%d:%02d:%02d".format(s / 3600, (s / 60) % 60, s % 60)
    }

    private fun parseHms(t: String): Long {
        val p = t.split(':')
        if (p.size != 3) return 0
        return ((p[0].toLongOrNull() ?: 0) * 3600 + (p[1].toLongOrNull() ?: 0) * 60) * 1000 + ((p[2].toDoubleOrNull() ?: 0.0) * 1000).toLong()
    }

    private fun esc(s: String) = ContentDirectory.esc(s)

    /** @return SOAP cevabının iç XML'i; null → hata */
    private fun control(path: String, action: String, body: String): String? = when (path) {
        "/ctl/avt" -> when (action) {
            "SetAVTransportURI" -> {
                currentUri = arg(body, "CurrentURI")
                currentMeta = arg(body, "CurrentURIMetaData")
                val title = Regex("<dc:title>(.*?)</dc:title>", RegexOption.DOT_MATCHES_ALL).find(currentMeta)?.groupValues?.get(1)
                val mime = Regex("protocolInfo=\"[^:]*:[^:]*:([^:\"]+)").find(currentMeta)?.groupValues?.get(1)
                host.setUri(currentUri, title?.let { ContentDirectory.unescape(it) }, mime)
                ""
            }
            "SetNextAVTransportURI" -> ""
            "Play" -> { host.play(); "" }
            "Pause" -> { host.pause(); "" }
            "Stop" -> { host.stop(); "" }
            "Seek" -> {
                val unit = arg(body, "Unit")
                if (unit == "REL_TIME" || unit == "ABS_TIME") host.seek(parseHms(arg(body, "Target")))
                ""
            }
            "Next", "Previous" -> ""
            "GetTransportInfo" -> "<CurrentTransportState>${host.state()}</CurrentTransportState><CurrentTransportStatus>OK</CurrentTransportStatus><CurrentSpeed>1</CurrentSpeed>"
            "GetPositionInfo" -> {
                val d = hms(host.durationMs())
                val p = hms(host.positionMs())
                "<Track>1</Track><TrackDuration>$d</TrackDuration><TrackMetaData>${esc(currentMeta)}</TrackMetaData><TrackURI>${esc(currentUri)}</TrackURI>" +
                    "<RelTime>$p</RelTime><AbsTime>$p</AbsTime><RelCount>2147483647</RelCount><AbsCount>2147483647</AbsCount>"
            }
            "GetMediaInfo" -> "<NrTracks>${if (currentUri.isEmpty()) 0 else 1}</NrTracks><MediaDuration>${hms(host.durationMs())}</MediaDuration>" +
                "<CurrentURI>${esc(currentUri)}</CurrentURI><CurrentURIMetaData>${esc(currentMeta)}</CurrentURIMetaData><NextURI></NextURI><NextURIMetaData></NextURIMetaData>" +
                "<PlayMedium>NETWORK</PlayMedium><RecordMedium>NOT_IMPLEMENTED</RecordMedium><WriteStatus>NOT_IMPLEMENTED</WriteStatus>"
            "GetTransportSettings" -> "<PlayMode>NORMAL</PlayMode><RecQualityMode>NOT_IMPLEMENTED</RecQualityMode>"
            "GetCurrentTransportActions" -> "<Actions>Play,Pause,Stop,Seek</Actions>"
            "GetDeviceCapabilities" -> "<PlayMedia>NETWORK</PlayMedia><RecMedia>NOT_IMPLEMENTED</RecMedia><RecQualityModes>NOT_IMPLEMENTED</RecQualityModes>"
            else -> null
        }
        "/ctl/rc" -> when (action) {
            "GetVolume" -> "<CurrentVolume>${host.volume()}</CurrentVolume>"
            "SetVolume" -> { host.setVolume(arg(body, "DesiredVolume").toIntOrNull()?.coerceIn(0, 100) ?: 50); "" }
            "GetMute" -> "<CurrentMute>${if (host.volume() == 0) 1 else 0}</CurrentMute>"
            "SetMute" -> { if (arg(body, "DesiredMute") == "1" || arg(body, "DesiredMute") == "true") host.setVolume(0); "" }
            else -> null
        }
        "/ctl/cm" -> when (action) {
            "GetProtocolInfo" -> "<Source></Source><Sink>$SINK</Sink>"
            "GetCurrentConnectionIDs" -> "<ConnectionIDs>0</ConnectionIDs>"
            "GetCurrentConnectionInfo" -> "<RcsID>0</RcsID><AVTransportID>0</AVTransportID><ProtocolInfo></ProtocolInfo><PeerConnectionManager></PeerConnectionManager><PeerConnectionID>-1</PeerConnectionID><Direction>Input</Direction><Status>OK</Status>"
            else -> null
        }
        else -> null
    }

    private fun soap(action: String, service: String, inner: String) =
        "<?xml version=\"1.0\" encoding=\"utf-8\"?><s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">" +
            "<s:Body><u:${action}Response xmlns:u=\"urn:schemas-upnp-org:service:$service:1\">$inner</u:${action}Response></s:Body></s:Envelope>"

    private fun deviceXml() = """<?xml version="1.0" encoding="utf-8"?>
<root xmlns="urn:schemas-upnp-org:device-1-0" xmlns:dlna="urn:schemas-dlna-org:device-1-0">
<specVersion><major>1</major><minor>0</minor></specVersion>
<device>
<deviceType>urn:schemas-upnp-org:device:MediaRenderer:1</deviceType>
<friendlyName>${esc(name)}</friendlyName>
<manufacturer>Alış</manufacturer>
<modelName>Alış</modelName>
<modelDescription>Alış media renderer</modelDescription>
<UDN>uuid:$uuid</UDN>
<dlna:X_DLNADOC>DMR-1.50</dlna:X_DLNADOC>
<serviceList>
<service><serviceType>urn:schemas-upnp-org:service:AVTransport:1</serviceType><serviceId>urn:upnp-org:serviceId:AVTransport</serviceId><SCPDURL>/avt.xml</SCPDURL><controlURL>/ctl/avt</controlURL><eventSubURL>/evt/avt</eventSubURL></service>
<service><serviceType>urn:schemas-upnp-org:service:RenderingControl:1</serviceType><serviceId>urn:upnp-org:serviceId:RenderingControl</serviceId><SCPDURL>/rc.xml</SCPDURL><controlURL>/ctl/rc</controlURL><eventSubURL>/evt/rc</eventSubURL></service>
<service><serviceType>urn:schemas-upnp-org:service:ConnectionManager:1</serviceType><serviceId>urn:upnp-org:serviceId:ConnectionManager</serviceId><SCPDURL>/cm.xml</SCPDURL><controlURL>/ctl/cm</controlURL><eventSubURL>/evt/cm</eventSubURL></service>
</serviceList>
</device>
</root>"""

    companion object {
        private const val FAULT = "<?xml version=\"1.0\"?><s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\"><s:Body><s:Fault><faultcode>s:Client</faultcode><faultstring>UPnPError</faultstring><detail><UPnPError xmlns=\"urn:schemas-upnp-org:control-1-0\"><errorCode>401</errorCode><errorDescription>Invalid Action</errorDescription></UPnPError></detail></s:Fault></s:Body></s:Envelope>"

        private val SINK = listOf(
            "video/mp4", "video/x-matroska", "video/x-mkv", "video/webm", "video/mpeg", "video/mp2t", "video/quicktime", "video/x-msvideo", "video/avi", "video/3gpp",
            "audio/mpeg", "audio/mp4", "audio/aac", "audio/flac", "audio/x-flac", "audio/ogg", "audio/wav", "audio/x-wav", "audio/L16", "audio/opus",
            "image/jpeg", "image/png", "image/gif", "image/webp",
        ).joinToString(",") { "http-get:*:$it:*" }

        private fun scpd(actions: String, vars: String) =
            "<?xml version=\"1.0\"?><scpd xmlns=\"urn:schemas-upnp-org:service-1-0\"><specVersion><major>1</major><minor>0</minor></specVersion>" +
                "<actionList>$actions</actionList><serviceStateTable>$vars</serviceStateTable></scpd>"

        private fun action(name: String, ins: List<Pair<String, String>>, outs: List<Pair<String, String>>) =
            "<action><name>$name</name><argumentList>" +
                ins.joinToString("") { "<argument><name>${it.first}</name><direction>in</direction><relatedStateVariable>${it.second}</relatedStateVariable></argument>" } +
                outs.joinToString("") { "<argument><name>${it.first}</name><direction>out</direction><relatedStateVariable>${it.second}</relatedStateVariable></argument>" } +
                "</argumentList></action>"

        private fun v(name: String, type: String = "string", events: Boolean = false) =
            "<stateVariable sendEvents=\"${if (events) "yes" else "no"}\"><name>$name</name><dataType>$type</dataType></stateVariable>"

        private val ID = "InstanceID" to "A_ARG_TYPE_InstanceID"

        val AVT_SCPD = scpd(
            action("SetAVTransportURI", listOf(ID, "CurrentURI" to "AVTransportURI", "CurrentURIMetaData" to "AVTransportURIMetaData"), emptyList()) +
                action("Play", listOf(ID, "Speed" to "TransportPlaySpeed"), emptyList()) +
                action("Pause", listOf(ID), emptyList()) +
                action("Stop", listOf(ID), emptyList()) +
                action("Seek", listOf(ID, "Unit" to "A_ARG_TYPE_SeekMode", "Target" to "A_ARG_TYPE_SeekTarget"), emptyList()) +
                action("GetTransportInfo", listOf(ID), listOf("CurrentTransportState" to "TransportState", "CurrentTransportStatus" to "TransportStatus", "CurrentSpeed" to "TransportPlaySpeed")) +
                action("GetPositionInfo", listOf(ID), listOf("Track" to "CurrentTrack", "TrackDuration" to "CurrentTrackDuration", "TrackMetaData" to "CurrentTrackMetaData", "TrackURI" to "CurrentTrackURI", "RelTime" to "RelativeTimePosition", "AbsTime" to "AbsoluteTimePosition", "RelCount" to "RelativeCounterPosition", "AbsCount" to "AbsoluteCounterPosition")) +
                action("GetMediaInfo", listOf(ID), listOf("NrTracks" to "NumberOfTracks", "MediaDuration" to "CurrentMediaDuration", "CurrentURI" to "AVTransportURI", "CurrentURIMetaData" to "AVTransportURIMetaData")) +
                action("GetCurrentTransportActions", listOf(ID), listOf("Actions" to "CurrentTransportActions")),
            v("A_ARG_TYPE_InstanceID", "ui4") + v("AVTransportURI") + v("AVTransportURIMetaData") + v("TransportPlaySpeed") + v("A_ARG_TYPE_SeekMode") +
                v("A_ARG_TYPE_SeekTarget") + v("TransportState") + v("TransportStatus") + v("CurrentTrack", "ui4") + v("CurrentTrackDuration") +
                v("CurrentTrackMetaData") + v("CurrentTrackURI") + v("RelativeTimePosition") + v("AbsoluteTimePosition") + v("RelativeCounterPosition", "i4") +
                v("AbsoluteCounterPosition", "i4") + v("NumberOfTracks", "ui4") + v("CurrentMediaDuration") + v("CurrentTransportActions") + v("LastChange", events = true),
        )

        val RC_SCPD = scpd(
            action("GetVolume", listOf(ID, "Channel" to "A_ARG_TYPE_Channel"), listOf("CurrentVolume" to "Volume")) +
                action("SetVolume", listOf(ID, "Channel" to "A_ARG_TYPE_Channel", "DesiredVolume" to "Volume"), emptyList()) +
                action("GetMute", listOf(ID, "Channel" to "A_ARG_TYPE_Channel"), listOf("CurrentMute" to "Mute")) +
                action("SetMute", listOf(ID, "Channel" to "A_ARG_TYPE_Channel", "DesiredMute" to "Mute"), emptyList()),
            v("A_ARG_TYPE_InstanceID", "ui4") + v("A_ARG_TYPE_Channel") + v("Volume", "ui2") + v("Mute", "boolean") + v("LastChange", events = true),
        )

        val CM_SCPD = scpd(
            action("GetProtocolInfo", emptyList(), listOf("Source" to "SourceProtocolInfo", "Sink" to "SinkProtocolInfo")) +
                action("GetCurrentConnectionIDs", emptyList(), listOf("ConnectionIDs" to "CurrentConnectionIDs")),
            v("SourceProtocolInfo") + v("SinkProtocolInfo") + v("CurrentConnectionIDs"),
        )

        @Suppress("unused")
        private fun readAll(i: InputStream): ByteArray = ByteArrayOutputStream().also { i.copyTo(it) }.toByteArray()
    }
}
