package com.alis.core.player

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionToken
import com.alis.core.data.graph
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Müzik: arka planda çalma, bildirim ve kilit ekranı kontrolleri, kulaklık / medya tuşları.
 * Bildirimi Media3 kendisi yönetir (ön plan türü mediaPlayback).
 */
class AudioService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = Players.exo(this, graph.net.client, audioOnly = true)
        val launch = packageManager.getLaunchIntentForPackage(packageName)?.apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP }
        val b = MediaSession.Builder(this, player).setCallback(object : MediaSession.Callback {
            // Denetleyiciden gelen öğelerde adres (localConfiguration) taşınmaz; requestMetadata'dan geri kurulur
            override fun onAddMediaItems(
                mediaSession: MediaSession,
                controller: MediaSession.ControllerInfo,
                mediaItems: MutableList<MediaItem>,
            ): ListenableFuture<MutableList<MediaItem>> = Futures.immediateFuture(
                mediaItems.map { m ->
                    val uri = m.localConfiguration?.uri ?: m.requestMetadata.mediaUri
                    if (uri == null) m else m.buildUpon().setUri(uri).build()
                }.toMutableList(),
            )
        })
        if (launch != null) b.setSessionActivity(PendingIntent.getActivity(this, 0, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        session = b.build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }
}

/** Arayüzün müzik oynatıcısına bağlantısı (MediaController). */
object Audio {
    private var controller: MediaController? = null
    private var connecting = false
    private val _player = MutableStateFlow<Player?>(null)
    val player: StateFlow<Player?> = _player.asStateFlow()

    /** Basit durum özeti: çalan şarkı ve oynatılıyor mu (çubuğu göstermek için). */
    data class Now(val item: MediaItem?, val playing: Boolean, val index: Int, val count: Int, val shuffle: Boolean, val repeat: Int)

    private val _now = MutableStateFlow(Now(null, false, 0, 0, false, Player.REPEAT_MODE_OFF))
    val now: StateFlow<Now> = _now.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = publish(player)
    }

    private fun publish(p: Player) {
        _now.value = Now(p.currentMediaItem, p.isPlaying, p.currentMediaItemIndex, p.mediaItemCount, p.shuffleModeEnabled, p.repeatMode)
    }

    fun connect(ctx: Context, then: ((Player) -> Unit)? = null) {
        controller?.let { c -> then?.invoke(c); return }
        if (connecting) {
            if (then != null) pending += then
            return
        }
        connecting = true
        if (then != null) pending += then
        val app = ctx.applicationContext
        val f = MediaController.Builder(app, SessionToken(app, ComponentName(app, AudioService::class.java))).buildAsync()
        f.addListener({
            connecting = false
            val c = runCatching { f.get() }.getOrNull() ?: return@addListener
            controller = c
            c.addListener(listener)
            _player.value = c
            publish(c)
            pending.forEach { it(c) }
            pending.clear()
        }, ContextCompat.getMainExecutor(app))
    }

    private val pending = ArrayList<(Player) -> Unit>()

    /** Bir sırayı baştan çalar. */
    fun play(ctx: Context, items: List<MediaItem>, start: Int, shuffle: Boolean = false) = connect(ctx) { p ->
        p.setMediaItems(items, start.coerceIn(0, (items.size - 1).coerceAtLeast(0)), 0)
        p.shuffleModeEnabled = shuffle
        p.prepare()
        p.play()
    }

    /** Sıranın sonuna ekler (çoklu seçim → müzik sırasına ekle). */
    fun enqueue(ctx: Context, items: List<MediaItem>) = connect(ctx) { p ->
        val empty = p.mediaItemCount == 0
        p.addMediaItems(items)
        if (empty) {
            p.prepare()
            p.play()
        }
    }

    fun stop() {
        controller?.run {
            stop()
            clearMediaItems()
        }
    }

    fun pause() {
        controller?.pause()
    }
}
