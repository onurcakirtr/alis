package com.alis.core.data

import com.alis.core.model.Folder
import com.alis.core.model.Home
import com.alis.core.model.Item
import com.alis.core.model.ItemDetail
import com.alis.core.model.Kind
import com.alis.core.model.Server
import com.alis.core.model.Subtitle
import com.alis.core.upnp.ContentDirectory
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * Ekranların tek kitaplık arayüzü: Akış'ta zengin JSON API, genel DLNA sunucularında ContentDirectory.
 * DLNA'da altyazılar Browse sonucundan önbelleğe alınır.
 */
class Library(private val net: Net, private val servers: ServerRepository) {
    private val cd = ContentDirectory(net.client)
    private val dlnaSubs = ConcurrentHashMap<String, List<Subtitle>>()
    /** DLNA: klasör kimliği → içindekiler (oynatıcıda sıradaki için) */
    private val dlnaFolders = ConcurrentHashMap<String, List<Item>>()

    suspend fun home(s: Server): Home? = if (s.isAkis) servers.client(s).home() else null

    suspend fun list(s: Server, id: String): Folder {
        if (s.isAkis) return servers.client(s).list(id)
        val control = s.cdControl ?: throw IOException("no ContentDirectory")
        val all = ArrayList<Item>()
        var start = 0
        var total: Int
        do {
            val r = cd.browse(control, s.id, id, start, 500)
            all += r.items
            r.subs.forEach { (k, v) -> dlnaSubs["${s.id}|$k"] = v }
            total = r.total
            start += r.items.size
        } while (r.items.isNotEmpty() && start < total && start < 5000)
        dlnaFolders["${s.id}|$id"] = all
        return Folder(id = id, title = "", items = all)
    }

    suspend fun search(s: Server, q: String): List<Item> {
        if (s.isAkis) return servers.client(s).search(q)
        val control = s.cdControl ?: return emptyList()
        return runCatching { cd.search(control, s.id, q).also { r -> r.subs.forEach { (k, v) -> dlnaSubs["${s.id}|$k"] = v } }.items }
            .getOrDefault(emptyList())
    }

    /** Birleşik arama: çevrim içi tüm sunucularda aynı anda (her biri en çok 8 sn). */
    suspend fun searchAll(q: String): List<Pair<Server, List<Item>>> = coroutineScope {
        servers.servers.value.filter { it.online }.map { s ->
            async { s to (withTimeoutOrNull(8000) { runCatching { search(s, q) }.getOrNull() } ?: emptyList()) }
        }.awaitAll().filter { it.second.isNotEmpty() }
    }

    /** Oynatmadan önce: altyazılar, kaldığı yer, sıradaki / önceki. */
    suspend fun detail(s: Server, item: Item, folderId: String?): ItemDetail {
        if (s.isAkis) return servers.client(s).detail(item.id, folderId.orEmpty())
        val siblings = folderId?.let { dlnaFolders["${s.id}|$it"] }?.filter { it.kind == item.kind }.orEmpty()
        val i = siblings.indexOfFirst { it.id == item.id }
        return ItemDetail(
            item = item,
            subs = dlnaSubs["${s.id}|${item.id}"].orEmpty(),
            next = siblings.getOrNull(i + 1).takeIf { i >= 0 },
            prev = siblings.getOrNull(i - 1).takeIf { i >= 0 },
        )
    }

    /** Bir klasördeki aynı türden öğeler (müzik sırası, foto gezinme). */
    suspend fun siblings(s: Server, folderId: String, kind: Kind): List<Item> =
        list(s, folderId).items.filter { it.kind == kind }
}
