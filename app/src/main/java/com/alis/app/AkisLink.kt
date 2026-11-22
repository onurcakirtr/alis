package com.alis.app

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import androidx.core.app.NotificationCompat
import com.alis.app.player.PlayerActivity
import com.alis.app.player.VideoController
import com.alis.app.ui.PlayQueue
import com.alis.core.data.Graph
import com.alis.core.model.AccessException
import com.alis.core.model.Item
import com.alis.core.model.Kind
import com.alis.core.model.Server
import com.alis.core.player.Audio
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Akış ↔ Alış kumanda bağı (Alış tarafı). Eşleşmiş her Akış'a:
 *  - sürekli bir bekleme isteği açar ve gelen komutları uygular (oynat / duraklat, sarma, parçalar, hız, aç…)
 *  - oynatırken saniyede bir, boştayken seyrek durum bildirir (Akış'ta saniye cinsinden ilerleme ve kumanda)
 * Bağlantıyı hep Alış açar; TV'de ek ayar gerekmez.
 */
object AkisLink {
    /** Şu an açık video oynatıcı (PlayerActivity kurunca kendini kaydeder). */
    @Volatile
    var video: VideoController? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val loops = HashMap<String, Job>()
    private lateinit var app: Context
    private lateinit var g: Graph

    fun start(ctx: Context, graph: Graph) {
        app = ctx.applicationContext
        g = graph
        // Eşleşen sunucular değiştikçe döngüler açılır / kapanır
        scope.launch {
            g.servers.servers.collect { list ->
                val paired = list.filter { it.isAkis && it.token != null && !it.remote }
                synchronized(loops) {
                    val ids = paired.map { it.id }.toSet()
                    loops.keys.filter { it !in ids }.forEach { loops.remove(it)?.cancel() }
                    paired.forEach { s -> if (loops[s.id]?.isActive != true) loops[s.id] = scope.launch { commandLoop(s.id) } }
                }
            }
        }
        scope.launch { stateLoop() }
    }

    private fun server(id: String): Server? = g.servers.byId(id)?.takeIf { it.token != null }

    private suspend fun commandLoop(serverId: String) {
        while (scope.isActive) {
            val s = server(serverId) ?: return
            val ok = runCatching {
                val cmds = g.servers.client(s).alisCommands()
                for (k in 0 until cmds.length()) {
                    val c = cmds.getJSONObject(k)
                    withContext(Dispatchers.Main) { handle(s, c) }
                }
                if (cmds.length() > 0) push(s)
            }
            // Akış eski sürümse (uç yok) ya da kapalıysa sık sık denemesin
            if (ok.isFailure) delay(if (ok.exceptionOrNull() is AccessException) 30_000 else 10_000)
        }
    }

    private suspend fun stateLoop() {
        var lastIdle = 0L
        while (scope.isActive) {
            val st = withContext(Dispatchers.Main) { state() }
            val active = st.optString("app") != "idle"
            val now = System.currentTimeMillis()
            if (active || now - lastIdle > 20_000) {
                if (!active) lastIdle = now
                g.servers.servers.value.filter { it.isAkis && it.token != null && !it.remote && it.online }.forEach { s ->
                    runCatching { g.servers.client(s).alisState(st) }
                }
            }
            delay(if (active) 1000 else 5000)
        }
    }

    private suspend fun push(s: Server) {
        val st = withContext(Dispatchers.Main) { state() }
        runCatching { g.servers.client(s).alisState(st) }
    }

    private fun volume(): Int {
        val am = app.getSystemService(AudioManager::class.java)
        return am.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    }

    /** Ana iş parçacığında: video oynatıcı > müzik > boşta. */
    private fun state(): JSONObject {
        video?.let { return it.remoteState().put("vol", volume()).put("device", g.settings.current.deviceName) }
        val p = Audio.player.value
        if (p != null && p.mediaItemCount > 0) {
            return JSONObject().put("app", "music")
                .put("title", p.mediaMetadata.title?.toString() ?: "")
                .put("pos", p.currentPosition).put("dur", p.duration.coerceAtLeast(0)).put("playing", p.isPlaying)
                .put("index", p.currentMediaItemIndex).put("count", p.mediaItemCount)
                .put("vol", volume()).put("device", g.settings.current.deviceName)
        }
        return JSONObject().put("app", "idle").put("vol", volume()).put("device", g.settings.current.deviceName)
    }

    private fun handle(s: Server, c: JSONObject) {
        when (c.optString("op")) {
            "open" -> open(s, c)
            "volume" -> {
                val am = app.getSystemService(AudioManager::class.java)
                am.setStreamVolume(AudioManager.STREAM_MUSIC, c.optInt("v").coerceIn(0, 100) * am.getStreamMaxVolume(AudioManager.STREAM_MUSIC) / 100, AudioManager.FLAG_SHOW_UI)
            }
            else -> {
                val v = video
                if (v != null) return v.remoteCommand(c)
                val p = Audio.player.value ?: return
                when (c.optString("op")) {
                    "toggle" -> if (p.isPlaying) p.pause() else p.play()
                    "play" -> p.play()
                    "pause" -> p.pause()
                    "seek" -> p.seekTo(c.optLong("ms"))
                    "seekBy" -> p.seekTo((p.currentPosition + c.optLong("ms")).coerceAtLeast(0))
                    "next" -> p.seekToNextMediaItem()
                    "prev" -> p.seekToPreviousMediaItem()
                    "stop" -> Audio.stop()
                }
            }
        }
    }

    /** "Bu videoyu Alış'ta aç": müzik arka planda çalar; video oynatıcıda açılır (arka plandaysa bildirime dokunulur). */
    private fun open(s: Server, c: JSONObject) {
        val url = c.optString("url").takeIf { it.startsWith("http") } ?: return
        val title = c.optString("title").ifEmpty { url.substringAfterLast('/') }
        val mime = c.optString("mime")
        if (mime.startsWith("audio/")) {
            video?.let { runCatching { it.player.pause() } }
            val item = Item("offline:akis", title, Kind.AUDIO, s.id, url = url, mime = mime)
            Audio.play(app, listOf(com.alis.core.player.Players.toMediaItem(item)), 0)
            return
        }
        Audio.pause()
        PlayQueue.server = s
        PlayQueue.items = listOf(Item("offline:akis", title, Kind.VIDEO, s.id, url = url, mime = mime.ifEmpty { null }))
        PlayQueue.index = 0
        PlayQueue.folderId = null
        val i = Intent(app, PlayerActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { app.startActivity(i) }
        // Android arka plandan ekran açmaya izin vermeyebilir: dokununca açılacak bildirim
        val tap = PendingIntent.getActivity(app, 77, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(app, AlisApp.CH_RENDERER)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(app.getString(R.string.link_open_title, s.name))
            .setContentText(title)
            .setAutoCancel(true)
            .setTimeoutAfter(120_000)
            .setContentIntent(tap)
            .build()
        runCatching { app.getSystemService(NotificationManager::class.java).notify(7300, n) }
    }
}
