package com.alis.app.ui

import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alis.core.ui.DeviceUi
import com.alis.core.ui.Devices
import com.alis.core.ui.LocalDeviceUi
import com.alis.core.ui.theme.AlisTheme
import com.alis.core.ui.theme.Themes

/**
 * İki arayüzün de kökü: seçili tema, açık/koyu, cihaz bilgisi.
 * TV'de yazılar ve boşluklar 10 ft (3 m) mesafeye göre büyütülür.
 */
@Composable
fun AlisRoot(activity: ComponentActivity, tv: Boolean, content: @Composable () -> Unit) {
    val g = graph()
    val s by g.settings.state.collectAsStateWithLifecycle()
    val theme = Themes.byId(s.theme)
    val dark = theme.isDark(s.darkMode, isSystemInDarkTheme())
    DisposableEffect(dark) {
        val t = android.graphics.Color.TRANSPARENT
        val style = if (dark) SystemBarStyle.dark(t) else SystemBarStyle.light(t, t)
        activity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        onDispose { }
    }
    val ctx = LocalContext.current
    val info = remember(tv) { Devices.info(ctx).let { if (tv) it.copy(form = com.alis.core.ui.FormFactor.TV, hasCamera = false) else it } }
    val density = LocalDensity.current
    AlisTheme(theme = theme, dark = dark) {
        CompositionLocalProvider(
            LocalDeviceUi provides info,
            LocalDensity provides if (tv) Density(density.density * 1.0f, density.fontScale * 1.12f) else density,
            // Kökte Surface yok: renk verilmemiş yazı ve ikonlar temanın ön plan rengini alsın (koyu temada siyah kalmasın)
            androidx.compose.material3.LocalContentColor provides androidx.compose.material3.MaterialTheme.colorScheme.onBackground,
        ) {
            content()
        }
    }
}

/** Cihaz bilgisi (TV / tablet / telefon) için kısa yol. */
@Composable
fun device(): DeviceUi = LocalDeviceUi.current
