package com.alis.core.akis

import com.alis.core.model.AccessException
import com.alis.core.model.AccessState
import com.alis.core.model.AdminBucket
import com.alis.core.model.AdminClient
import com.alis.core.model.AdminFolder
import com.alis.core.model.AdminKnown
import com.alis.core.model.AdminShare
import com.alis.core.model.AdminState
import com.alis.core.model.AppInfo
import com.alis.core.model.Capabilities
import com.alis.core.model.Category
import com.alis.core.model.Crumb
import com.alis.core.model.FileDir
import com.alis.core.model.FileEntry
import com.alis.core.model.Folder
import com.alis.core.model.Home
import com.alis.core.model.InboxItem
import com.alis.core.model.Item
import com.alis.core.model.ItemDetail
import com.alis.core.model.Kind
import com.alis.core.model.NotifInfo
import com.alis.core.model.PhoneStatus
import com.alis.core.model.Server
import com.alis.core.model.Subtitle
import com.alis.core.model.Track
import com.alis.core.model.ZipEntryInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.BufferedSink
import okio.source
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** Eşleştirme sonucu. */
sealed class PairResult {
    data class Ok(val token: String, val name: String, val uuid: String) : PairResult()
    data class Pending(val id: String) : PairResult()
    data object Denied : PairResult()
    data object Failed : PairResult()
}

/** Uzaktan giriş / yönetim girişi sonucu. */
sealed class LoginResult {
    data object Ok : LoginResult()
    data class Wait(val id: String) : LoginResult()
    data class Error(val reason: String) : LoginResult()
}

/**
 * Akış'ın HTTP API'si. Çerezler (PIN, uzaktan oturum, yönetim) ve eşleştirme anahtarı
 * paylaşılan [http] istemcisinin çerez kavanozu ve [AuthInterceptor] üzerinden gider.
 */
class AkisClient(http: OkHttpClient, val server: Server) {
    private val http = http
    private val noRedirect = http.newBuilder().followRedirects(false).followSslRedirects(false).build()
    private val longPoll = http.newBuilder().readTimeout(40, TimeUnit.SECONDS).build()
    private val streaming = http.newBuilder().readTimeout(0, TimeUnit.SECONDS).writeTimeout(0, TimeUnit.SECONDS).build()
    val base: String = server.base.trimEnd('/')

    private val jsonType = "application/json; charset=utf-8".toMediaType()

    // ---------------- Adresler ----------------

    fun enc(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
    fun mediaUrl(id: String, ext: String) = "$base/m/${enc(id)}/file.${ext.ifEmpty { "bin" }}"
    fun thumbUrl(id: String) = "$base/t/${enc(id)}.jpg"
    fun remuxUrl(id: String, startSec: Double, audio: Int, transcode: Boolean) =
        "$base/x/${enc(id)}?t=${"%.2f".format(java.util.Locale.US, startSec)}&a=$audio&tx=${if (transcode) 1 else 0}"
    fun rawUrl(path: String) = "$base/raw?p=${enc(path)}"
    fun fileThumbUrl(path: String) = "$base/tf?p=${enc(path)}"
    fun zipUrl(path: String) = "$base/zip?p=${enc(path)}"
    fun zipGetUrl(path: String, entry: String) = "$base/zipget?p=${enc(path)}&e=${enc(entry)}"
    fun appIconUrl(pkg: String) = "$base/appicon?pkg=${enc(pkg)}"
    fun apkUrl(pkg: String) = "$base/apk?pkg=${enc(pkg)}"
    fun notifIconUrl(pkg: String) = "$base/nicon?pkg=${enc(pkg)}"
    fun inboxUrl(id: String) = "$base/inbox/${enc(id)}?dl=1"
    fun cameraUrl(front: Boolean, height: Int) = "$base/cam/stream?f=${if (front) "front" else "back"}&q=$height"
    fun snapshotUrl() = "$base/cam/snap"
    fun screenUrl() = "$base/live/screen"
    fun micUrl() = "$base/live/mic.wav"
    fun liveHdUrl(which: String) = "$base/m/${enc("live:$which")}/file.ts"
    fun abs(u: String) = if (u.startsWith("http")) u else base + (if (u.startsWith("/")) u else "/$u")

    // ---------------- Temel istek ----------------

    private fun accessOf(r: Response, body: String?): AccessState? {
        when (r.header("X-Akis-Access")) {
            "pin" -> return AccessState.PIN
            "pin-locked" -> return AccessState.PIN_LOCKED
            "approval" -> return AccessState.APPROVAL
            "blocked" -> return AccessState.BLOCKED
            "login" -> return AccessState.LOGIN
            "off" -> return AccessState.OFF
        }
        // Eski Akış sürümleri: başlık yok, sayfadan anlaşılır
        val html = r.header("Content-Type")?.contains("html") == true
        if (r.code == 401) return AccessState.LOGIN
        if (html && body != null) {
            if (body.contains("action=\"/pin\"") || body.contains("name=\"pin\"")) return AccessState.PIN
            if (body.contains("/rlogin")) return AccessState.LOGIN
            if (r.request.url.encodedPath.startsWith("/api/")) return AccessState.APPROVAL
        }
        if (r.code == 403 && r.request.url.encodedPath.startsWith("/api/")) return null
        return null
    }

    private suspend fun call(req: Request, client: OkHttpClient = http): String = withContext(Dispatchers.IO) {
        val r = try {
            client.newCall(req).execute()
        } catch (e: IOException) {
            throw AccessException(AccessState.OFFLINE, e.message)
        }
        r.use {
            val text = it.body?.string()
            accessOf(it, text)?.let { st -> throw AccessException(st) }
            if (!it.isSuccessful) throw IOException("HTTP ${it.code}: ${text?.take(200)}")
            text.orEmpty()
        }
    }

    private suspend fun getJson(path: String, client: OkHttpClient = http): JSONObject {
        val text = call(Request.Builder().url(base + path).build(), client)
        return try { JSONObject(text) } catch (e: Exception) { throw IOException("JSON: ${e.message}") }
    }

    private suspend fun postJson(path: String, body: JSONObject, client: OkHttpClient = http): JSONObject {
        val req = Request.Builder().url(base + path).header("X-Akis", "1").post(body.toString().toRequestBody(jsonType)).build()
        val r = withContext(Dispatchers.IO) {
            try { client.newCall(req).execute() } catch (e: IOException) { throw AccessException(AccessState.OFFLINE, e.message) }
        }
        return r.use {
            val text = it.body?.string().orEmpty()
            it.header("X-Akis-Access")?.let { _ -> accessOf(it, text)?.let { st -> throw AccessException(st) } }
            runCatching { JSONObject(text) }.getOrElse { _ -> if (it.isSuccessful) JSONObject() else throw IOException("HTTP ${it.code}") }
                .also { j -> if (!it.isSuccessful && !j.has("err") && !j.has("e") && !j.has("s") && !j.has("ok")) throw IOException("HTTP ${it.code}") }
        }
    }

    // ---------------- Yapılandırma ----------------

    suspend fun conf(): Capabilities {
        val j = try {
            getJson("/api/conf")
        } catch (e: IOException) {
            // Eski sürüm: window.AKIS = {...}; ana sayfadan
            val html = call(Request.Builder().url("$base/").build())
            val m = Regex("window\\.AKIS\\s*=\\s*(\\{.*?\\});</script>", RegexOption.DOT_MATCHES_ALL).find(html)
                ?: throw IOException("conf")
            JSONObject(m.groupValues[1])
        }
        return Capabilities(
            name = j.optString("name"),
            search = j.optBoolean("search", true),
            upload = j.optBoolean("upload"),
            history = j.optBoolean("history", true),
            subtitles = j.optBoolean("subtitles", true),
            files = j.optBoolean("files"),
            admin = j.optBoolean("admin"),
            remote = j.optBoolean("remote"),
            totp = j.optBoolean("totp"),
            status = j.optBoolean("status"),
            apps = j.optBoolean("apps"),
            camera = j.optBoolean("camera"),
            camFront = j.optBoolean("camFront"),
            camHeight = j.optInt("camHeight", 720),
            notifs = j.optBoolean("notifs"),
            inbox = j.optBoolean("inbox"),
            screen = j.optBoolean("screen"),
            mic = j.optBoolean("mic"),
            docs = j.optBoolean("docs"),
            zipView = j.optBoolean("zipView"),
            thumbs = j.optBoolean("thumbs"),
            subLang = j.optString("subLang"),
            uploadTarget = j.optString("uploadTarget"),
            fileOps = j.optBoolean("fileOps"),
            fileOpsSwitch = j.optBoolean("fileOpsSwitch"),
            fileReq = j.optBoolean("fileReq"),
            paired = j.optBoolean("paired"),
            api = j.optInt("api", 0),
        )
    }

    // ---------------- Kitaplık ----------------

    private fun kind(code: String) = when (code) {
        "f" -> Kind.FOLDER
        "v" -> Kind.VIDEO
        "a" -> Kind.AUDIO
        "i" -> Kind.IMAGE
        else -> Kind.OTHER
    }

    fun item(o: JSONObject): Item {
        val id = o.optString("id")
        val k = kind(o.optString("k"))
        val ext = o.optString("ext")
        return Item(
            id = id,
            title = o.optString("t"),
            kind = k,
            serverId = server.id,
            url = if (k == Kind.FOLDER) null else mediaUrl(id, ext),
            thumb = if (k == Kind.FOLDER) null else thumbUrl(id),
            mime = o.optString("mime").ifEmpty { null },
            ext = ext,
            size = o.optLong("sz"),
            durationMs = o.optLong("d"),
            width = o.optInt("w"),
            height = o.optInt("h"),
            artist = o.optString("ar").ifEmpty { null },
            album = o.optString("al").ifEmpty { null },
            date = o.optLong("dt"),
            childCount = o.optInt("n", -1),
            progress = o.optDouble("p", 0.0).toFloat(),
            posMs = o.optLong("pos"),
            watched = o.optBoolean("ok"),
            path = if (id.startsWith("p:")) id.substring(2) else null,
        )
    }

    private fun items(a: JSONArray?): List<Item> = if (a == null) emptyList() else (0 until a.length()).map { item(a.getJSONObject(it)) }

    private fun crumbs(a: JSONArray?): List<Crumb> =
        if (a == null) emptyList() else (0 until a.length()).map { a.getJSONObject(it).let { o -> Crumb(o.optString("id"), o.optString("t")) } }

    suspend fun home(): Home {
        val j = getJson("/api/home")
        val cats = j.optJSONArray("cats")
        return Home(
            cont = items(j.optJSONArray("cont")),
            recent = items(j.optJSONArray("recent")),
            cats = if (cats == null) emptyList() else (0 until cats.length()).map {
                cats.getJSONObject(it).let { o -> Category(o.optString("id"), o.optString("t"), o.optInt("n", -1)) }
            },
        )
    }

    suspend fun list(id: String): Folder {
        val j = getJson("/api/list?id=${enc(id)}")
        return Folder(
            id = j.optString("id", id),
            title = j.optString("t"),
            parent = if (j.isNull("parent")) null else j.optString("parent"),
            crumbs = crumbs(j.optJSONArray("crumbs")),
            items = items(j.optJSONArray("items")),
        )
    }

    suspend fun search(q: String): List<Item> = items(getJson("/api/search?q=${enc(q)}").optJSONArray("items"))

    suspend fun detail(id: String, inFolder: String = ""): ItemDetail {
        val j = getJson("/api/item?id=${enc(id)}&in=${enc(inFolder)}")
        val subs = j.optJSONArray("subs")
        return ItemDetail(
            item = item(j.getJSONObject("item")),
            subs = if (subs == null) emptyList() else (0 until subs.length()).map {
                subs.getJSONObject(it).let { o ->
                    Subtitle(abs(o.optString("u")), o.optString("l"), o.optString("lang"), o.optBoolean("emb"), o.optBoolean("forced"))
                }
            },
            resumeMs = j.optLong("resume"),
            next = j.optJSONObject("next")?.let { item(it) },
            prev = j.optJSONObject("prev")?.let { item(it) },
        )
    }

    suspend fun probe(id: String): Pair<Long, List<Track>> {
        val j = getJson("/api/probe?id=${enc(id)}")
        if (!j.optBoolean("ok")) return 0L to emptyList()
        val a = j.optJSONArray("tracks") ?: JSONArray()
        return j.optLong("d") to (0 until a.length()).map {
            a.getJSONObject(it).let { o ->
                Track(
                    o.optInt("i"), o.optBoolean("v"), o.optString("mime"), o.optString("codec"), o.optString("lang"),
                    o.optInt("ch"), o.optInt("rate"), o.optInt("w"), o.optInt("h"), o.optBoolean("tx"),
                    o.optString("name"), o.optBoolean("def"),
                )
            }
        }
    }

    /** Özellikler (EXIF, parçalar…) düz anahtar → değer listesi olarak. */
    suspend fun info(id: String? = null, path: String? = null): List<Pair<String, String>> {
        val q = if (!id.isNullOrEmpty()) "id=${enc(id)}" else "p=${enc(path.orEmpty())}"
        val out = ArrayList<Pair<String, String>>()
        fun walk(prefix: String, o: JSONObject) {
            for (k in o.keys()) {
                when (val v = o.opt(k)) {
                    is JSONObject -> walk(if (prefix.isEmpty()) k else "$prefix.$k", v)
                    is JSONArray -> out += (if (prefix.isEmpty()) k else "$prefix.$k") to (0 until v.length()).joinToString(", ") { v.opt(it).toString() }
                    null -> {}
                    else -> out += (if (prefix.isEmpty()) k else "$prefix.$k") to v.toString()
                }
            }
        }
        walk("", getJson("/api/info?$q"))
        return out
    }

    /** Kitaplık değişene kadar bekler (en çok ~25 sn); yeni sürüm numarasını döndürür. */
    suspend fun changes(since: Int): Int = getJson("/api/changes?since=$since", longPoll).optInt("v", since)

    /** İzleme konumunu bildirir (kaldığın yer senkronu). */
    suspend fun reportProgress(id: String, posSec: Double, durSec: Double) {
        withContext(Dispatchers.IO) {
            val url = "$base/h?id=${enc(id)}&t=${"%.1f".format(java.util.Locale.US, posSec)}&d=${"%.1f".format(java.util.Locale.US, durSec)}"
            runCatching { http.newCall(Request.Builder().url(url).post(ByteArray(0).toRequestBody(null)).build()).execute().close() }
        }
    }

    // ---------------- Tüm dosyalar ----------------

    suspend fun files(path: String): FileDir {
        val j = getJson("/api/files?p=${enc(path)}")
        val a = j.optJSONArray("items") ?: JSONArray()
        val c = j.optJSONArray("crumbs") ?: JSONArray()
        return FileDir(
            path = j.optString("path"),
            crumbs = (0 until c.length()).map { c.getJSONObject(it).let { o -> Crumb(o.optString("p"), o.optString("n")) } },
            items = (0 until a.length()).map {
                a.getJSONObject(it).let { o ->
                    FileEntry(
                        name = o.optString("n"), path = o.optString("p"), dir = o.optBoolean("dir"),
                        size = o.optLong("sz"), modified = o.optLong("m"), ext = o.optString("ext"),
                        kind = o.optString("k").takeIf { k -> k.isNotEmpty() }?.let { k -> kind(k) },
                        mediaId = o.optString("id").ifEmpty { null },
                    )
                }
            },
        )
    }

    suspend fun zipList(path: String): List<ZipEntryInfo> {
        val j = getJson("/api/zipls?p=${enc(path)}")
        if (j.has("err")) throw IOException(j.optString("err"))
        val a = j.optJSONArray("items") ?: JSONArray()
        return (0 until a.length()).map { a.getJSONObject(it).let { o -> ZipEntryInfo(o.optString("n"), o.optBoolean("dir"), o.optLong("sz"), o.optLong("m")) } }
    }

    /**
     * Dosya işlemi (Akış'ta eşleşmiş Alış ve bu cihaz için Dosya yönetimi izni gerekir).
     * @return başarısız öğe sayısı; izin yoksa IOException("pair" | "device" | "off")
     */
    suspend fun fileOp(op: String, paths: List<String> = emptyList(), dst: String? = null, name: String? = null): Int {
        val b = JSONObject().put("op", op).put("p", JSONArray(paths))
        dst?.let { b.put("dst", it) }
        name?.let { b.put("name", it) }
        val r = postJson("/api/fs", b)
        if (r.has("e")) throw IOException(r.optString("e"))
        return r.optInt("failed", 0)
    }

    /** Akış'a dosya gönderir (PUT /yukle). [dir] boşsa Akış'ın yükleme klasörüne. */
    suspend fun upload(name: String, length: Long, dir: String?, mime: String?, open: () -> InputStream, progress: (Long) -> Unit) {
        val type: MediaType? = (mime ?: "application/octet-stream").toMediaTypeOrNullSafe()
        val body = object : RequestBody() {
            override fun contentType() = type
            override fun contentLength() = length
            override fun writeTo(sink: BufferedSink) {
                open().use { input ->
                    val buf = ByteArray(256 * 1024)
                    var sent = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        sink.write(buf, 0, n)
                        sent += n
                        progress(sent)
                    }
                }
            }
        }
        val url = "$base/yukle/${enc(name)}" + if (!dir.isNullOrEmpty()) "?dir=${enc(dir)}" else ""
        call(Request.Builder().url(url).put(body).build(), streaming)
    }

    /** Dosya isteme bağlantısına gönderir (PUT /drop/<anahtar>/<ad>); tam adres verilir. */
    suspend fun uploadToDrop(dropUrl: String, name: String, length: Long, open: () -> InputStream, progress: (Long) -> Unit) =
        withContext(Dispatchers.IO) {
            val body = object : RequestBody() {
                override fun contentType(): MediaType? = null
                override fun contentLength() = length
                override fun writeTo(sink: BufferedSink) {
                    open().source().use { src ->
                        var sent = 0L
                        while (true) {
                            val n = src.read(sink.buffer, 256L * 1024)
                            if (n < 0) break
                            sink.emit()
                            sent += n
                            progress(sent)
                        }
                    }
                }
            }
            val url = dropUrl.trimEnd('/') + "/" + enc(name)
            streaming.newCall(Request.Builder().url(url).put(body).build()).execute().use { r ->
                if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            }
        }

    // ---------------- Telefon araçları ----------------

    suspend fun status(): PhoneStatus {
        val j = getJson("/api/status")
        val st = j.optJSONArray("storage") ?: JSONArray()
        return PhoneStatus(
            device = j.optString("device"), android = j.optString("android"),
            battery = j.optInt("battery", -1), charging = j.optBoolean("charging"),
            temp = if (j.has("temp")) j.optDouble("temp") else null,
            storage = (0 until st.length()).map { st.getJSONObject(it).let { o -> Triple(o.optString("name"), o.optLong("total"), o.optLong("free")) } },
            ramTotal = j.optLong("ramTotal"), ramFree = j.optLong("ramFree"),
            rssi = if (j.has("rssi")) j.optInt("rssi") else null,
            linkSpeed = if (j.has("linkSpeed")) j.optInt("linkSpeed") else null,
            signal = if (j.has("signal")) j.optInt("signal") else null,
            uptimeMs = j.optLong("uptime"),
        )
    }

    suspend fun apps(): List<AppInfo> {
        val a = getJson("/api/apps").optJSONArray("items") ?: JSONArray()
        return (0 until a.length()).map {
            a.getJSONObject(it).let { o ->
                AppInfo(o.optString("pkg"), o.optString("name"), o.optString("ver"), o.optLong("sz"), o.optBoolean("split"), o.optBoolean("sys"), o.optLong("upd"))
            }
        }
    }

    /** @return (sürüm, bildirimler) */
    suspend fun notifs(): Pair<Int, List<NotifInfo>> {
        val j = getJson("/api/notifs")
        val a = j.optJSONArray("items") ?: JSONArray()
        return j.optInt("v") to (0 until a.length()).map {
            a.getJSONObject(it).let { o ->
                NotifInfo(o.optString("k"), o.optString("pkg"), o.optString("app"), o.optString("title"), o.optString("text").ifEmpty { null }, o.optLong("t"), o.optBoolean("gone"))
            }
        }
    }

    suspend fun inbox(): List<InboxItem> {
        val a = getJson("/api/inbox").optJSONArray("items") ?: JSONArray()
        return (0 until a.length()).map {
            a.getJSONObject(it).let { o -> InboxItem(o.optString("id"), o.optString("n"), o.optLong("sz"), o.optString("m"), o.optString("from"), o.optLong("t")) }
        }
    }

    // ---------------- Erişim akışları ----------------

    /** Web PIN'i: doğruysa akis_pin çerezi kaydedilir. */
    suspend fun pin(pin: String): Boolean = withContext(Dispatchers.IO) {
        val req = Request.Builder().url("$base/pin").post(FormBody.Builder().add("pin", pin).build()).build()
        try {
            noRedirect.newCall(req).execute().use { it.code == 303 || it.code == 302 }
        } catch (e: IOException) {
            throw AccessException(AccessState.OFFLINE, e.message)
        }
    }

    /** Uzaktan giriş (tünel): şifre + isteğe bağlı iki adımlı kod. */
    suspend fun remoteLogin(password: String, code: String): LoginResult = withContext(Dispatchers.IO) {
        val req = Request.Builder().url("$base/rlogin").post(FormBody.Builder().add("pw", password).add("code", code).build()).build()
        try {
            noRedirect.newCall(req).execute().use { r ->
                if (r.code == 303 || r.code == 302) return@use LoginResult.Ok
                val html = r.body?.string().orEmpty()
                val wait = Regex("rlogin/status\\?r=([A-Za-z0-9_-]+)").find(html)?.groupValues?.get(1)
                when {
                    wait != null -> LoginResult.Wait(wait)
                    html.contains("locked", ignoreCase = true) -> LoginResult.Error("locked")
                    else -> LoginResult.Error("wrong")
                }
            }
        } catch (e: IOException) {
            throw AccessException(AccessState.OFFLINE, e.message)
        }
    }

    /** "ok" (çerez kaydedildi) | "pending" | diğerleri hata */
    suspend fun remoteLoginStatus(id: String): String =
        runCatching { JSONObject(call(Request.Builder().url("$base/rlogin/status?r=${enc(id)}").build())).optString("s") }.getOrDefault("error")

    private fun pairResult(j: JSONObject): PairResult = when {
        j.optBoolean("ok") && j.optString("token").isNotEmpty() -> PairResult.Ok(j.optString("token"), j.optString("name"), j.optString("uuid"))
        j.optString("s") == "pending" -> PairResult.Pending(j.optString("r"))
        j.optString("s") == "denied" -> PairResult.Denied
        else -> PairResult.Failed
    }

    /** Kodsuz eşleştirme: telefona onay bildirimi gider (ya da "bu ağa güveniyorum" açıksa hemen anahtar). */
    suspend fun pairRequest(deviceName: String): PairResult = pairResult(postJson("/pair/request", JSONObject().put("name", deviceName)))

    suspend fun pairStatus(id: String): PairResult = pairResult(postJson("/pair/status?r=${enc(id)}", JSONObject()))

    /** Akış'ta gösterilen 6 haneli kodla eşleştirme. */
    suspend fun pairWithCode(code: String, deviceName: String): PairResult =
        pairResult(postJson("/pair", JSONObject().put("code", code).put("name", deviceName)))

    // ---------------- Yönetim ----------------

    suspend fun adminLogin(password: String, code: String): LoginResult {
        val j = postJson("/admin/login", JSONObject().put("pw", password).put("code", code))
        return if (j.has("r")) LoginResult.Wait(j.optString("r")) else LoginResult.Error(j.optString("err", "wrong"))
    }

    /** "ok" | "pending" | "denied" | "expired" */
    suspend fun adminStatus(id: String): String = runCatching { getJson("/admin/status?r=${enc(id)}").optString("s") }.getOrDefault("error")

    suspend fun adminLoggedIn(): Boolean = runCatching {
        withContext(Dispatchers.IO) { http.newCall(Request.Builder().url("$base/admin/state").build()).execute().use { it.code == 200 } }
    }.getOrDefault(false)

    suspend fun adminState(): AdminState {
        val j = getJson("/admin/state")
        val sw = j.optJSONObject("switches") ?: JSONObject()
        fun <T> arr(name: String, f: (JSONObject) -> T): List<T> {
            val a = j.optJSONArray(name) ?: return emptyList()
            return (0 until a.length()).map { f(a.getJSONObject(it)) }
        }
        val langs = j.optJSONArray("langs")
        return AdminState(
            name = j.optString("name"),
            switches = sw.keys().asSequence().associateWith { sw.optBoolean(it) },
            subLang = j.optString("subLang"),
            langs = if (langs == null) emptyList() else (0 until langs.length()).map { langs.optString(it) },
            folders = arr("folders") { AdminFolder(it.optString("uri"), it.optString("name"), it.optBoolean("path")) },
            buckets = arr("buckets") { AdminBucket(it.optString("id"), it.optString("name"), it.optInt("n"), it.optBoolean("hidden")) },
            clients = arr("clients") { AdminClient(it.optString("ip"), it.optString("name"), it.optLong("last")) },
            known = arr("known") { AdminKnown(it.optString("ip"), it.optString("name"), it.optBoolean("allowed"), it.optLong("until")) },
            shares = arr("shares") { AdminShare(it.optString("token"), it.optString("name"), it.optLong("expires"), it.optBoolean("once"), it.optInt("dl")) },
            filesGranted = j.optBoolean("filesGranted"),
            ring = j.optBoolean("ring"),
            ringing = j.optBoolean("ringing"),
            me = j.optString("me"),
            now = j.optLong("now"),
        )
    }

    /** Klasör seçici (yalnızca klasörler): (yol, üst, [ad → yol]) */
    suspend fun adminDirs(path: String): Triple<String, String, List<Pair<String, String>>> {
        val j = getJson("/admin/dirs?p=${enc(path)}")
        val a = j.optJSONArray("items") ?: JSONArray()
        return Triple(j.optString("path"), j.optString("parent"), (0 until a.length()).map { a.getJSONObject(it).let { o -> o.optString("n") to o.optString("p") } })
    }

    /** POST /admin/do — op: set, hide, folderAdd, folderRemove, kick, ban, unblock, shareClose, ring, ringStop */
    suspend fun adminDo(body: JSONObject) {
        val r = postJson("/admin/do", body)
        if (r.has("err")) throw IOException(r.optString("err"))
    }

    // ---------------- Kumanda bağı (Akış → Alış) ----------------

    /** Akış'ta verilen komutları bekler (en çok ~25 sn). Eşleşme yoksa AccessException / IOException. */
    suspend fun alisCommands(): JSONArray = getJson("/alis/cmd?wait=25", longPoll).optJSONArray("cmds") ?: JSONArray()

    /** Oynatıcı durumunu Akış'a bildirir. */
    suspend fun alisState(state: JSONObject) {
        runCatching { postJson("/alis/state", state) }
    }

    suspend fun adminLogout() {
        runCatching { postJson("/admin/logout", JSONObject()) }
    }
}

private fun String.toMediaTypeOrNullSafe(): MediaType? = runCatching { this.toMediaType() }.getOrNull()
