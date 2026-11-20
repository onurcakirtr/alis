package com.alis.app

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.alis.core.data.Feature
import com.alis.core.data.GraphProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Otomatik güncelleme (Akış'takiyle aynı): GitHub'daki son sürümü denetler, APK'yı indirir,
 * release'teki SHA256SUMS.txt ile doğrular ve Android'in kurulum ekranını açar (son onay kullanıcıda).
 * GitHub'a yalnızca "son sürüm ne" sorusu gider. Android Studio'dan kurulan geliştirme sürümlerinde kapalıdır.
 */
object Updater {
    private const val REPO = "onurcakirtr/alis"
    private const val NOTIF_ID = 4700

    sealed interface State {
        data object Idle : State
        data object Checking : State
        data object UpToDate : State
        data class Available(val version: String, val apkName: String, val apkUrl: String, val sumsUrl: String?, val page: String) : State
        data class Downloading(val version: String, val progress: Float) : State
        data object Installing : State
        data class Failed(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> get() = _state

    val current: String get() = BuildConfig.VERSION_NAME.substringBefore('-')

    /** Geliştirme (debug) sürümünde denetim yok: GitHub sürümü farklı anahtarla imzalı, üzerine kurulamaz. */
    val supported: Boolean get() = !BuildConfig.DEBUG

    fun newer(a: String, b: String): Boolean {
        val x = a.split('.').map { it.toIntOrNull() ?: 0 }
        val y = b.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(x.size, y.size)) {
            val d = x.getOrElse(i) { 0 } - y.getOrElse(i) { 0 }
            if (d != 0) return d > 0
        }
        return false
    }

    private fun get(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15_000
        readTimeout = 30_000
        setRequestProperty("User-Agent", "Alis/${BuildConfig.VERSION_NAME}")
        setRequestProperty("Accept", "application/vnd.github+json")
    }

    /** @param force kullanıcı "Güncellemeleri denetle"ye bastı (ayar ve günlük sınır yok sayılır) */
    suspend fun check(ctx: Context, force: Boolean) = withContext(Dispatchers.IO) {
        if (!supported) return@withContext
        val prefs = ctx.getSharedPreferences("updater", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val enabled = (ctx.applicationContext as GraphProvider).graph.settings.current.on(Feature.UPDATE_CHECK)
        if (!force && (!enabled || now - prefs.getLong("last", 0L) < 20 * 60 * 60_000L)) return@withContext
        if (_state.value is State.Downloading || _state.value is State.Installing) return@withContext
        _state.value = State.Checking
        try {
            val c = get("https://api.github.com/repos/$REPO/releases/latest")
            if (c.responseCode != 200) throw IllegalStateException("HTTP ${c.responseCode}")
            val o = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
            prefs.edit().putLong("last", now).apply()
            val version = o.getString("tag_name").removePrefix("v")
            if (!newer(version, current)) {
                _state.value = State.UpToDate
                return@withContext
            }
            val want = "Alis-$version.apk"
            val assets = o.getJSONArray("assets")
            var apk: String? = null
            var sums: String? = null
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                when (a.getString("name")) {
                    want -> apk = a.getString("browser_download_url")
                    "SHA256SUMS.txt" -> sums = a.getString("browser_download_url")
                }
            }
            if (apk == null) {
                _state.value = State.UpToDate
                return@withContext
            }
            val announce = (_state.value as? State.Available)?.version != version
            _state.value = State.Available(version, want, apk, sums, o.optString("html_url"))
            if (announce && !force) notifyAvailable(ctx, version)
        } catch (e: Exception) {
            _state.value = if (force) State.Failed(e.message ?: e.javaClass.simpleName) else State.Idle
        }
    }

    /** "Bilinmeyen kaynaklardan yükleme" izni yoksa ayar ekranını açar ve false döner. */
    fun ensureInstallPermission(ctx: Context): Boolean {
        if (ctx.packageManager.canRequestPackageInstalls()) return true
        runCatching {
            ctx.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
        return false
    }

    suspend fun install(ctx: Context, a: State.Available) = withContext(Dispatchers.IO) {
        try {
            val dir = File(ctx.cacheDir, "update").apply { deleteRecursively(); mkdirs() }
            val file = File(dir, a.apkName)
            _state.value = State.Downloading(a.version, 0f)
            val c = get(a.apkUrl).apply { setRequestProperty("Accept", "application/octet-stream") }
            if (c.responseCode != 200) throw IllegalStateException("HTTP ${c.responseCode}")
            val total = c.contentLengthLong
            val md = MessageDigest.getInstance("SHA-256")
            c.inputStream.use { input ->
                file.outputStream().use { out ->
                    val buf = ByteArray(256 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        md.update(buf, 0, n)
                        done += n
                        if (total > 0) _state.value = State.Downloading(a.version, done.toFloat() / total)
                    }
                }
            }
            a.sumsUrl?.let { url ->
                val sums = get(url).apply { setRequestProperty("Accept", "application/octet-stream") }.inputStream.bufferedReader().use { it.readText() }
                val expected = sums.lines().firstOrNull { it.trim().endsWith(a.apkName) }?.trim()?.substringBefore(' ')?.lowercase()
                val actual = md.digest().joinToString("") { "%02x".format(it) }
                if (expected != null && expected != actual) {
                    file.delete()
                    throw IllegalStateException(ctx.getString(R.string.upd_bad_sum))
                }
            }
            _state.value = State.Installing
            val pi = ctx.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(ctx.packageName)
            }
            val id = pi.createSession(params)
            pi.openSession(id).use { session ->
                session.openWrite(a.apkName, 0, file.length()).use { out ->
                    file.inputStream().use { it.copyTo(out, 256 * 1024) }
                    session.fsync(out)
                }
                val status = PendingIntent.getBroadcast(
                    ctx, id, Intent(ctx, UpdateReceiver::class.java),
                    PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                session.commit(status.intentSender)
            }
        } catch (e: Exception) {
            _state.value = State.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    internal fun installResult(ctx: Context, intent: Intent) {
        when (val st = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                ctx.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            PackageInstaller.STATUS_SUCCESS -> _state.value = State.Idle
            else -> _state.value = if (st == PackageInstaller.STATUS_FAILURE_ABORTED) State.Idle
            else State.Failed(intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "($st)")
        }
    }

    private fun notifyAvailable(ctx: Context, version: String) {
        val launch = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName) ?: return
        val open = PendingIntent.getActivity(ctx, NOTIF_ID, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(ctx, AlisApp.CH_UPDATES)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(ctx.getString(R.string.upd_available, version))
            .setContentText(ctx.getString(R.string.upd_tap))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try { ctx.getSystemService(NotificationManager::class.java).notify(NOTIF_ID, n) } catch (_: SecurityException) {}
    }
}

class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Updater.installResult(context, intent)
}
