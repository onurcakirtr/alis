package com.alis.core.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentValues
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.alis.core.akis.AkisClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

enum class TransferState { QUEUED, RUNNING, DONE, FAILED, CANCELED }

/** Bir indirme ya da yükleme kaydı. */
data class Transfer(
    val id: String,
    val upload: Boolean,
    val name: String,
    val serverId: String,
    /** indirme: kaynak adres; yükleme: yerel içerik adresi (content://) */
    val source: String,
    val size: Long = -1,
    val done: Long = 0,
    val state: TransferState = TransferState.QUEUED,
    /** indirme: kaydedilen dosya (content://); yükleme: hedef klasör (Akış yolu) */
    val target: String? = null,
    val mime: String? = null,
    /** indirme POST gövdesi (seçilenleri ZIP: "p=…&i=…") */
    val form: String? = null,
    /** dosya isteme bağlantısı (/drop/…) */
    val drop: String? = null,
    val error: String? = null,
    val time: Long = System.currentTimeMillis(),
    /** kitaplık türü (çevrimdışı oynatma için): v / a / i */
    val kind: String = "",
)

/** Aktarım listesi (files/transfers.json) ve WorkManager işleri. */
class Transfers(private val ctx: Context, private val net: Net, private val servers: ServerRepository) {
    private val file = File(ctx.filesDir, "transfers.json")
    private val _list = MutableStateFlow(load())
    val list: StateFlow<List<Transfer>> = _list.asStateFlow()

    init {
        // Uygulama kapanırken yarım kalanlar yeniden sıraya
        _list.value.filter { it.state == TransferState.RUNNING }.forEach { update(it.id) { t -> t.copy(state = TransferState.QUEUED) } }
        createChannel(ctx)
    }

    private fun load(): List<Transfer> = runCatching {
        val a = JSONArray(file.readText())
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            Transfer(
                id = o.getString("id"), upload = o.optBoolean("up"), name = o.optString("name"), serverId = o.optString("srv"),
                source = o.optString("src"), size = o.optLong("size", -1), done = o.optLong("done"),
                state = runCatching { TransferState.valueOf(o.optString("state")) }.getOrDefault(TransferState.FAILED),
                target = o.optString("target").ifEmpty { null }, mime = o.optString("mime").ifEmpty { null },
                form = o.optString("form").ifEmpty { null }, drop = o.optString("drop").ifEmpty { null },
                error = o.optString("err").ifEmpty { null }, time = o.optLong("time"), kind = o.optString("kind"),
            )
        }
    }.getOrDefault(emptyList())

    @Synchronized
    private fun persist() {
        val a = JSONArray()
        _list.value.forEach { t ->
            a.put(
                JSONObject().put("id", t.id).put("up", t.upload).put("name", t.name).put("srv", t.serverId).put("src", t.source)
                    .put("size", t.size).put("done", t.done).put("state", t.state.name).put("target", t.target ?: "")
                    .put("mime", t.mime ?: "").put("form", t.form ?: "").put("drop", t.drop ?: "").put("err", t.error ?: "")
                    .put("time", t.time).put("kind", t.kind),
            )
        }
        runCatching { file.writeText(a.toString()) }
    }

    fun get(id: String) = _list.value.firstOrNull { it.id == id }

    @Synchronized
    fun update(id: String, f: (Transfer) -> Transfer) {
        _list.value = _list.value.map { if (it.id == id) f(it) else it }
        persist()
    }

    @Synchronized
    private fun add(t: Transfer) {
        _list.value = listOf(t) + _list.value
        persist()
        enqueue(t.id)
    }

    private fun enqueue(id: String) {
        val req = OneTimeWorkRequestBuilder<TransferWorker>()
            .setInputData(workDataOf("id" to id))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .addTag("alis-transfer")
            .build()
        WorkManager.getInstance(ctx).enqueueUniqueWork("t-$id", ExistingWorkPolicy.REPLACE, req)
    }

    fun download(serverId: String, url: String, name: String, size: Long = -1, mime: String? = null, kind: String = "", form: String? = null) =
        add(Transfer(UUID.randomUUID().toString(), false, name, serverId, url, size, mime = mime, kind = kind, form = form))

    fun upload(serverId: String, uri: Uri, name: String, size: Long, mime: String?, dir: String?, drop: String? = null) =
        add(Transfer(UUID.randomUUID().toString(), true, name, serverId, uri.toString(), size, target = dir, mime = mime, drop = drop))

    fun retry(id: String) {
        update(id) { it.copy(state = TransferState.QUEUED, error = null) }
        enqueue(id)
    }

    fun cancel(id: String) {
        WorkManager.getInstance(ctx).cancelUniqueWork("t-$id")
        update(id) { if (it.state == TransferState.DONE) it else it.copy(state = TransferState.CANCELED) }
    }

    /** Listeden kaldırır; [deleteFile] ise indirilen dosyayı da siler. */
    fun remove(id: String, deleteFile: Boolean) {
        val t = get(id) ?: return
        WorkManager.getInstance(ctx).cancelUniqueWork("t-$id")
        if (deleteFile && !t.upload) t.target?.let { runCatching { ctx.contentResolver.delete(Uri.parse(it), null, null) } }
        synchronized(this) {
            _list.value = _list.value.filter { it.id != id }
            persist()
        }
    }

    fun clearFinished() = synchronized(this) {
        _list.value = _list.value.filter { it.state == TransferState.QUEUED || it.state == TransferState.RUNNING }
        persist()
    }

    /** Çevrimdışı oynatma: bu adresten indirilmiş bitmiş dosya varsa yerel adresi. */
    fun offlineUri(url: String): Uri? = _list.value.firstOrNull { !it.upload && it.source == url && it.state == TransferState.DONE }?.target?.let { Uri.parse(it) }

    // ---------------- İşi yapan taraf (TransferWorker çağırır) ----------------

    internal suspend fun run(id: String, progress: suspend (Transfer) -> Unit): Boolean {
        val t = get(id) ?: return true
        if (t.state == TransferState.CANCELED || t.state == TransferState.DONE) return true
        update(id) { it.copy(state = TransferState.RUNNING, error = null) }
        return try {
            if (t.upload) doUpload(t, progress) else doDownload(t, progress)
            update(id) { it.copy(state = TransferState.DONE, done = if (it.size > 0) it.size else it.done) }
            true
        } catch (e: kotlinx.coroutines.CancellationException) {
            update(id) { if (it.state == TransferState.CANCELED) it else it.copy(state = TransferState.QUEUED) }
            throw e
        } catch (e: Exception) {
            update(id) { it.copy(state = TransferState.FAILED, error = e.message ?: e.javaClass.simpleName) }
            false
        }
    }

    private suspend fun doDownload(t0: Transfer, progress: suspend (Transfer) -> Unit) = withContext(Dispatchers.IO) {
        var t = t0
        val resolver = ctx.contentResolver
        var target = t.target?.let { Uri.parse(it) }
        if (target == null || runCatching { resolver.openFileDescriptor(target!!, "r")?.close() }.isFailure) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, t.name)
                put(MediaStore.Downloads.MIME_TYPE, t.mime ?: "application/octet-stream")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Alış")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            target = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: throw IOException("MediaStore")
            update(t.id) { it.copy(target = target.toString(), done = 0) }
            t = t.copy(target = target.toString(), done = 0)
        }
        val resumeFrom = if (t.form == null) t.done else 0L
        val rb = Request.Builder().url(t.source)
        val form = t.form
        if (form != null) {
            val fb = FormBody.Builder()
            form.split('&').forEach { kv ->
                val k = kv.substringBefore('=')
                val v = java.net.URLDecoder.decode(kv.substringAfter('=', ""), "UTF-8")
                if (k.isNotEmpty()) fb.add(k, v)
            }
            rb.post(fb.build())
        } else if (resumeFrom > 0) {
            rb.header("Range", "bytes=$resumeFrom-")
        }
        val client = net.client.newBuilder().readTimeout(60, TimeUnit.SECONDS).build()
        client.newCall(rb.build()).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            val partial = r.code == 206
            val start = if (partial) resumeFrom else 0L
            // Sunucunun verdiği dosya adı (paylaşım bağlantıları, ZIP'ler)
            r.header("Content-Disposition")?.let { cd ->
                val fn = Regex("""filename\*=UTF-8''([^;]+)""", RegexOption.IGNORE_CASE).find(cd)?.groupValues?.get(1)?.let { java.net.URLDecoder.decode(it, "UTF-8") }
                    ?: Regex("filename=\"?([^\";]+)\"?", RegexOption.IGNORE_CASE).find(cd)?.groupValues?.get(1)
                if (!fn.isNullOrBlank() && fn != t.name && t.done == 0L) {
                    runCatching { resolver.update(target!!, ContentValues().apply { put(MediaStore.Downloads.DISPLAY_NAME, fn) }, null, null) }
                    update(t.id) { it.copy(name = fn) }
                }
            }
            val len = r.body?.contentLength() ?: -1
            val total = if (len >= 0) start + len else t.size
            update(t.id) { it.copy(size = total, done = start) }
            val mode = if (partial) "wa" else "wt"
            resolver.openOutputStream(target!!, mode)?.use { out ->
                val input = r.body?.byteStream() ?: throw IOException("body")
                val buf = ByteArray(256 * 1024)
                var done = start
                var last = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    done += n
                    val now = System.currentTimeMillis()
                    if (now - last > 700) {
                        last = now
                        update(t.id) { it.copy(done = done) }
                        get(t.id)?.let { progress(it) }
                    }
                }
                update(t.id) { it.copy(done = done, size = if (total > 0) total else done) }
            } ?: throw IOException("write")
        }
        resolver.update(target!!, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
    }

    private suspend fun doUpload(t: Transfer, progress: suspend (Transfer) -> Unit) {
        val server = servers.byId(t.serverId)
        val uri = Uri.parse(t.source)
        val resolver = ctx.contentResolver
        var last = 0L
        val onProgress: (Long) -> Unit = { sent ->
            val now = System.currentTimeMillis()
            if (now - last > 700) {
                last = now
                update(t.id) { it.copy(done = sent) }
            }
        }
        val open = { resolver.openInputStream(uri) ?: throw IOException("read") }
        if (t.drop != null) {
            val c = AkisClient(net.client, server ?: com.alis.core.model.Server(t.drop, "", t.drop, com.alis.core.model.ServerKind.AKIS))
            c.uploadToDrop(t.drop, t.name, t.size, open, onProgress)
        } else {
            server ?: throw IOException("server")
            servers.client(server).upload(t.name, t.size, t.target, t.mime, open, onProgress)
        }
        get(t.id)?.let { progress(it) }
    }

    companion object {
        const val CHANNEL = "transfers"

        fun createChannel(ctx: Context) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(NotificationChannel(CHANNEL, ctx.getString(R.string.ch_transfers), NotificationManager.IMPORTANCE_LOW))
            }
        }
    }
}

/** Uygulamanın tek örneğine erişim (WorkManager işçileri için). */
interface TransfersProvider {
    val transfers: Transfers
}

class TransferWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    private val nid = 5000 + ((inputData.getString("id")?.hashCode() ?: 0) and 0xFFFF)

    private fun info(t: Transfer?): ForegroundInfo {
        val pct = if (t != null && t.size > 0) (t.done * 100 / t.size).toInt() else -1
        val n = NotificationCompat.Builder(applicationContext, Transfers.CHANNEL)
            .setSmallIcon(if (t?.upload == true) android.R.drawable.stat_sys_upload else android.R.drawable.stat_sys_download)
            .setContentTitle(t?.name ?: applicationContext.getString(R.string.ch_transfers))
            .setContentText(
                applicationContext.getString(if (t?.upload == true) R.string.tr_uploading else R.string.tr_downloading) +
                    if (pct >= 0) " · $pct%" else "",
            )
            .setProgress(100, pct.coerceAtLeast(0), pct < 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        return if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(nid, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else ForegroundInfo(nid, n)
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val tr = (applicationContext as TransfersProvider).transfers
        return info(inputData.getString("id")?.let { tr.get(it) })
    }

    override suspend fun doWork(): Result {
        val id = inputData.getString("id") ?: return Result.failure()
        val tr = (applicationContext as TransfersProvider).transfers
        Transfers.createChannel(applicationContext)
        runCatching { setForeground(info(tr.get(id))) }
        val ok = tr.run(id) { t -> runCatching { setForeground(info(t)) } }
        return when {
            ok -> Result.success()
            runAttemptCount < 3 && tr.get(id)?.error?.startsWith("HTTP 4") != true -> {
                tr.update(id) { it.copy(state = TransferState.QUEUED) }
                Result.retry()
            }
            else -> Result.failure()
        }
    }
}
