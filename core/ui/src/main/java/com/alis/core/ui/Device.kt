package com.alis.core.ui

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.core.os.LocaleListCompat

/** Cihaz türü: telefon / tablet aynı (mobil) arayüzü, TV ayrı bir arayüzü kullanır. */
enum class FormFactor { PHONE, TABLET, TV }

data class DeviceUi(
    val form: FormFactor = FormFactor.PHONE,
    /** Kamera var mı (QR okuma yalnızca o zaman görünür) */
    val hasCamera: Boolean = true,
    val touch: Boolean = true,
) {
    val isTv: Boolean get() = form == FormFactor.TV
}

val LocalDeviceUi = staticCompositionLocalOf { DeviceUi() }

object Devices {
    fun isTv(ctx: Context): Boolean {
        val ui = ctx.getSystemService(UiModeManager::class.java)
        if (ui?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION) return true
        val pm = ctx.packageManager
        return pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK) && !pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
    }

    fun hasCamera(ctx: Context): Boolean = ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)

    fun hasTouch(ctx: Context): Boolean = ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)

    /** En kısa kenar 600dp ve üstü tablet sayılır. */
    fun info(ctx: Context): DeviceUi {
        val tv = isTv(ctx)
        val sw = ctx.resources.configuration.smallestScreenWidthDp
        return DeviceUi(
            form = if (tv) FormFactor.TV else if (sw >= 600) FormFactor.TABLET else FormFactor.PHONE,
            hasCamera = !tv && hasCamera(ctx),
            touch = hasTouch(ctx),
        )
    }
}

/**
 * Uygulama dili. Yeni dil: app/src/main/res/values-<kod>/strings.xml (ve core modüllerindeki values-<kod>)
 * çevrilir, [SUPPORTED]'a bir satır eklenir. Android 13+ sistem dil menüsü kendiliğinden oluşur.
 */
object Lang {
    val SUPPORTED = listOf(
        "tr" to "Türkçe",
        "en" to "English",
    )

    val current: String get() = AppCompatDelegate.getApplicationLocales().toLanguageTags().substringBefore(',')

    fun set(tag: String) {
        AppCompatDelegate.setApplicationLocales(
            if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag),
        )
    }
}
