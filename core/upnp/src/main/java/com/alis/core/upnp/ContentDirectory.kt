package com.alis.core.upnp

import com.alis.core.model.Item
import com.alis.core.model.Kind
import com.alis.core.model.Subtitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.IOException
import java.io.StringReader

data class BrowseResult(
    val items: List<Item>,
    val total: Int,
    /** öğe kimliği → altyazılar (sec:CaptionInfoEx ya da metin türündeki res) */
    val subs: Map<String, List<Subtitle>> = emptyMap(),
)

/** Genel UPnP ContentDirectory istemcisi (SOAP Browse / Search). */
class ContentDirectory(private val http: OkHttpClient) {

    private val xmlType = "text/xml; charset=\"utf-8\"".toMediaType()

    private suspend fun soap(control: String, action: String, args: String): String = withContext(Dispatchers.IO) {
        val body = """<?xml version="1.0" encoding="utf-8"?>
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/"><s:Body><u:$action xmlns:u="urn:schemas-upnp-org:service:ContentDirectory:1">$args</u:$action></s:Body></s:Envelope>"""
        val req = Request.Builder().url(control)
            .header("SOAPACTION", "\"urn:schemas-upnp-org:service:ContentDirectory:1#$action\"")
            .post(body.toRequestBody(xmlType))
            .build()
        http.newCall(req).execute().use { r ->
            val text = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw IOException("SOAP ${r.code}")
            text
        }
    }

    suspend fun browse(control: String, serverId: String, objectId: String, start: Int = 0, count: Int = 500): BrowseResult {
        val xml = soap(
            control, "Browse",
            "<ObjectID>${esc(objectId)}</ObjectID><BrowseFlag>BrowseDirectChildren</BrowseFlag><Filter>*</Filter>" +
                "<StartingIndex>$start</StartingIndex><RequestedCount>$count</RequestedCount><SortCriteria></SortCriteria>",
        )
        return result(xml, serverId)
    }

    suspend fun search(control: String, serverId: String, term: String, count: Int = 300): BrowseResult {
        val q = esc(term.replace("\"", ""))
        val criteria = esc("dc:title contains \"$q\"")
        val xml = soap(
            control, "Search",
            "<ContainerID>0</ContainerID><SearchCriteria>$criteria</SearchCriteria><Filter>*</Filter>" +
                "<StartingIndex>0</StartingIndex><RequestedCount>$count</RequestedCount><SortCriteria></SortCriteria>",
        )
        return result(xml, serverId)
    }

    private fun result(xml: String, serverId: String): BrowseResult {
        val didl = arg(xml, "Result")
        val total = arg(xml, "TotalMatches").toIntOrNull() ?: 0
        val r = Didl.parse(unescape(didl), serverId)
        return r.copy(total = maxOf(total, r.items.size))
    }

    companion object {
        fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

        fun unescape(s: String) = s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&")

        fun arg(xml: String, name: String): String =
            Regex("<(?:\\w+:)?$name(?:\\s[^>]*)?>(.*?)</(?:\\w+:)?$name>", RegexOption.DOT_MATCHES_ALL).find(xml)?.groupValues?.get(1) ?: ""
    }
}

/** DIDL-Lite ayrıştırıcı. */
object Didl {
    private class Res(val url: String, val protocol: String, val size: Long, val duration: String, val resolution: String)

    fun parse(didl: String, serverId: String): BrowseResult {
        if (didl.isBlank()) return BrowseResult(emptyList(), 0)
        val p = XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }.newPullParser()
        p.setInput(StringReader(didl))
        val items = ArrayList<Item>()
        val subs = HashMap<String, List<Subtitle>>()

        var inObj = false
        var container = false
        var id = ""
        var childCount = -1
        var title = ""
        var cls = ""
        var art: String? = null
        var artist: String? = null
        var album: String? = null
        var date = ""
        val res = ArrayList<Res>()
        val caps = ArrayList<Subtitle>()
        var tag = ""
        var attrs = emptyMap<String, String>()

        fun reset() {
            id = ""; childCount = -1; title = ""; cls = ""; art = null; artist = null; album = null; date = ""
            res.clear(); caps.clear()
        }

        while (true) {
            when (p.next()) {
                XmlPullParser.END_DOCUMENT -> break
                XmlPullParser.START_TAG -> {
                    tag = p.name
                    attrs = (0 until p.attributeCount).associate { p.getAttributeName(it) to p.getAttributeValue(it) }
                    when (tag) {
                        "container", "item" -> {
                            inObj = true
                            container = tag == "container"
                            reset()
                            id = attrs["id"].orEmpty()
                            childCount = attrs["childCount"]?.toIntOrNull() ?: -1
                        }
                    }
                }
                XmlPullParser.TEXT -> if (inObj) {
                    val t = p.text?.trim().orEmpty()
                    if (t.isNotEmpty()) when (tag) {
                        "dc:title" -> title = t
                        "upnp:class" -> cls = t
                        "upnp:albumArtURI" -> if (art == null) art = t
                        "upnp:artist", "dc:creator" -> if (artist == null) artist = t
                        "upnp:album" -> album = t
                        "dc:date" -> date = t
                        "res" -> res += Res(
                            t, attrs["protocolInfo"].orEmpty(), attrs["size"]?.toLongOrNull() ?: 0,
                            attrs["duration"].orEmpty(), attrs["resolution"].orEmpty(),
                        )
                        "sec:CaptionInfoEx", "sec:CaptionInfo" -> caps += Subtitle(t, attrs["sec:type"]?.uppercase() ?: "SRT", "")
                    }
                }
                XmlPullParser.END_TAG -> {
                    val name = p.name
                    if ((name == "container" || name == "item") && inObj) {
                        inObj = false
                        val kind = when {
                            container -> Kind.FOLDER
                            cls.contains("videoItem") -> Kind.VIDEO
                            cls.contains("audioItem") -> Kind.AUDIO
                            cls.contains("imageItem") -> Kind.IMAGE
                            else -> Kind.OTHER
                        }
                        // Ana kaynak: medya türüyle uyumlu ilk res (altyazı / küçük resim değil)
                        val main = res.firstOrNull { r ->
                            val mime = mimeOf(r.protocol)
                            mime.startsWith("video/") || mime.startsWith("audio/") || (kind == Kind.IMAGE && mime.startsWith("image/"))
                        } ?: res.firstOrNull()
                        val mime = main?.let { mimeOf(it.protocol) }
                        val thumb = art ?: res.firstOrNull { it.protocol.contains("JPEG_TN") }?.url ?: if (kind == Kind.IMAGE) main?.url else null
                        val (w, h) = main?.resolution?.split('x')?.let { (it.getOrNull(0)?.toIntOrNull() ?: 0) to (it.getOrNull(1)?.toIntOrNull() ?: 0) } ?: (0 to 0)
                        val ext = main?.url?.substringBefore('?')?.substringAfterLast('/')?.substringAfterLast('.', "")?.lowercase()?.takeIf { it.length in 1..5 } ?: ""
                        items += Item(
                            id = id, title = title.ifEmpty { id }, kind = kind, serverId = serverId,
                            url = if (container) null else main?.url, thumb = thumb, mime = mime, ext = ext,
                            size = main?.size ?: 0, durationMs = parseDuration(main?.duration.orEmpty()),
                            width = w, height = h, artist = artist, album = album, date = parseDate(date),
                            childCount = childCount,
                        )
                        val textSubs = res.filter { mimeOf(it.protocol).let { m -> m.startsWith("text/") || m.contains("subrip") || m.contains("vtt") } }
                            .map { Subtitle(it.url, mimeOf(it.protocol).substringAfter('/').uppercase(), "") }
                        val all = (caps + textSubs).distinctBy { it.url }
                        if (all.isNotEmpty()) subs[id] = all
                    }
                    tag = ""
                }
            }
        }
        return BrowseResult(items, items.size, subs)
    }

    /** "http-get:*:video/mp4:DLNA.ORG_PN=…" → "video/mp4" */
    fun mimeOf(protocolInfo: String): String = protocolInfo.split(':').getOrNull(2)?.lowercase().orEmpty()

    /** "1:23:45.678" → ms */
    fun parseDuration(s: String): Long {
        if (s.isBlank()) return 0
        val parts = s.split(':')
        if (parts.size != 3) return 0
        val h = parts[0].toLongOrNull() ?: 0
        val m = parts[1].toLongOrNull() ?: 0
        val sec = parts[2].toDoubleOrNull() ?: 0.0
        return ((h * 3600 + m * 60) * 1000 + sec * 1000).toLong()
    }

    /** "2024-05-01" ya da "2024-05-01T10:00:00" → saniye */
    fun parseDate(s: String): Long {
        if (s.length < 10) return 0
        return runCatching {
            val f = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            (f.parse(s.take(10))?.time ?: 0) / 1000
        }.getOrDefault(0)
    }
}
