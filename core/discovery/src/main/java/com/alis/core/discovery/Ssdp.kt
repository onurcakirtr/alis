package com.alis.core.discovery

import android.content.Context
import android.net.wifi.WifiManager
import com.alis.core.model.Server
import com.alis.core.model.ServerKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.URI
import java.util.concurrent.TimeUnit

/** desc.xml'den okunan cihaz bilgisi. */
data class DeviceDescription(
    val udn: String,
    val name: String,
    val manufacturer: String,
    val model: String,
    val deviceType: String,
    val icon: String?,
    val cdControl: String?,
    val avControl: String?,
    val base: String,
) {
    val isAkis: Boolean get() = manufacturer.equals("Akış", ignoreCase = true) || model.equals("Akış", ignoreCase = true)
    val isMediaServer: Boolean get() = deviceType.contains("MediaServer", ignoreCase = true) || cdControl != null

    fun toServer(): Server = Server(
        id = udn.ifEmpty { base },
        name = name.ifEmpty { base },
        base = base,
        kind = if (isAkis) ServerKind.AKIS else ServerKind.DLNA,
        icon = icon,
        cdControl = cdControl,
        online = true,
        lastSeen = System.currentTimeMillis(),
        model = model,
    )
}

/**
 * SSDP keşfi: M-SEARCH gönderir, cevap veren MediaServer'ların cihaz tanımını okur.
 * Wi-Fi'de çok noktaya yayın paketleri için MulticastLock alınır.
 */
class SsdpDiscovery(private val ctx: Context, http: OkHttpClient) {
    private val http = http.newBuilder().connectTimeout(3, TimeUnit.SECONDS).readTimeout(4, TimeUnit.SECONDS).build()

    fun search(durationMs: Long = 5000, target: String = MEDIA_SERVER): Flow<DeviceDescription> = callbackFlow {
        val wifi = ctx.applicationContext.getSystemService(WifiManager::class.java)
        val lock = wifi?.createMulticastLock("alis-ssdp")?.apply {
            setReferenceCounted(false)
            runCatching { acquire() }
        }
        val sock = DatagramSocket()
        sock.soTimeout = 400
        val seen = HashSet<String>()
        val job = launch(Dispatchers.IO) {
            val msg = ("M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 2\r\nST: $target\r\n" +
                "USER-AGENT: Android/10 UPnP/1.0 Alis/1.0\r\n\r\n").toByteArray()
            val group = InetAddress.getByName("239.255.255.250")
            val deadline = System.currentTimeMillis() + durationMs
            var sent = 0
            var nextSend = 0L
            val buf = ByteArray(4096)
            while (isActive && System.currentTimeMillis() < deadline) {
                if (sent < 3 && System.currentTimeMillis() >= nextSend) {
                    runCatching { sock.send(DatagramPacket(msg, msg.size, group, 1900)) }
                    sent++
                    nextSend = System.currentTimeMillis() + 800
                }
                val pkt = DatagramPacket(buf, buf.size)
                try {
                    sock.receive(pkt)
                } catch (_: SocketTimeoutException) {
                    continue
                } catch (_: Exception) {
                    break
                }
                val text = String(pkt.data, 0, pkt.length, Charsets.UTF_8)
                val location = header(text, "location") ?: continue
                if (!seen.add(location)) continue
                launch {
                    describe(location)?.let { if (it.isMediaServer || target != MEDIA_SERVER) trySend(it) }
                }
            }
        }
        job.invokeOnCompletion { close() }
        awaitClose {
            job.cancel()
            runCatching { sock.close() }
            runCatching { lock?.release() }
        }
    }.flowOn(Dispatchers.IO)

    /** Cihaz tanımını indirip ayrıştırır; ulaşılamazsa null. */
    fun describe(location: String): DeviceDescription? = runCatching {
        http.newCall(Request.Builder().url(location).build()).execute().use { r ->
            if (!r.isSuccessful) return null
            parse(r.body?.string() ?: return null, location)
        }
    }.getOrNull()

    companion object {
        const val MEDIA_SERVER = "urn:schemas-upnp-org:device:MediaServer:1"

        fun header(response: String, name: String): String? =
            response.lineSequence().firstOrNull { it.substringBefore(':').trim().equals(name, ignoreCase = true) }
                ?.substringAfter(':')?.trim()?.takeIf { it.isNotEmpty() }

        fun origin(url: String): String {
            val u = URI(url)
            val port = if (u.port > 0) ":${u.port}" else ""
            return "${u.scheme}://${u.host}$port"
        }

        fun resolve(base: String, ref: String?): String? {
            if (ref.isNullOrBlank()) return null
            return runCatching { URI(base).resolve(ref.trim()).toString() }.getOrNull()
        }

        /** desc.xml ayrıştırıcı: ilk (kök) cihazın adı, üreticisi, simgesi ve servis denetim adresleri. */
        fun parse(xml: String, location: String): DeviceDescription? {
            val p = XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }.newPullParser()
            p.setInput(StringReader(xml))
            var urlBase: String? = null
            var udn = ""
            var name = ""
            var manufacturer = ""
            var model = ""
            var type = ""
            var depth = 0
            var deviceDepth = -1
            val icons = ArrayList<Triple<String, Int, String>>() // url, genişlik, mime
            var iconUrl: String? = null
            var iconW = 0
            var iconMime = ""
            var inIcon = false
            var svcType = ""
            var svcCtl = ""
            var inService = false
            var cd: String? = null
            var av: String? = null
            var tag = ""
            fun local(n: String) = n.substringAfter(':')
            while (true) {
                when (p.next()) {
                    XmlPullParser.END_DOCUMENT -> break
                    XmlPullParser.START_TAG -> {
                        depth++
                        tag = local(p.name)
                        when (tag) {
                            "device" -> if (deviceDepth < 0) deviceDepth = depth
                            "icon" -> { inIcon = true; iconUrl = null; iconW = 0; iconMime = "" }
                            "service" -> { inService = true; svcType = ""; svcCtl = "" }
                        }
                    }
                    XmlPullParser.TEXT -> {
                        val t = p.text?.trim().orEmpty()
                        if (t.isEmpty()) continue
                        // Yalnızca kök cihazın kendi alanları (iç içe cihazlar atlanır)
                        val rootField = depth == deviceDepth + 1
                        when {
                            tag == "URLBase" -> urlBase = t
                            inIcon -> when (tag) {
                                "url" -> iconUrl = t
                                "width" -> iconW = t.toIntOrNull() ?: 0
                                "mimetype" -> iconMime = t
                            }
                            inService -> when (tag) {
                                "serviceType" -> svcType = t
                                "controlURL" -> svcCtl = t
                            }
                            rootField && tag == "UDN" -> udn = t
                            rootField && tag == "friendlyName" -> name = t
                            rootField && tag == "manufacturer" -> manufacturer = t
                            rootField && tag == "modelName" -> model = t
                            rootField && tag == "deviceType" -> type = t
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        when (local(p.name)) {
                            "icon" -> {
                                inIcon = false
                                iconUrl?.let { icons += Triple(it, iconW, iconMime) }
                            }
                            "service" -> {
                                inService = false
                                if (svcType.contains("ContentDirectory") && cd == null) cd = svcCtl
                                if (svcType.contains("AVTransport") && av == null) av = svcCtl
                            }
                        }
                        depth--
                        tag = ""
                    }
                }
            }
            val base = urlBase ?: location
            val icon = icons.sortedWith(compareBy({ !it.third.contains("png") }, { -it.second })).firstOrNull()?.first
            return DeviceDescription(
                udn = udn, name = name, manufacturer = manufacturer, model = model, deviceType = type,
                icon = resolve(base, icon), cdControl = resolve(base, cd), avControl = resolve(base, av),
                base = origin(location),
            )
        }
    }
}
