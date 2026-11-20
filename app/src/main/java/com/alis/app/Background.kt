package com.alis.app

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.tvprovider.media.tv.WatchNextProgram
import com.alis.app.player.PlayerActivity
import com.alis.app.ui.PlayQueue
import com.alis.core.data.AppSettings
import com.alis.core.data.Feature
import com.alis.core.data.graph
import com.alis.core.model.Item
import com.alis.core.model.Kind
import com.alis.core.player.Audio
import com.alis.core.ui.Devices
import com.alis.core.upnp.Renderer
import com.alis.core.upnp.RendererHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.Inet4Address
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Arka plan servislerini ayarlara göre açar / kapatır. */
object Background {
    fun sync(ctx: Context, s: AppSettings = ctx.graph.settings.current) {
        val app = ctx.applicationContext
        val monitor = s.on(Feature.NOTIF_MIRROR) || s.on(Feature.SERVER_STATUS_NOTIF)
        toggle(app, MonitorService::class.java, monitor)
        toggle(app, RendererService::class.java, s.on(Feature.RENDERER))
    }

    fun restartRenderer(ctx: Context) {
        val app = ctx.applicationContext
        if (!app.graph.settings.current.on(Feature.RENDERER)) return
        app.stopService(Intent(app, RendererService::class.java))
        toggle(app, RendererService::class.java, true)
    }

    private fun toggle(ctx: Context, cls: Class<out Service>, on: Boolean) {
        val i = Intent(ctx, cls)
        // Arka plandan ön plan servisi başlatma kısıtı (Android 12+): sessizce geç, uygulama açılınca tekrar denenir
        if (on) runCatching { ctx.startForegroundService(i) } else ctx.stopService(i)
    }

    /** Cihazın Wi-Fi / Ethernet IPv4 adresi. */
    fun localIp(ctx: Context): String? {
        val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return null
        val lp: LinkProperties = cm.getLinkProperties(cm.activeNetwork) ?: return null
        return lp.linkAddresses.map { it.address }.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }?.hostAddress
    }

    fun foreground(svc: Service, id: Int, n: Notification) {
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0
        runCatching { ServiceCompat.startForeground(svc, id, n, type) }
    }

    fun openApp(ctx: Context): PendingIntent {
        val launch = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName) ?: Intent()
        return PendingIntent.getActivity(ctx, 0, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}

/**
 * Bildirim yansıtma (Akış'taki bildirimler bu cihazda yerel bildirim olarak) ve
 * Akış durum bildirimi (çevrim içi / dışı, pil). İkisi de varsayılan kapalı.
 */
class MonitorService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val seen = HashMap<String, MutableSet<String>>()
    private val nm by lazy { getSystemService(NotificationManager::class.java) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Background.foreground(this, ID, status(getString(R.string.mon_starting)))
        if (!running) {
            running = true
            scope.launch { loop() }
        }
        return START_STICKY
    }

    private var running = false

    private fun status(text: String): Notification = NotificationCompat.Builder(this, AlisApp.CH_STATUS)
        .setSmallIcon(android.R.drawable.stat_sys_download_done)
        .setContentTitle(getString(R.string.mon_title))
        .setContentText(text)
        .setStyle(NotificationCompat.BigTextStyle().bigText(text))
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setContentIntent(Background.openApp(this))
        .build()

    private suspend fun loop() {
        val g = graph
        while (scope.isActive) {
            val s = g.settings.current
            if (!s.on(Feature.NOTIF_MIRROR) && !s.on(Feature.SERVER_STATUS_NOTIF)) { stopSelf(); return }
            val lines = ArrayList<String>()
            for (server in g.servers.servers.value.filter { it.isAkis && it.saved }) {
                val c = g.servers.client(server)
                val caps = runCatching { g.servers.capabilities(server, refresh = true) }.getOrNull()
                if (s.on(Feature.SERVER_STATUS_NOTIF)) {
                    val battery = if (caps?.status == true) runCatching { c.status() }.getOrNull() else null
                    lines += server.name + ": " + when {
                        caps == null -> getString(R.string.mon_offline)
                        battery != null && battery.battery >= 0 -> getString(R.string.mon_online_battery, battery.battery) + if (battery.charging) " ⚡" else ""
                        else -> getString(R.string.mon_online)
                    }
                }
                if (s.on(Feature.NOTIF_MIRROR) && caps?.notifs == true) {
                    val list = runCatching { c.notifs().second }.getOrNull() ?: continue
                    val known = seen.getOrPut(server.id) { list.map { it.key + it.time }.toMutableSet() } // ilk turda eskiler gösterilmez
                    for (n in list.filter { !it.gone }) {
                        val k = n.key + n.time
                        if (!known.add(k)) continue
                        val notif = NotificationCompat.Builder(this, AlisApp.CH_MIRROR)
                            .setSmallIcon(android.R.drawable.stat_notify_more)
                            .setContentTitle(n.app + " · " + n.title)
                            .setContentText(n.text ?: "")
                            .setSubText(server.name)
                            .setWhen(n.time)
                            .setAutoCancel(true)
                            .setGroup("mirror-" + server.id)
                            .setContentIntent(Background.openApp(this))
                            .build()
                        runCatching { nm.notify((server.id + k).hashCode(), notif) }
                    }
                }
            }
            val text = if (lines.isEmpty()) getString(R.string.mon_mirroring) else lines.joinToString("\n")
            runCatching { nm.notify(ID, status(text)) }
            delay(if (s.on(Feature.NOTIF_MIRROR)) 15_000 else 60_000)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        running = false
        super.onDestroy()
    }

    companion object {
        const val ID = 7100
    }
}

/** Renderer'dan oynatılan videonun oynatıcısı (oynatıcı ekranı kendini buraya bağlar). */
object RendererBridge {
    @Volatile
    var video: Player? = null
}

/**
 * DLNA oynatıcı (Renderer) modu: Alış ağda "oynatıcı" olarak görünür.
 * Müzik arka planda çalar; video oynatıcı ekranında açılır (Alış açık değilse bildirime dokunmak gerekir —
 * Android arka plandan ekran açılmasına izin vermez).
 */
class RendererService : Service(), RendererHost {
    private var renderer: Renderer? = null
    private val main = Handler(Looper.getMainLooper())
    private var lastAudio = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val s = graph.settings.current
        val name = s.rendererName.ifEmpty { "Alış • " + s.deviceName }
        Background.foreground(this, ID, note(getString(R.string.dmr_ready, name), null))
        if (renderer == null) {
            val prefs = getSharedPreferences("renderer", MODE_PRIVATE)
            val uuid = prefs.getString("uuid", null) ?: UUID.randomUUID().toString().also { prefs.edit().putString("uuid", it).apply() }
            renderer = Renderer(name, uuid, this) { Background.localIp(this) }.also { runCatching { it.start() } }
        }
        return START_STICKY
    }

    private fun note(text: String, tap: PendingIntent?): Notification = NotificationCompat.Builder(this, AlisApp.CH_RENDERER)
        .setSmallIcon(android.R.drawable.ic_media_play)
        .setContentTitle(getString(R.string.dmr_title))
        .setContentText(text)
        .setOngoing(true)
        .setOnlyAlertOnce(tap == null)
        .setContentIntent(tap ?: Background.openApp(this))
        .build()

    override fun onDestroy() {
        renderer?.stop()
        renderer = null
        super.onDestroy()
    }

    /** Ana iş parçacığında çalıştırıp sonucu bekler (UPnP istekleri arka plan iş parçacığından gelir). */
    private fun <T> onMain(block: () -> T): T? {
        if (Looper.myLooper() == Looper.getMainLooper()) return block()
        var r: T? = null
        val latch = CountDownLatch(1)
        main.post { r = runCatching(block).getOrNull(); latch.countDown() }
        latch.await(2, TimeUnit.SECONDS)
        return r
    }

    private fun active(): Player? = if (lastAudio) Audio.player.value else RendererBridge.video

    @SuppressLint("UnspecifiedImmutableFlag")
    override fun setUri(uri: String, title: String?, mime: String?) {
        val ext = uri.substringBefore('?').substringAfterLast('.', "").lowercase()
        val audio = mime?.startsWith("audio/") == true || ext in setOf("mp3", "m4a", "aac", "flac", "ogg", "opus", "wav")
        val image = mime?.startsWith("image/") == true || ext in setOf("jpg", "jpeg", "png", "gif", "webp")
        lastAudio = audio
        onMain {
            when {
                audio -> {
                    RendererBridge.video?.pause()
                    val mi = MediaItem.Builder().setUri(uri).setMediaMetadata(MediaMetadata.Builder().setTitle(title ?: uri.substringAfterLast('/')).build())
                        .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(Uri.parse(uri)).build()).build()
                    Audio.connect(this) { p ->
                        p.setMediaItem(mi)
                        p.prepare()
                    }
                }
                image -> {
                    val i = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(uri), mime ?: "image/*").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { startActivity(i) }
                }
                else -> {
                    Audio.pause()
                    PlayQueue.server = null
                    PlayQueue.items = listOf(Item("offline:dmr", title ?: uri.substringAfterLast('/'), Kind.VIDEO, url = uri, ext = ext))
                    PlayQueue.index = 0
                    PlayQueue.folderId = null
                    val i = Intent(this, PlayerActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    val started = runCatching { startActivity(i) }.isSuccess
                    // Arka plandan başlatılamadıysa dokununca açılsın
                    val tap = PendingIntent.getActivity(this, 1, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                    getSystemService(NotificationManager::class.java).notify(ID, note(getString(R.string.dmr_incoming, title ?: ""), tap))
                    started
                }
            }
        }
    }

    override fun play() { onMain { active()?.play() } }
    override fun pause() { onMain { active()?.pause() } }
    override fun stop() { onMain { active()?.let { it.pause(); it.seekTo(0) } } }
    override fun seek(ms: Long) { onMain { active()?.seekTo(ms) } }

    override fun state(): String = onMain {
        val p = active() ?: return@onMain "NO_MEDIA_PRESENT"
        when {
            p.playbackState == Player.STATE_BUFFERING -> "TRANSITIONING"
            p.isPlaying -> "PLAYING"
            p.playbackState == Player.STATE_IDLE || p.playbackState == Player.STATE_ENDED -> "STOPPED"
            else -> "PAUSED_PLAYBACK"
        }
    } ?: "STOPPED"

    override fun positionMs(): Long = onMain { active()?.currentPosition } ?: 0
    override fun durationMs(): Long = onMain { active()?.duration?.takeIf { it > 0 } } ?: 0

    override fun volume(): Int {
        val am = getSystemService(AudioManager::class.java)
        return am.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    }

    override fun setVolume(v: Int) {
        val am = getSystemService(AudioManager::class.java)
        am.setStreamVolume(AudioManager.STREAM_MUSIC, v * am.getStreamMaxVolume(AudioManager.STREAM_MUSIC) / 100, 0)
    }

    companion object {
        const val ID = 7200
    }
}

/** Google TV ana ekranı "Devam et" satırı (Watch Next). */
object WatchNext {
    @SuppressLint("RestrictedApi")
    fun publish(ctx: Context, serverId: String, item: Item, posMs: Long, durMs: Long) {
        if (!Devices.isTv(ctx) || !ctx.graph.settings.current.on(Feature.WATCH_NEXT)) return
        if (item.kind != Kind.VIDEO || durMs <= 0 || item.id.startsWith("offline:") || item.id.startsWith("live:")) return
        runCatching {
            val key = "$serverId|${item.id}"
            val prefs = ctx.getSharedPreferences("watchnext", Context.MODE_PRIVATE)
            val existing = prefs.getLong(key, -1)
            val finished = posMs > durMs * 0.95
            if (finished) {
                if (existing >= 0) ctx.contentResolver.delete(TvContractCompat.buildWatchNextProgramUri(existing), null, null)
                prefs.edit().remove(key).apply()
                return
            }
            val intent = Uri.parse("alis://play?s=" + Uri.encode(serverId) + "&i=" + Uri.encode(item.id))
            val b = WatchNextProgram.Builder()
                .setType(TvContractCompat.WatchNextPrograms.TYPE_MOVIE)
                .setWatchNextType(TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE)
                .setLastEngagementTimeUtcMillis(System.currentTimeMillis())
                .setTitle(item.title)
                .setDurationMillis(durMs.toInt())
                .setLastPlaybackPositionMillis(posMs.toInt())
                .setIntentUri(intent)
                .setInternalProviderId(key)
            item.thumb?.let { b.setPosterArtUri(Uri.parse(it)) }
            val values = b.build().toContentValues()
            if (existing >= 0) {
                val n = ctx.contentResolver.update(TvContractCompat.buildWatchNextProgramUri(existing), values, null, null)
                if (n > 0) return
            }
            val uri = ctx.contentResolver.insert(TvContractCompat.WatchNextPrograms.CONTENT_URI, values)
            uri?.let { prefs.edit().putLong(key, android.content.ContentUris.parseId(it)).apply() }
        }
    }
}
