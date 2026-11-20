package com.alis.core.model

/** Sunucunun türü: Akış (zengin JSON API) ya da herhangi bir UPnP/DLNA medya sunucusu. */
enum class ServerKind { AKIS, DLNA }

/**
 * Bir medya sunucusu (keşfedilen ya da kayıtlı).
 * @param id   UPnP UDN (uuid:…) ya da elle eklenende adresin kendisi
 * @param base "http://192.168.1.5:8200" gibi, sonda / olmadan
 */
data class Server(
    val id: String,
    val name: String,
    val base: String,
    val kind: ServerKind,
    val icon: String? = null,
    /** ContentDirectory denetim adresi (tam URL); Akış'ta da vardır ama zengin API kullanılır. */
    val cdControl: String? = null,
    val saved: Boolean = false,
    /** https tünel adresi (uzaktan erişim) */
    val remote: Boolean = false,
    /** Akış eşleştirme anahtarı (Authorization: Bearer) */
    val token: String? = null,
    val online: Boolean = false,
    val lastSeen: Long = 0,
    val model: String? = null,
) {
    val isAkis: Boolean get() = kind == ServerKind.AKIS
    val host: String get() = base.substringAfter("://").substringBefore('/').substringBefore(':')
}

enum class Kind { FOLDER, VIDEO, AUDIO, IMAGE, OTHER }

/** Kitaplıktaki bir öğe ya da klasör. */
data class Item(
    val id: String,
    val title: String,
    val kind: Kind,
    val serverId: String = "",
    /** Doğrudan oynatma / indirme adresi (tam URL) */
    val url: String? = null,
    val thumb: String? = null,
    val mime: String? = null,
    val ext: String = "",
    val size: Long = 0,
    val durationMs: Long = 0,
    val width: Int = 0,
    val height: Int = 0,
    val artist: String? = null,
    val album: String? = null,
    /** saniye (Unix) */
    val date: Long = 0,
    val childCount: Int = -1,
    val progress: Float = 0f,
    val posMs: Long = 0,
    val watched: Boolean = false,
    /** Tüm dosyalardan geliyorsa telefondaki yol */
    val path: String? = null,
) {
    val isFolder: Boolean get() = kind == Kind.FOLDER
    val playable: Boolean get() = kind == Kind.VIDEO || kind == Kind.AUDIO
    val fileName: String get() = if (ext.isNotEmpty() && !title.endsWith(".$ext", ignoreCase = true)) "$title.$ext" else title
}

data class Subtitle(
    val url: String,
    val label: String,
    val lang: String,
    val embedded: Boolean = false,
    val forced: Boolean = false,
)

/** Video dosyasındaki bir parça (/api/probe). */
data class Track(
    val index: Int,
    val video: Boolean,
    val mime: String,
    val codec: String,
    val lang: String,
    val channels: Int,
    val rate: Int,
    val width: Int,
    val height: Int,
    val canTranscode: Boolean,
    val name: String,
    val isDefault: Boolean,
)

data class ItemDetail(
    val item: Item,
    val subs: List<Subtitle> = emptyList(),
    val resumeMs: Long = 0,
    val next: Item? = null,
    val prev: Item? = null,
)

data class Crumb(val id: String, val title: String)

data class Folder(
    val id: String,
    val title: String,
    val parent: String? = null,
    val crumbs: List<Crumb> = emptyList(),
    val items: List<Item> = emptyList(),
)

data class Category(val id: String, val title: String, val count: Int)

data class Home(
    val cont: List<Item> = emptyList(),
    val recent: List<Item> = emptyList(),
    val cats: List<Category> = emptyList(),
)

/** Tüm dosyalar gezgininde bir girdi. */
data class FileEntry(
    val name: String,
    val path: String,
    val dir: Boolean,
    val size: Long = 0,
    /** saniye */
    val modified: Long = 0,
    val ext: String = "",
    val kind: Kind? = null,
    /** Medya ise oynatma kimliği (p:<yol>) */
    val mediaId: String? = null,
)

data class FileDir(
    val path: String,
    val crumbs: List<Crumb> = emptyList(),
    val items: List<FileEntry> = emptyList(),
)

data class ZipEntryInfo(val name: String, val dir: Boolean, val size: Long, val modified: Long)

/** Akış'ın açık özellikleri (/api/conf ya da window.AKIS). */
data class Capabilities(
    val name: String = "",
    val search: Boolean = true,
    val upload: Boolean = false,
    val history: Boolean = true,
    val subtitles: Boolean = true,
    val files: Boolean = false,
    val admin: Boolean = false,
    val remote: Boolean = false,
    val totp: Boolean = false,
    val status: Boolean = false,
    val apps: Boolean = false,
    val camera: Boolean = false,
    val camFront: Boolean = false,
    val camHeight: Int = 720,
    val notifs: Boolean = false,
    val inbox: Boolean = false,
    val screen: Boolean = false,
    val mic: Boolean = false,
    val docs: Boolean = false,
    val zipView: Boolean = false,
    val thumbs: Boolean = false,
    val subLang: String = "",
    val uploadTarget: String = "",
    val fileOps: Boolean = false,
    /** Akış'ta Dosya yönetimi açık mı (bu cihaza izin verilmemiş olsa da) */
    val fileOpsSwitch: Boolean = false,
    val fileReq: Boolean = false,
    val paired: Boolean = false,
    /** Akış /api/conf destekliyor mu (eski sürümler HTML'den okunur) */
    val api: Int = 0,
)

data class PhoneStatus(
    val device: String = "",
    val android: String = "",
    val battery: Int = -1,
    val charging: Boolean = false,
    val temp: Double? = null,
    val storage: List<Triple<String, Long, Long>> = emptyList(), // ad, toplam, boş
    val ramTotal: Long = 0,
    val ramFree: Long = 0,
    val rssi: Int? = null,
    val linkSpeed: Int? = null,
    val signal: Int? = null,
    val uptimeMs: Long = 0,
)

data class AppInfo(
    val pkg: String,
    val name: String,
    val version: String,
    val size: Long,
    val split: Boolean,
    val system: Boolean,
    val updated: Long,
)

data class NotifInfo(
    val key: String,
    val pkg: String,
    val app: String,
    val title: String,
    val text: String?,
    val time: Long,
    val gone: Boolean,
)

data class InboxItem(
    val id: String,
    val name: String,
    val size: Long,
    val mime: String,
    val from: String,
    val time: Long,
)

/** Yönetim paneli durumu (/admin/state). */
data class AdminState(
    val name: String,
    val switches: Map<String, Boolean>,
    val subLang: String,
    val langs: List<String>,
    val folders: List<AdminFolder>,
    val buckets: List<AdminBucket>,
    val clients: List<AdminClient>,
    val known: List<AdminKnown>,
    val shares: List<AdminShare>,
    val filesGranted: Boolean,
    val ring: Boolean,
    val ringing: Boolean,
    val me: String,
    val now: Long,
)

data class AdminFolder(val uri: String, val name: String, val isPath: Boolean)
data class AdminBucket(val id: String, val name: String, val count: Int, val hidden: Boolean)
data class AdminClient(val ip: String, val name: String, val last: Long)
data class AdminKnown(val ip: String, val name: String, val allowed: Boolean, val until: Long)
data class AdminShare(val token: String, val name: String, val expires: Long, val once: Boolean, val downloads: Int)

/** Erişim durumu: Akış bir isteği neden reddetti? */
enum class AccessState { OK, PIN, PIN_LOCKED, APPROVAL, BLOCKED, LOGIN, OFF, OFFLINE }

class AccessException(val state: AccessState, message: String? = null) : Exception(message ?: state.name)
