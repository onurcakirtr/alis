package com.alis.app.tv

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import com.alis.app.mobile.MobileActivity
import com.alis.app.ui.AlisRoot
import com.alis.app.ui.LocalNav
import com.alis.app.ui.Navigator
import com.alis.app.ui.Route
import com.alis.core.data.graph
import com.alis.core.player.Audio

/** Google TV arayüzü: mobil arayüzün ölçeklenmişi değil, kumanda için ayrı tasarım. */
class TvActivity : AppCompatActivity() {
    private val navigator = Navigator(Route.Servers)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (graph.settings.current.uiMode == "mobile") {
            startActivity(Intent(this, MobileActivity::class.java))
            finish()
            return
        }
        intent?.data?.let { com.alis.app.ui.Open.deepLink(this, it) }
        setContent {
            AlisRoot(this, tv = true) {
                CompositionLocalProvider(LocalNav provides remember { navigator }) {
                    TvApp()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.data?.let { com.alis.app.ui.Open.deepLink(this, it) }
    }

    /** Medya tuşları müzik çalarken her ekranda çalışsın. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val p = Audio.player.value
        if (p != null && p.mediaItemCount > 0) {
            when (keyCode) {
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> { if (p.isPlaying) p.pause() else p.play(); return true }
                KeyEvent.KEYCODE_MEDIA_NEXT -> { p.seekToNextMediaItem(); return true }
                KeyEvent.KEYCODE_MEDIA_PREVIOUS -> { p.seekToPreviousMediaItem(); return true }
            }
        }
        return super.onKeyDown(keyCode, event)
    }
}
