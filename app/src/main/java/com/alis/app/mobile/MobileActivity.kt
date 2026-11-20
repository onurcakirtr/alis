package com.alis.app.mobile

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.lifecycle.lifecycleScope
import com.alis.app.tv.TvActivity
import com.alis.app.ui.AlisRoot
import com.alis.app.ui.LocalNav
import com.alis.app.ui.Navigator
import com.alis.app.ui.Route
import com.alis.core.data.Feature
import com.alis.core.data.graph
import com.alis.core.player.Audio
import com.alis.core.ui.Devices
import kotlinx.coroutines.launch

/** Telefon ve tablet arayüzü. TV'de açılırsa (ayar "otomatik" ise) TV arayüzüne geçer. */
class MobileActivity : AppCompatActivity() {
    private val navigator = Navigator(Route.Servers)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mode = graph.settings.current.uiMode
        if ((mode == "auto" && Devices.isTv(this)) || mode == "tv") {
            startActivity(Intent(this, TvActivity::class.java))
            finish()
            return
        }
        handle(intent)
        setContent {
            AlisRoot(this, tv = false) {
                CompositionLocalProvider(LocalNav provides remember { navigator }) {
                    MobileApp()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    override fun onStop() {
        super.onStop()
        // "Arka planda müzik" kapalıysa uygulamadan çıkınca durur
        if (!graph.settings.current.on(Feature.BACKGROUND_AUDIO) && !isChangingConfigurations) Audio.pause()
    }

    /** alis://pair?u=http://…:8200&c=123456 — Akış'ın eşleştirme QR'ı. */
    private fun handle(i: Intent?) {
        val data = i?.data ?: return
        if (data.scheme != "alis") return
        pairFromUri(data)
    }

    fun pairFromUri(data: Uri) {
        val url = data.getQueryParameter("u") ?: return
        val code = data.getQueryParameter("c").orEmpty()
        lifecycleScope.launch {
            val s = graph.servers.addAddress(url).getOrNull() ?: return@launch
            if (code.length == 6) {
                val r = runCatching { graph.servers.client(s).pairWithCode(code, graph.settings.current.deviceName) }.getOrNull()
                if (r is com.alis.core.akis.PairResult.Ok) graph.servers.setToken(s.id, r.token)
            }
            navigator.root(Route.Servers)
            navigator.push(Route.Home(s.id))
        }
    }
}
