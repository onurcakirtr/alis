package com.alis.core.data

import android.content.Context
import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first

/**
 * Özellik düğmeleri: her özellik Ayarlar → Özellikler'den ayrı ayrı açılıp kapanır.
 * Hassas olanlar ([sensitive]) varsayılan kapalıdır.
 */
enum class Feature(val key: String, val default: Boolean, val sensitive: Boolean = false) {
    DISCOVERY("discovery", true),
    GENERIC_DLNA("genericDlna", true),
    QR_ADD("qrAdd", true),
    RESUME_SYNC("resumeSync", true),
    AUTOPLAY_NEXT("autoplayNext", true),
    SUBTITLES("subtitles", true),
    BACKGROUND_AUDIO("backgroundAudio", true),
    PIP("pip", true),
    GESTURES("gestures", true),
    SLIDESHOW("slideshow", true),
    DOWNLOADS("downloads", true),
    SHARE_SEND("shareSend", true),
    FILE_MANAGER("fileManager", true),
    DOC_PREVIEW("docPreview", true),
    ZIP_VIEW("zipView", true),
    MERGED_LIBRARY("mergedLibrary", true),
    INBOX("inbox", true),
    ADMIN("admin", true),
    WATCH_NEXT("watchNext", true),
    /** Günde bir kez GitHub'da yeni sürüm denetimi (Ayarlar → Hakkında) */
    UPDATE_CHECK("updateCheck", true),
    PHONE_STATUS("phoneStatus", false, true),
    APPS("apps", false, true),
    LIVE("live", false, true),
    NOTIF_MIRROR("notifMirror", false, true),
    SERVER_STATUS_NOTIF("serverStatusNotif", false, true),
    RENDERER("renderer", false, true),
}

data class AppSettings(
    val theme: String = "material",
    val darkMode: String = "system",
    val onboarded: Boolean = false,
    val features: Map<Feature, Boolean> = Feature.entries.associateWith { it.default },
    val deviceName: String = Build.MODEL,
    /** "grid" | "list" */
    val view: String = "grid",
    /** "name" | "date" | "size" */
    val sort: String = "name",
    val sortDesc: Boolean = false,
    val subSize: Float = 1f,
    val subBackground: Boolean = true,
    val subLang: String = "",
    val audioLang: String = "",
    val seekStepSec: Int = 10,
    val slideshowSec: Int = 5,
    val lastServer: String = "",
    val rendererName: String = "",
    /** Telefonu TV arayüzüyle (ya da tersi) açmaya zorla: "auto" | "tv" | "mobile" */
    val uiMode: String = "auto",
    /** Video bitince: "off" kapalı | "now" hemen | "countdown" 5 sn geri sayımla (YouTube gibi) */
    val autoplay: String = "countdown",
    /** Açılışta son kullanılan sunucuyu doğrudan aç */
    val openLast: Boolean = true,
    /** Dosya yöneticisi yer imleri: sunucu kimliği → klasör yolları */
    val bookmarks: Map<String, List<String>> = emptyMap(),
) {
    fun on(f: Feature): Boolean = features[f] ?: f.default
}

private val Context.store: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** DataStore tabanlı ayar deposu. [state] ilk değeri senkron okunur (açılışta tema yanıp sönmesin). */
class SettingsStore(private val ctx: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val ds = ctx.store

    private fun read(p: Preferences) = AppSettings(
        theme = p[K_THEME] ?: "material",
        darkMode = p[K_DARK] ?: "system",
        onboarded = p[K_ONBOARDED] ?: false,
        features = Feature.entries.associateWith { f -> p[booleanPreferencesKey("f_" + f.key)] ?: f.default },
        deviceName = p[K_DEVICE]?.takeIf { it.isNotBlank() } ?: Build.MODEL,
        view = p[K_VIEW] ?: "grid",
        sort = p[K_SORT] ?: "name",
        sortDesc = p[K_SORT_DESC] ?: false,
        subSize = p[K_SUB_SIZE] ?: 1f,
        subBackground = p[K_SUB_BG] ?: true,
        subLang = p[K_SUB_LANG] ?: "",
        audioLang = p[K_AUDIO_LANG] ?: "",
        seekStepSec = p[K_SEEK] ?: 10,
        slideshowSec = p[K_SLIDE] ?: 5,
        lastServer = p[K_LAST] ?: "",
        rendererName = p[K_RENDERER] ?: "",
        uiMode = p[K_UI] ?: "auto",
        autoplay = p[K_AUTOPLAY] ?: (if (p[booleanPreferencesKey("f_autoplayNext")] == false) "off" else "countdown"),
        openLast = p[K_OPEN_LAST] ?: true,
        bookmarks = runCatching {
            val o = org.json.JSONObject(p[K_BOOKMARKS] ?: "{}")
            o.keys().asSequence().associateWith { k -> o.getJSONArray(k).let { a -> (0 until a.length()).map { a.getString(it) } } }
        }.getOrDefault(emptyMap()),
    )

    val state: StateFlow<AppSettings> = ds.data.map { read(it) }
        .stateIn(scope, SharingStarted.Eagerly, runBlocking { read(ds.data.first()) })

    val current: AppSettings get() = state.value

    fun setFeature(f: Feature, on: Boolean) = scope.launch { ds.edit { it[booleanPreferencesKey("f_" + f.key)] = on } }
    fun setTheme(id: String) = scope.launch { ds.edit { it[K_THEME] = id } }
    fun setDarkMode(mode: String) = scope.launch { ds.edit { it[K_DARK] = mode } }
    fun setOnboarded() = scope.launch { ds.edit { it[K_ONBOARDED] = true } }
    fun setDeviceName(v: String) = scope.launch { ds.edit { it[K_DEVICE] = v.trim().take(40) } }
    fun setView(v: String) = scope.launch { ds.edit { it[K_VIEW] = v } }
    fun setSort(v: String, desc: Boolean) = scope.launch { ds.edit { it[K_SORT] = v; it[K_SORT_DESC] = desc } }
    fun setSubSize(v: Float) = scope.launch { ds.edit { it[K_SUB_SIZE] = v } }
    fun setSubBackground(v: Boolean) = scope.launch { ds.edit { it[K_SUB_BG] = v } }
    fun setSubLang(v: String) = scope.launch { ds.edit { it[K_SUB_LANG] = v } }
    fun setAudioLang(v: String) = scope.launch { ds.edit { it[K_AUDIO_LANG] = v } }
    fun setSeekStep(v: Int) = scope.launch { ds.edit { it[K_SEEK] = v } }
    fun setSlideshow(v: Int) = scope.launch { ds.edit { it[K_SLIDE] = v } }
    fun setLastServer(id: String) = scope.launch { ds.edit { it[K_LAST] = id } }
    fun setRendererName(v: String) = scope.launch { ds.edit { it[K_RENDERER] = v.trim().take(40) } }
    fun setUiMode(v: String) = scope.launch { ds.edit { it[K_UI] = v } }
    fun setAutoplay(v: String) = scope.launch { ds.edit { it[K_AUTOPLAY] = v } }
    fun setOpenLast(v: Boolean) = scope.launch { ds.edit { it[K_OPEN_LAST] = v } }

    /** Klasörü yer imlerine ekler ya da çıkarır. */
    fun toggleBookmark(serverId: String, path: String) = scope.launch {
        ds.edit { prefs ->
            val o = runCatching { org.json.JSONObject(prefs[K_BOOKMARKS] ?: "{}") }.getOrDefault(org.json.JSONObject())
            val list = o.optJSONArray(serverId)?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty().toMutableList()
            if (path in list) list.remove(path) else list.add(path)
            if (list.isEmpty()) o.remove(serverId) else o.put(serverId, org.json.JSONArray(list))
            prefs[K_BOOKMARKS] = o.toString()
        }
    }

    private companion object {
        val K_THEME = stringPreferencesKey("theme")
        val K_DARK = stringPreferencesKey("darkMode")
        val K_ONBOARDED = booleanPreferencesKey("onboarded")
        val K_DEVICE = stringPreferencesKey("deviceName")
        val K_VIEW = stringPreferencesKey("view")
        val K_SORT = stringPreferencesKey("sort")
        val K_SORT_DESC = booleanPreferencesKey("sortDesc")
        val K_SUB_SIZE = floatPreferencesKey("subSize")
        val K_SUB_BG = booleanPreferencesKey("subBg")
        val K_SUB_LANG = stringPreferencesKey("subLang")
        val K_AUDIO_LANG = stringPreferencesKey("audioLang")
        val K_SEEK = intPreferencesKey("seekStep")
        val K_SLIDE = intPreferencesKey("slideshow")
        val K_LAST = stringPreferencesKey("lastServer")
        val K_RENDERER = stringPreferencesKey("rendererName")
        val K_UI = stringPreferencesKey("uiMode")
        val K_AUTOPLAY = stringPreferencesKey("autoplay")
        val K_OPEN_LAST = booleanPreferencesKey("openLast")
        val K_BOOKMARKS = stringPreferencesKey("bookmarks")
    }
}
