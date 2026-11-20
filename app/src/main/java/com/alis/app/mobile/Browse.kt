package com.alis.app.mobile

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alis.app.R
import com.alis.app.ui.ItemRowContent
import com.alis.app.ui.LoadView
import com.alis.app.ui.MediaCard
import com.alis.app.ui.Open
import com.alis.app.ui.Route
import com.alis.app.ui.graph
import com.alis.app.ui.itemMeta
import com.alis.app.ui.kindIcon
import com.alis.app.ui.nav
import com.alis.app.ui.rememberLoad
import com.alis.app.ui.rememberServer
import com.alis.app.ui.toast
import com.alis.core.data.Feature
import com.alis.core.data.Graph
import com.alis.core.model.Item
import com.alis.core.model.Kind
import com.alis.core.model.Server
import com.alis.core.player.Audio
import com.alis.core.player.Players
import com.alis.core.ui.MessageBox
import com.alis.core.ui.focusRing

@Composable
fun BrowseScreen(serverId: String, id: String, title: String) {
    val server = rememberServer(serverId) ?: return
    Column(Modifier.fillMaxSize()) {
        BrowseContent(server, id, Modifier.fillMaxSize(), title)
    }
}

/** Sıralama: ayarlardaki ölçüte göre; klasörler her zaman önce. */
fun sortItems(list: List<Item>, by: String, desc: Boolean): List<Item> {
    val c = when (by) {
        "date" -> compareBy<Item> { it.date }
        "size" -> compareBy { it.size }
        else -> compareBy { it.title.lowercase() }
    }
    val sorted = list.sortedWith(if (desc) c.reversed() else c)
    return sorted.filter { it.isFolder } + sorted.filter { !it.isFolder }
}

/** Seçilenleri indirir: dosyalar tek tek, Akış klasörleri ZIP olarak. */
fun downloadItems(g: Graph, server: Server, items: List<Item>): Int {
    var n = 0
    val folders = items.filter { it.isFolder }
    for (i in items.filter { !it.isFolder && it.url != null }) {
        g.transfers.download(server.id, i.url!!, i.fileName, i.size.takeIf { it > 0 } ?: -1, i.mime, kindCode(i.kind))
        n++
    }
    if (folders.isNotEmpty() && server.isAkis) {
        val c = g.servers.client(server)
        val form = folders.joinToString("&") { "i=" + c.enc(it.id) }
        g.transfers.download(server.id, c.base + "/zipsel", (folders.singleOrNull()?.title ?: "Alis") + ".zip", -1, "application/zip", "", form)
        n++
    }
    return n
}

fun kindCode(k: Kind) = when (k) {
    Kind.VIDEO -> "v"
    Kind.AUDIO -> "a"
    Kind.IMAGE -> "i"
    else -> ""
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BrowseContent(server: Server, folderId: String, modifier: Modifier, title: String? = null, header: Boolean = true) {
    val g = graph()
    val nav = nav()
    val ctx = LocalContext.current
    val s by g.settings.state.collectAsStateWithLifecycle()
    val load = rememberLoad(server.id, folderId) { g.library.list(server, folderId) }
    var selecting by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var sortMenu by remember { mutableStateOf(false) }
    val queued = stringResource(R.string.download_queued)

    Column(modifier) {
        val folder = load.value
        if (selecting) {
            TopBar(stringResource(R.string.selected_n, selected.size), back = false) {
                ActionIcon(Icons.Rounded.SelectAll, stringResource(R.string.select_all)) { selected = folder?.items?.map { it.id }?.toSet() ?: emptySet() }
                if (s.on(Feature.DOWNLOADS)) ActionIcon(Icons.Rounded.Download, stringResource(R.string.download), enabled = selected.isNotEmpty()) {
                    val items = folder?.items?.filter { it.id in selected }.orEmpty()
                    downloadItems(g, server, items)
                    toast(ctx, queued)
                    selecting = false
                    selected = emptySet()
                }
                ActionIcon(Icons.AutoMirrored.Rounded.PlaylistAdd, stringResource(R.string.add_to_queue), enabled = selected.isNotEmpty()) {
                    val audio = folder?.items?.filter { it.id in selected && it.kind == Kind.AUDIO }.orEmpty()
                    if (audio.isNotEmpty()) Audio.enqueue(ctx, audio.map { Players.toMediaItem(Open.offline(ctx, it)) })
                    selecting = false
                    selected = emptySet()
                }
                ActionIcon(Icons.Rounded.Close, stringResource(R.string.cancel)) { selecting = false; selected = emptySet() }
            }
        } else if (header) {
            TopBar(title ?: folder?.title?.ifEmpty { null } ?: server.name, server.name.takeIf { title != null }) {
                ActionIcon(if (s.view == "grid") Icons.AutoMirrored.Rounded.ViewList else Icons.Rounded.GridView, stringResource(R.string.view_toggle)) {
                    g.settings.setView(if (s.view == "grid") "list" else "grid")
                }
                Box {
                    ActionIcon(Icons.AutoMirrored.Rounded.Sort, stringResource(R.string.sort)) { sortMenu = true }
                    DropdownMenu(sortMenu, { sortMenu = false }) {
                        listOf("name" to R.string.sort_name, "date" to R.string.sort_date, "size" to R.string.sort_size).forEach { (k, l) ->
                            DropdownMenuItem(
                                { Text(stringResource(l) + if (s.sort == k) (if (s.sortDesc) " ↓" else " ↑") else "") },
                                { g.settings.setSort(k, if (s.sort == k) !s.sortDesc else false); sortMenu = false },
                            )
                        }
                    }
                }
                ActionIcon(Icons.Rounded.Checklist, stringResource(R.string.select)) { selecting = true }
            }
        }
        // Kırıntı yolu (Akış)
        val crumbs = folder?.crumbs.orEmpty()
        if (crumbs.size > 1) {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(crumbs) { c ->
                    AssistChip(onClick = { if (c.id != folderId) nav.push(Route.Browse(server.id, c.id, c.title)) }, label = { Text(c.title) })
                }
            }
        }
        LoadView(load, server, Modifier.weight(1f)) { f ->
            val items = sortItems(f.items, s.sort, s.sortDesc)
            if (items.isEmpty()) {
                MessageBox(Icons.Rounded.FolderOff, stringResource(R.string.folder_empty))
                return@LoadView
            }
            val playable = items.filter { it.kind == Kind.AUDIO }
            val onClick: (Item) -> Unit = { it ->
                if (selecting) selected = if (it.id in selected) selected - it.id else selected + it.id
                else Open.item(ctx, nav, server, it, items, folderId)
            }
            val onLong: (Item) -> Unit = { it ->
                if (!selecting) {
                    selecting = true
                    selected = setOf(it.id)
                } else if (server.isAkis) {
                    nav.push(Route.Info(server.id, it.id, null, it.title))
                }
            }
            Column {
                if (playable.size > 1 && !selecting) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ToolChip(Icons.Rounded.PlayArrow, stringResource(R.string.play_all)) {
                            Audio.play(ctx, playable.map { Players.toMediaItem(Open.offline(ctx, it)) }, 0)
                            nav.push(Route.Music)
                        }
                        ToolChip(Icons.Rounded.Shuffle, stringResource(R.string.shuffle)) {
                            Audio.play(ctx, playable.map { Players.toMediaItem(Open.offline(ctx, it)) }, (playable.indices).random(), shuffle = true)
                            nav.push(Route.Music)
                        }
                    }
                }
                if (s.view == "grid") {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(if (items.any { it.kind == Kind.VIDEO }) 170.dp else 120.dp),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(items, key = { it.id }) { it ->
                            Box(
                                Modifier.focusRing(MaterialTheme.shapes.medium)
                                    .combinedClickable(onClick = { onClick(it) }, onLongClick = { onLong(it) }),
                            ) { MediaCard(it, it.id in selected) }
                        }
                    }
                } else {
                    LazyColumn(contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp), modifier = Modifier.fillMaxSize()) {
                        items(items, key = { it.id }) { it ->
                            Surface(
                                color = Color.Transparent,
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier.fillMaxWidth().focusRing(MaterialTheme.shapes.medium, 1.01f)
                                    .combinedClickable(onClick = { onClick(it) }, onLongClick = { onLong(it) }),
                            ) {
                                ItemRowContent(it.title, itemMeta(it), if (it.isFolder) null else it.thumb, kindIcon(it.kind), it.id in selected) {
                                    if (server.isAkis && !it.isFolder && !selecting) {
                                        ActionIcon(Icons.Rounded.Info, stringResource(R.string.properties)) { nav.push(Route.Info(server.id, it.id, null, it.title)) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
