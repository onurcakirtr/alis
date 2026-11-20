package com.alis.app.player

import android.app.PictureInPictureParams
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.util.Rational
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.mutableStateOf
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.alis.app.ui.AlisRoot
import com.alis.app.ui.PlayQueue
import com.alis.core.data.Feature
import com.alis.core.data.graph
import com.alis.core.model.Item
import com.alis.core.model.Kind
import com.alis.core.model.Server
import com.alis.core.ui.Devices

/** Video oynatıcı (telefon, tablet ve TV). Sırayı [PlayQueue]'dan ya da dışarıdan gelen adresten alır. */
class PlayerActivity : AppCompatActivity() {
    private var ctl: VideoController? = null
    private val pip = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (!start(intent)) {
            finish()
            return
        }
        val tv = Devices.isTv(this)
        setContent {
            AlisRoot(this, tv = tv) {
                val c = ctl
                if (c != null) PlayerScreen(c, pip.value, tv, onExit = { finish() }, onPip = { enterPip() })
            }
        }
    }

    /** Oynatılacak sırayı kurar. */
    private fun start(i: Intent?): Boolean {
        ctl?.release()
        ctl = null
        val data = i?.data
        val external = i?.action == Intent.ACTION_VIEW && data != null
        val server: Server? = if (external) null else PlayQueue.server
        val folder: String? = if (external) null else PlayQueue.folderId
        val index: Int = if (external) 0 else PlayQueue.index
        val items: List<Item> = if (external && data != null) {
            val name = data.lastPathSegment?.substringAfterLast('/') ?: "Video"
            listOf(Item("offline:ext", name.substringBeforeLast('.'), Kind.VIDEO, url = data.toString(), ext = name.substringAfterLast('.', "")))
        } else {
            PlayQueue.items
        }
        if (items.isEmpty()) return false
        ctl = VideoController(this, graph, server, ArrayList(items), index.coerceIn(0, items.size - 1), folder)
        return true
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Oynatıcı açıkken başka bir video seçildi: baştan kur
        setIntent(intent)
        recreate()
    }

    fun enterPip() {
        if (!graph.settings.current.on(Feature.PIP)) return
        if (!packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)) return
        val f = ctl?.player?.videoSize
        val ratio = if (f != null && f.width > 0 && f.height > 0) Rational(f.width, f.height) else Rational(16, 9)
        val safe = if (ratio.toFloat() > 2.39f) Rational(239, 100) else if (ratio.toFloat() < 0.42f) Rational(42, 100) else ratio
        runCatching { enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(safe).build()) }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (ctl?.playing == true && !Devices.isTv(this)) enterPip()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        pip.value = isInPictureInPictureMode
    }

    override fun onStop() {
        super.onStop()
        if (!isInPictureInPictureMode) ctl?.player?.pause()
    }

    override fun onDestroy() {
        // Yön / boyut değişiklikleri etkinliği yeniden kurmaz; buraya yalnızca kapanışta ya da yeni videoda gelinir
        ctl?.release()
        ctl = null
        super.onDestroy()
    }

    /** Medya tuşları her durumda (kontroller açık / kapalı) çalışır. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val c = ctl ?: return super.dispatchKeyEvent(event)
        if (event.action == KeyEvent.ACTION_DOWN) {
            val step = graph.settings.current.seekStepSec * 1000L
            when (event.keyCode) {
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_HEADSETHOOK -> { c.toggle(); return true }
                KeyEvent.KEYCODE_MEDIA_PLAY -> { c.player.play(); return true }
                KeyEvent.KEYCODE_MEDIA_PAUSE -> { c.player.pause(); return true }
                KeyEvent.KEYCODE_MEDIA_NEXT -> { c.next(); return true }
                KeyEvent.KEYCODE_MEDIA_PREVIOUS -> { c.prev(); return true }
                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { c.seekBy(step * (1 + event.repeatCount / 4)); return true }
                KeyEvent.KEYCODE_MEDIA_REWIND -> { c.seekBy(-step * (1 + event.repeatCount / 4)); return true }
                KeyEvent.KEYCODE_MEDIA_STOP -> { finish(); return true }
                KeyEvent.KEYCODE_CAPTIONS -> {
                    if (c.subChoice == SubChoice.Off) c.externalSubs.indices.firstOrNull()?.let { c.selectExternal(it) } else c.subtitlesOff()
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }
}
