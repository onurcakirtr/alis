package com.alis.app.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.alis.app.R
import com.alis.app.player.PlayerActivity
import com.alis.core.data.Feature
import com.alis.core.data.graph
import com.alis.core.model.FileEntry
import com.alis.core.model.Item
import com.alis.core.model.Kind
import com.alis.core.model.Server
import com.alis.core.player.Audio
import com.alis.core.player.Players

/** Oynatıcıya verilecek sıra (etkinlikler arası; süreç ölürse oynatıcı kendini kapatır). */
object PlayQueue {
    var server: Server? = null
    var items: List<Item> = emptyList()
    var index: Int = 0
    var folderId: String? = null
}

/** Foto görüntüleyiciye verilecek liste. */
object PhotoSession {
    var server: Server? = null
    var items: List<Item> = emptyList()
    var index: Int = 0
}

object Open {
    /**
     * Bir öğeyi türüne göre açar.
     * @param siblings aynı klasördeki öğeler (sıradaki video, müzik sırası, foto kaydırma)
     */
    fun item(ctx: Context, nav: Navigator, server: Server, item: Item, siblings: List<Item>, folderId: String?) {
        when (item.kind) {
            Kind.FOLDER -> nav.push(Route.Browse(server.id, item.id, item.title))
            Kind.VIDEO -> video(ctx, server, item, siblings.filter { it.kind == Kind.VIDEO }, folderId)
            Kind.AUDIO -> {
                val list = siblings.filter { it.kind == Kind.AUDIO }.ifEmpty { listOf(item) }
                Audio.play(ctx, list.map { Players.toMediaItem(offline(ctx, it)) }, list.indexOfFirst { it.id == item.id }.coerceAtLeast(0))
                nav.push(Route.Music)
            }
            Kind.IMAGE -> {
                val list = siblings.filter { it.kind == Kind.IMAGE }.ifEmpty { listOf(item) }
                PhotoSession.server = server
                PhotoSession.items = list
                PhotoSession.index = list.indexOfFirst { it.id == item.id }.coerceAtLeast(0)
                nav.push(Route.Photos)
            }
            Kind.OTHER -> external(ctx, item.url, item.mime)
        }
    }

    fun video(ctx: Context, server: Server, item: Item, list: List<Item>, folderId: String?) {
        PlayQueue.server = server
        PlayQueue.items = list.ifEmpty { listOf(item) }
        PlayQueue.index = PlayQueue.items.indexOfFirst { it.id == item.id }.coerceAtLeast(0)
        PlayQueue.folderId = folderId
        ctx.startActivity(Intent(ctx, PlayerActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK.takeIf { ctx !is android.app.Activity } ?: 0))
    }

    /** İndirilmiş bitmiş kopya varsa çevrimdışı oynat. */
    fun offline(ctx: Context, item: Item): Item {
        val u = item.url ?: return item
        val local = ctx.graph.transfers.offlineUri(u) ?: return item
        return item.copy(url = local.toString())
    }

    /** Tüm dosyalardaki bir dosyayı açar: medya kitaplık gibi, belge önizleme, ZIP içi, diğerleri dışarıda. */
    fun file(ctx: Context, nav: Navigator, server: Server, f: FileEntry, all: List<FileEntry>, dirPath: String) {
        val client = ctx.graph.servers.client(server)
        val s = ctx.graph.settings.current
        if (f.dir) return nav.push(Route.Files(server.id, f.path))
        val ext = f.ext.lowercase()
        if (f.mediaId != null && f.kind != null && f.kind != Kind.OTHER) {
            val siblings = all.filter { it.mediaId != null && it.kind == f.kind }.map { fileItem(client, server, it) }
            return item(ctx, nav, server, fileItem(client, server, f), siblings, null)
        }
        if (ext == "zip" && s.on(Feature.ZIP_VIEW)) return nav.push(Route.Zip(server.id, f.path, f.name))
        if (s.on(Feature.DOC_PREVIEW) && (ext in TEXT_EXT || ext == "pdf")) return nav.push(Route.Doc(server.id, f.path, f.name))
        external(ctx, client.rawUrl(f.path), null)
    }

    fun fileItem(client: com.alis.core.akis.AkisClient, server: Server, f: FileEntry): Item {
        val id = f.mediaId ?: ("p:" + f.path)
        return Item(
            id = id, title = f.name.substringBeforeLast('.', f.name), kind = f.kind ?: Kind.OTHER, serverId = server.id,
            url = client.mediaUrl(id, f.ext), thumb = client.fileThumbUrl(f.path), ext = f.ext, size = f.size,
            date = f.modified, path = f.path,
        )
    }

    fun external(ctx: Context, url: String?, mime: String?) {
        if (url == null) return
        val i = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(url), mime ?: "*/*").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            ctx.startActivity(i)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(ctx, R.string.no_app, Toast.LENGTH_SHORT).show()
        }
    }

    /** alis://play?s=<sunucu>&i=<öğe> — Google TV "Devam et" satırından. */
    fun deepLink(ctx: Context, uri: Uri): Boolean {
        if (uri.scheme != "alis" || uri.host != "play") return false
        val s = ctx.graph.servers.byId(uri.getQueryParameter("s") ?: return false) ?: return false
        val id = uri.getQueryParameter("i") ?: return false
        video(ctx, s, Item(id, "", Kind.VIDEO, s.id), emptyList(), null)
        return true
    }

    val TEXT_EXT = setOf(
        "txt", "md", "markdown", "json", "xml", "csv", "log", "ini", "conf", "yml", "yaml", "kt", "java", "js", "ts", "py", "c", "cpp", "h",
        "html", "htm", "css", "sh", "bat", "gradle", "kts", "properties", "srt", "vtt", "ass", "sql", "go", "rs", "swift", "toml",
    )
}
