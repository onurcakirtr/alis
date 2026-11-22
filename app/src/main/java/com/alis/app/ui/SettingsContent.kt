package com.alis.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.BatteryStd
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Gesture
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PictureInPicture
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Slideshow
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alis.app.Background
import com.alis.app.BuildConfig
import com.alis.app.R
import com.alis.core.data.Feature
import com.alis.core.ui.ExpressiveCard
import com.alis.core.ui.FeatureRow
import com.alis.core.ui.Lang
import com.alis.core.ui.SectionTitle
import com.alis.core.ui.Segmented
import com.alis.core.ui.SettingRow
import com.alis.core.ui.ThemeGrid
import com.alis.core.ui.focusRing

/** Özellik → ikon, başlık, açıklama. */
private data class FeatureInfo(val f: Feature, val icon: ImageVector, val title: Int, val sub: Int)

private val FEATURES = listOf(
    FeatureInfo(Feature.DISCOVERY, Icons.Rounded.Radar, R.string.f_discovery, R.string.f_discovery_sub),
    FeatureInfo(Feature.GENERIC_DLNA, Icons.Rounded.Dns, R.string.f_dlna, R.string.f_dlna_sub),
    FeatureInfo(Feature.QR_ADD, Icons.Rounded.QrCodeScanner, R.string.f_qr, R.string.f_qr_sub),
    FeatureInfo(Feature.MERGED_LIBRARY, Icons.Rounded.TravelExplore, R.string.f_merged, R.string.f_merged_sub),
    FeatureInfo(Feature.RESUME_SYNC, Icons.Rounded.History, R.string.f_resume, R.string.f_resume_sub),
    FeatureInfo(Feature.SUBTITLES, Icons.Rounded.ClosedCaption, R.string.f_subs, R.string.f_subs_sub),
    FeatureInfo(Feature.BACKGROUND_AUDIO, Icons.Rounded.LibraryMusic, R.string.f_bg_audio, R.string.f_bg_audio_sub),
    FeatureInfo(Feature.PIP, Icons.Rounded.PictureInPicture, R.string.f_pip, R.string.f_pip_sub),
    FeatureInfo(Feature.GESTURES, Icons.Rounded.Gesture, R.string.f_gestures, R.string.f_gestures_sub),
    FeatureInfo(Feature.SLIDESHOW, Icons.Rounded.Slideshow, R.string.f_slideshow, R.string.f_slideshow_sub),
    FeatureInfo(Feature.DOWNLOADS, Icons.Rounded.Download, R.string.f_downloads, R.string.f_downloads_sub),
    FeatureInfo(Feature.SHARE_SEND, Icons.AutoMirrored.Rounded.Send, R.string.f_send, R.string.f_send_sub),
    FeatureInfo(Feature.FILE_MANAGER, Icons.Rounded.FolderOpen, R.string.f_files, R.string.f_files_sub),
    FeatureInfo(Feature.DOC_PREVIEW, Icons.AutoMirrored.Rounded.Article, R.string.f_docs, R.string.f_docs_sub),
    FeatureInfo(Feature.ZIP_VIEW, Icons.Rounded.FolderZip, R.string.f_zip, R.string.f_zip_sub),
    FeatureInfo(Feature.INBOX, Icons.Rounded.Inbox, R.string.f_inbox, R.string.f_inbox_sub),
    FeatureInfo(Feature.ADMIN, Icons.Rounded.AdminPanelSettings, R.string.f_admin, R.string.f_admin_sub),
    FeatureInfo(Feature.WATCH_NEXT, Icons.Rounded.Tv, R.string.f_watchnext, R.string.f_watchnext_sub),
)

private val SENSITIVE = listOf(
    FeatureInfo(Feature.PHONE_STATUS, Icons.Rounded.BatteryStd, R.string.f_status, R.string.f_status_sub),
    FeatureInfo(Feature.APPS, Icons.Rounded.Apps, R.string.f_apps, R.string.f_apps_sub),
    FeatureInfo(Feature.LIVE, Icons.Rounded.Videocam, R.string.f_live, R.string.f_live_sub),
    FeatureInfo(Feature.NOTIF_MIRROR, Icons.Rounded.Notifications, R.string.f_mirror, R.string.f_mirror_sub),
    FeatureInfo(Feature.SERVER_STATUS_NOTIF, Icons.Rounded.MonitorHeart, R.string.f_statusnotif, R.string.f_statusnotif_sub),
    FeatureInfo(Feature.RENDERER, Icons.Rounded.Cast, R.string.f_renderer, R.string.f_renderer_sub),
)

/**
 * Ayarların içeriği (mobil ve TV ortak). [columns] 2 ise bölümler iki sütuna dağılır (tablet / TV).
 */
@Composable
fun SettingsContent(columns: Int, contentPadding: PaddingValues = PaddingValues(16.dp)) {
    val g = graph()
    val ctx = LocalContext.current
    val s by g.settings.state.collectAsStateWithLifecycle()
    var editName by remember { mutableStateOf(false) }
    var editRenderer by remember { mutableStateOf(false) }
    var pickLang by remember { mutableStateOf(false) }
    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun setFeature(f: Feature, on: Boolean) {
        g.settings.setFeature(f, on)
        if (on && Build.VERSION.SDK_INT >= 33 && (f == Feature.NOTIF_MIRROR || f == Feature.SERVER_STATUS_NOTIF || f == Feature.RENDERER) &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        Background.sync(ctx, s.copy(features = s.features + (f to on)))
    }

    val appearance: @Composable () -> Unit = {
        SectionTitle(stringResource(R.string.set_appearance))
        ExpressiveCard {
            ThemeGrid(s.theme, s.darkMode, columns = if (columns > 1) 4 else 3) { g.settings.setTheme(it) }
            Spacer(Modifier.height(12.dp))
            Segmented(
                listOf("system" to stringResource(R.string.mode_system), "light" to stringResource(R.string.mode_light), "dark" to stringResource(R.string.mode_dark)),
                s.darkMode,
            ) { g.settings.setDarkMode(it) }
            Spacer(Modifier.height(8.dp))
            val langName = Lang.SUPPORTED.firstOrNull { it.first == Lang.current }?.second ?: stringResource(R.string.lang_system)
            SettingRow(Icons.Rounded.Language, stringResource(R.string.set_language), langName, onClick = { pickLang = true })
        }
    }
    val device: @Composable () -> Unit = {
        SectionTitle(stringResource(R.string.set_device))
        ExpressiveCard {
            SettingRow(Icons.Rounded.Devices, stringResource(R.string.set_device_name), s.deviceName, onClick = { editName = true })
            com.alis.core.ui.CardDivider()
            Text(stringResource(R.string.set_ui_mode), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(6.dp))
            Segmented(
                listOf("auto" to stringResource(R.string.ui_auto), "mobile" to stringResource(R.string.ui_mobile), "tv" to stringResource(R.string.ui_tv)),
                s.uiMode,
            ) { g.settings.setUiMode(it) }
            Text(stringResource(R.string.set_ui_mode_sub), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            com.alis.core.ui.CardDivider()
            FeatureRow(Icons.Rounded.Dns, stringResource(R.string.set_open_last), stringResource(R.string.set_open_last_sub), s.openLast) { g.settings.setOpenLast(it) }
        }
    }
    val player: @Composable () -> Unit = {
        SectionTitle(stringResource(R.string.set_player))
        ExpressiveCard {
            Text(stringResource(R.string.set_sub_size, (s.subSize * 100).toInt()), style = MaterialTheme.typography.bodyLarge)
            Slider(s.subSize, { g.settings.setSubSize(it) }, valueRange = 0.6f..2f, steps = 13, modifier = Modifier.focusRing(MaterialTheme.shapes.small, 1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.set_sub_bg), Modifier.weight(1f))
                Switch(s.subBackground, { g.settings.setSubBackground(it) }, modifier = Modifier.focusRing(MaterialTheme.shapes.extraLarge))
            }
            com.alis.core.ui.CardDivider()
            Text(stringResource(R.string.set_autoplay), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(6.dp))
            Segmented(
                listOf("off" to stringResource(R.string.autoplay_off), "now" to stringResource(R.string.autoplay_now), "countdown" to stringResource(R.string.autoplay_countdown)),
                s.autoplay,
            ) { g.settings.setAutoplay(it) }
            Text(stringResource(R.string.set_autoplay_sub), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            com.alis.core.ui.CardDivider()
            FeatureRow(Icons.Rounded.Schedule, stringResource(R.string.set_clock), stringResource(R.string.set_clock_sub), s.playerClock) { g.settings.setPlayerClock(it) }
            com.alis.core.ui.CardDivider()
            Text(stringResource(R.string.set_seek_step), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(6.dp))
            Segmented(listOf("5", "10", "15", "30").map { it to stringResource(R.string.seconds_n, it.toInt()) }, s.seekStepSec.toString()) { g.settings.setSeekStep(it.toInt()) }
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.set_slideshow), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(6.dp))
            Segmented(listOf("3", "5", "8", "15").map { it to stringResource(R.string.seconds_n, it.toInt()) }, s.slideshowSec.toString()) { g.settings.setSlideshow(it.toInt()) }
            com.alis.core.ui.CardDivider()
            Text(stringResource(R.string.set_langs), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(6.dp))
            val langs = listOf("" to stringResource(R.string.lang_auto), "tr" to "Türkçe", "en" to "English")
            Text(stringResource(R.string.set_audio_lang), style = MaterialTheme.typography.labelLarge)
            Segmented(langs, s.audioLang) { g.settings.setAudioLang(it) }
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.set_sub_lang), style = MaterialTheme.typography.labelLarge)
            Segmented(langs, s.subLang) { g.settings.setSubLang(it) }
        }
    }
    val features: @Composable () -> Unit = {
        SectionTitle(stringResource(R.string.set_features))
        ExpressiveCard {
            FEATURES.forEachIndexed { i, fi ->
                FeatureRow(fi.icon, stringResource(fi.title), stringResource(fi.sub), s.on(fi.f), divider = i > 0) { setFeature(fi.f, it) }
            }
        }
    }
    val rendererExtra: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit = {
        SettingRow(null, stringResource(R.string.set_renderer_name), s.rendererName.ifEmpty { "Alış • " + s.deviceName }, onClick = { editRenderer = true })
    }
    val sensitive: @Composable () -> Unit = {
        SectionTitle(stringResource(R.string.set_sensitive))
        ExpressiveCard {
            Text(stringResource(R.string.set_sensitive_sub), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SENSITIVE.forEach { fi ->
                FeatureRow(
                    fi.icon, stringResource(fi.title), stringResource(fi.sub), s.on(fi.f), divider = true,
                    extra = if (fi.f == Feature.RENDERER) rendererExtra else null,
                ) { setFeature(fi.f, it) }
            }
        }
    }
    val about: @Composable () -> Unit = {
        SectionTitle(stringResource(R.string.set_about))
        ExpressiveCard {
            SettingRow(Icons.Rounded.Info, stringResource(R.string.app_name) + " " + BuildConfig.VERSION_NAME, stringResource(R.string.about_text))
            com.alis.core.ui.CardDivider()
            AboutUpdate(s.on(Feature.UPDATE_CHECK)) { setFeature(Feature.UPDATE_CHECK, it) }
            com.alis.core.ui.CardDivider()
            Text(stringResource(R.string.about_license), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    val left = listOf(appearance, device, player)
    val right = listOf(features, sensitive, about)
    if (columns > 1) {
        LazyColumn(contentPadding = contentPadding) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) { left.forEach { it() } }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) { right.forEach { it() } }
                }
            }
        }
    } else {
        LazyColumn(contentPadding = contentPadding, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            (left + right).forEach { block -> item { block() } }
        }
    }

    if (editName) NameDialog(stringResource(R.string.set_device_name), s.deviceName, onDismiss = { editName = false }) {
        g.settings.setDeviceName(it)
        editName = false
    }
    if (editRenderer) NameDialog(stringResource(R.string.set_renderer_name), s.rendererName, onDismiss = { editRenderer = false }) {
        g.settings.setRendererName(it)
        editRenderer = false
        Background.restartRenderer(ctx)
    }
    if (pickLang) {
        AlertDialog(
            onDismissRequest = { pickLang = false },
            title = { Text(stringResource(R.string.set_language)) },
            text = {
                Column {
                    (listOf("" to stringResource(R.string.lang_system)) + Lang.SUPPORTED).forEach { (tag, name) ->
                        Row(
                            Modifier.fillMaxWidth().focusRing(MaterialTheme.shapes.small, 1f).padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(Lang.current == tag, { Lang.set(tag); pickLang = false })
                            Spacer(Modifier.width(8.dp))
                            Text(name)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickLang = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
