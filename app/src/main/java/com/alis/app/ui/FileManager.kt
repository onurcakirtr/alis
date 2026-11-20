package com.alis.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.SdCard
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alis.app.R
import com.alis.core.data.Feature
import com.alis.core.model.Capabilities
import com.alis.core.model.FileEntry
import com.alis.core.model.Kind
import com.alis.core.model.Server
import com.alis.core.ui.ExpressiveCard
import com.alis.core.ui.MessageBox
import com.alis.core.ui.formatBytes
import com.alis.core.ui.focusRing
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/** Kes / kopyala panosu (tek sunucu içinde). */
object FileClipboard {
    var serverId: String? = null
    var paths: List<String> = emptyList()
    var cut: Boolean = false
    val active: Boolean get() = paths.isNotEmpty()
    fun clear() {
        serverId = null
        paths = emptyList()
    }
}

/**
 * Akış'taki "Tüm dosyalar" için dosya yöneticisi.
 * Düzenleme (klasör, ad, taşı, kopyala, sil) Akış'ta eşleşme + bu cihaza Dosya yönetimi izni ister;
 * izin yoksa gezinme, indirme ve yükleme yine çalışır.
 * @param tv TV'de daha büyük satırlar ve yazılı eylem düğmeleri
 */
@OptIn(ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun FileManager(server: Server, path: String, tv: Boolean, header: @Composable (title: String, actions: @Composable () -> Unit) -> Unit) {
    val g = graph()
    val nav = nav()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val client = remember(server) { g.servers.client(server) }
    val caps = rememberLoad(server.id, server.token) { g.servers.capabilities(server, refresh = true) }
    val load = rememberLoad(server.id, path) { client.files(path) }
    var selecting by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    androidx.activity.compose.BackHandler(enabled = selecting) { selecting = false; selected = emptySet() }
    var dialog by remember { mutableStateOf<String?>(null) } // mkdir | rename | delete
    var menuFor by remember { mutableStateOf<String?>(null) } // uzun basılan dosyanın yolu
    var filtering by remember { mutableStateOf(false) }
    var typeFilter by remember { mutableStateOf("all") }
    var sortMenu by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf("") }
    val st by g.settings.state.collectAsStateWithLifecycle()
    val marks = st.bookmarks[server.id].orEmpty()
    androidx.activity.compose.BackHandler(enabled = filtering && !selecting) { filtering = false; filter = "" }
    var busy by remember { mutableStateOf(false) }
    var clipTick by remember { mutableStateOf(0) }
    val c: Capabilities? = caps.value
    val canEdit = c?.fileOps == true
    val queued = stringResource(R.string.download_queued)
    val done = stringResource(R.string.fm_done)
    val partly = stringResource(R.string.fm_partly)
    val errorTexts = mapOf(
        "pair" to stringResource(R.string.fm_need_pair), "device" to stringResource(R.string.fm_need_permission),
        "off" to stringResource(R.string.err_off), "exists" to stringResource(R.string.fm_exists),
    )
    val upload = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uploadUris(ctx, server, uris, path.ifEmpty { null })
    }

    fun run(op: String, paths: List<String> = emptyList(), dst: String? = null, name: String? = null, after: () -> Unit = {}) {
        busy = true
        scope.launch {
            val r = runCatching { client.fileOp(op, paths, dst, name) }
            busy = false
            r.onSuccess { failed -> toast(ctx, if (failed == 0) done else partly); after(); load.reload() }
                .onFailure { e -> toast(ctx, errorTexts[e.message] ?: (e.message ?: "?")) }
        }
    }

    val preparing = stringResource(R.string.fm_preparing)
    val shareFailed = stringResource(R.string.apps_failed)
    /** Dosyayı önbelleğe indirip Android'in paylaş menüsüne verir (WhatsApp, e-posta, Drive…). */
    fun shareFile(f: FileEntry) {
        busy = true
        toast(ctx, preparing)
        scope.launch {
            val out = runCatching {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val dir = java.io.File(ctx.cacheDir, "share").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
                    val file = java.io.File(dir, f.name)
                    g.net.client.newCall(okhttp3.Request.Builder().url(client.rawUrl(f.path)).build()).execute().use { r ->
                        if (!r.isSuccessful) throw java.io.IOException("HTTP ${r.code}")
                        file.outputStream().use { o -> r.body!!.byteStream().copyTo(o) }
                    }
                    file
                }
            }.getOrNull()
            busy = false
            if (out == null) return@launch toast(ctx, shareFailed)
            val uri = androidx.core.content.FileProvider.getUriForFile(ctx, ctx.packageName + ".files", out)
            val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType(mimeOf(f.name) ?: "*/*")
                .putExtra(android.content.Intent.EXTRA_STREAM, uri)
                .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            runCatching { ctx.startActivity(android.content.Intent.createChooser(send, f.name).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }

    val dir = load.value
    val entries = dir?.items.orEmpty()
    val title = dir?.crumbs?.lastOrNull()?.title ?: stringResource(R.string.tool_files)

    Column(Modifier.fillMaxSize()) {
        val selSize = entries.filter { it.path in selected && !it.dir }.sumOf { it.size }
        header(if (selecting) stringResource(R.string.selected_n, selected.size) + if (selSize > 0) " · " + formatBytes(selSize) else "" else title) {
            if (!selecting) {
                Act(Icons.Rounded.Search, R.string.fm_filter, tv) { filtering = !filtering; if (!filtering) filter = "" }
                if (path.isNotEmpty()) Act(if (path in marks) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder, if (path in marks) R.string.fm_unbookmark else R.string.fm_bookmark, tv) {
                    g.settings.toggleBookmark(server.id, path)
                }
                Act(Icons.Rounded.Refresh, R.string.refresh, tv) { load.reload() }
                if (path.isNotEmpty() && canEdit) Act(Icons.Rounded.CreateNewFolder, R.string.fm_new_folder, tv) { dialog = "mkdir" }
                if (path.isNotEmpty() && c?.upload == true && g.settings.current.on(Feature.SHARE_SEND) && !tv) Act(Icons.Rounded.Upload, R.string.fm_upload_here, tv) { upload.launch(arrayOf("*/*")) }
                Act(Icons.Rounded.Checklist, R.string.select, tv) { selecting = true }
            } else {
                Act(Icons.Rounded.SelectAll, R.string.select_all, tv) { selected = entries.map { it.path }.toSet() }
                val sel = entries.filter { it.path in selected }
                if (g.settings.current.on(Feature.DOWNLOADS)) Act(Icons.Rounded.Download, R.string.download, tv, sel.isNotEmpty()) {
                    downloadFiles(g, server, sel)
                    toast(ctx, queued)
                    selecting = false
                    selected = emptySet()
                }
                if (canEdit && path.isNotEmpty()) {
                    Act(Icons.Rounded.ContentCut, R.string.fm_cut, tv, sel.isNotEmpty()) {
                        FileClipboard.serverId = server.id; FileClipboard.paths = sel.map { it.path }; FileClipboard.cut = true
                        selecting = false; selected = emptySet(); clipTick++
                    }
                    Act(Icons.Rounded.ContentCopy, R.string.fm_copy, tv, sel.isNotEmpty()) {
                        FileClipboard.serverId = server.id; FileClipboard.paths = sel.map { it.path }; FileClipboard.cut = false
                        selecting = false; selected = emptySet(); clipTick++
                    }
                    Act(Icons.Rounded.DriveFileRenameOutline, R.string.rename, tv, sel.size == 1) { dialog = "rename" }
                    Act(Icons.Rounded.DeleteOutline, R.string.delete, tv, sel.isNotEmpty()) { dialog = "delete" }
                }
                Act(Icons.Rounded.Info, R.string.properties, tv, sel.size == 1) {
                    sel.firstOrNull()?.let { nav.push(Route.Info(server.id, null, it.path, it.name)) }
                }
                Act(Icons.Rounded.Close, R.string.cancel, tv) { selecting = false; selected = emptySet() }
            }
        }
        // Kırıntı yolu
        // Ada göre süzme
        if (filtering) {
            androidx.compose.material3.OutlinedTextField(
                filter, { filter = it },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = { if (filter.isNotEmpty()) androidx.compose.material3.IconButton(onClick = { filter = "" }) { Icon(Icons.Rounded.Close, stringResource(R.string.clear)) } },
                placeholder = { Text(stringResource(R.string.fm_filter_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
        // Yer imleri (depolama kökünde)
        if (path.isEmpty() && marks.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                marks.forEach { m ->
                    AssistChip(
                        onClick = { nav.push(Route.Files(server.id, m)) },
                        label = { Text(m.trimEnd('/').substringAfterLast('/')) },
                        leadingIcon = { Icon(Icons.Rounded.Bookmark, null, Modifier.size(16.dp)) },
                        modifier = Modifier.focusRing(MaterialTheme.shapes.small),
                    )
                }
            }
        }
        val crumbs = dir?.crumbs.orEmpty()
        if (crumbs.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AssistChip(onClick = { nav.push(Route.Files(server.id, "")) }, label = { Text(stringResource(R.string.fm_roots)) }, modifier = Modifier.focusRing(MaterialTheme.shapes.small))
                crumbs.forEach { cr ->
                    AssistChip(onClick = { if (cr.id != path) nav.push(Route.Files(server.id, cr.id)) }, label = { Text(cr.title) }, modifier = Modifier.focusRing(MaterialTheme.shapes.small))
                }
            }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        // Neden düzenlenemiyor?
        if (c != null && !c.fileOps && path.isNotEmpty()) EditBanner(server, c)
        // Yapıştırma çubuğu
        val clipNow = clipTick
        if (clipNow >= 0 && FileClipboard.active && FileClipboard.serverId == server.id && canEdit && path.isNotEmpty()) {
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (FileClipboard.cut) Icons.AutoMirrored.Rounded.DriveFileMove else Icons.Rounded.ContentCopy, null)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        stringResource(if (FileClipboard.cut) R.string.fm_clip_cut else R.string.fm_clip_copy, FileClipboard.paths.size),
                        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = {
                        val op = if (FileClipboard.cut) "move" else "copy"
                        run(op, FileClipboard.paths, dst = path) { if (FileClipboard.cut) FileClipboard.clear(); clipTick++ }
                    }, modifier = Modifier.focusRing(CircleShape)) {
                        Icon(Icons.Rounded.ContentPaste, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.fm_paste))
                    }
                    TextButton(onClick = { FileClipboard.clear(); clipTick++ }, modifier = Modifier.focusRing(CircleShape)) { Text(stringResource(R.string.cancel)) }
                }
            }
        }
        // Tür süzgeci, sıralama ve görünüm (depolama kökünde gerekmez)
        if (path.isNotEmpty() && entries.isNotEmpty() && !selecting) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                FILE_TYPES.forEach { (key, label) ->
                    if (key == "all" || entries.any { matchesType(it, key) }) {
                        androidx.compose.material3.FilterChip(
                            typeFilter == key, { typeFilter = key }, label = { Text(stringResource(label)) },
                            modifier = Modifier.focusRing(MaterialTheme.shapes.small),
                        )
                    }
                }
                Box {
                    Act(Icons.AutoMirrored.Rounded.Sort, R.string.sort, tv) { sortMenu = true }
                    androidx.compose.material3.DropdownMenu(sortMenu, { sortMenu = false }) {
                        listOf("name" to R.string.sort_name, "date" to R.string.sort_date, "size" to R.string.sort_size, "type" to R.string.sort_type).forEach { (k, l) ->
                            androidx.compose.material3.DropdownMenuItem(
                                { Text(stringResource(l) + if (st.sort == k) (if (st.sortDesc) " ↓" else " ↑") else "") },
                                { g.settings.setSort(k, if (st.sort == k) !st.sortDesc else false); sortMenu = false },
                            )
                        }
                    }
                }
                if (!tv) Act(if (st.view == "grid") Icons.AutoMirrored.Rounded.ViewList else Icons.Rounded.GridView, R.string.view_toggle, tv) {
                    g.settings.setView(if (st.view == "grid") "list" else "grid")
                }
            }
        }

        // Uzun basma menüsü (liste ve ızgara ortak)
        val fileMenu: @Composable (FileEntry, List<FileEntry>) -> Unit = { f, all ->
            androidx.compose.material3.DropdownMenu(expanded = menuFor == f.path, onDismissRequest = { menuFor = null }) {
                MenuEntry(R.string.open, Icons.AutoMirrored.Rounded.OpenInNew) { menuFor = null; Open.file(ctx, nav, server, f, all, path) }
                MenuEntry(R.string.select, Icons.Rounded.Checklist) { menuFor = null; selecting = true; selected = setOf(f.path) }
                if (!f.dir) {
                    MenuEntry(R.string.fm_share, Icons.Rounded.Share) { menuFor = null; shareFile(f) }
                    MenuEntry(R.string.fm_open_with, Icons.Rounded.Apps) { menuFor = null; Open.external(ctx, client.rawUrl(f.path), mimeOf(f.name)) }
                }
                if (g.settings.current.on(Feature.DOWNLOADS)) MenuEntry(R.string.download, Icons.Rounded.Download) {
                    menuFor = null; downloadFiles(g, server, listOf(f)); toast(ctx, queued)
                }
                if (canEdit) {
                    MenuEntry(R.string.fm_cut, Icons.Rounded.ContentCut) {
                        menuFor = null; FileClipboard.serverId = server.id; FileClipboard.paths = listOf(f.path); FileClipboard.cut = true; clipTick++
                    }
                    MenuEntry(R.string.fm_copy, Icons.Rounded.ContentCopy) {
                        menuFor = null; FileClipboard.serverId = server.id; FileClipboard.paths = listOf(f.path); FileClipboard.cut = false; clipTick++
                    }
                    MenuEntry(R.string.rename, Icons.Rounded.DriveFileRenameOutline) { menuFor = null; selected = setOf(f.path); dialog = "rename" }
                    MenuEntry(R.string.delete, Icons.Rounded.DeleteOutline) { menuFor = null; selected = setOf(f.path); dialog = "delete" }
                }
                MenuEntry(R.string.properties, Icons.Rounded.Info) { menuFor = null; nav.push(Route.Info(server.id, null, f.path, f.name)) }
            }
        }

        androidx.compose.material3.pulltorefresh.PullToRefreshBox(isRefreshing = load.refreshing, onRefresh = { load.reload() }, modifier = Modifier.weight(1f)) {
        LoadView(load, server, Modifier.fillMaxSize()) { d ->
            val shown = sortFiles(
                d.items.filter { matchesType(it, typeFilter) && (filter.isBlank() || it.name.contains(filter.trim(), ignoreCase = true)) },
                st.sort, st.sortDesc,
            )
            if (shown.isEmpty()) {
                MessageBox(Icons.Rounded.FolderOff, stringResource(if (filter.isBlank() && typeFilter == "all") R.string.folder_empty else R.string.search_none))
                return@LoadView
            }
            val onTap: (FileEntry) -> Unit = { f ->
                if (selecting) selected = if (f.path in selected) selected - f.path else selected + f.path
                else Open.file(ctx, nav, server, f, shown, path)
            }
            val onLong: (FileEntry) -> Unit = { f ->
                if (path.isNotEmpty()) {
                    if (selecting) selected = if (f.path in selected) selected - f.path else selected + f.path else menuFor = f.path
                }
            }
            when {
                // Depolama kökleri: büyük kartlar
                path.isEmpty() -> LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
                    items(shown, key = { it.path }) { f ->
                        ExpressiveCard(onClick = { onTap(f) }, padding = 16.dp) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                com.alis.core.ui.IconBadge(if (f.name.contains("SD", true) || f.path.count { it == '/' } <= 2 && !f.path.contains("emulated")) Icons.Rounded.SdCard else Icons.Rounded.PhoneAndroid, size = 52.dp)
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(f.name, style = MaterialTheme.typography.titleMedium)
                                    Text(f.path, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                }
                            }
                        }
                    }
                }
                st.view == "grid" && !tv -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(112.dp),
                    state = rememberGridMemory("fg|${server.id}|$path"),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(shown, key = { it.path }) { f ->
                        Box {
                            FileCard(client, f, c?.thumbs == true, f.path in selected, Modifier.focusRing(MaterialTheme.shapes.medium).combinedClickable(onClick = { onTap(f) }, onLongClick = { onLong(f) }))
                            fileMenu(f, shown)
                        }
                    }
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) { FolderSummary(shown) }
                }
                else -> LazyColumn(state = rememberListMemory("f|${server.id}|$path"), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp), modifier = Modifier.fillMaxSize()) {
                    items(shown, key = { it.path }) { f ->
                        val sel = f.path in selected
                        Box {
                            Surface(
                                color = if (sel) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier.fillMaxWidth().focusRing(MaterialTheme.shapes.medium, 1.01f)
                                    .combinedClickable(onClick = { onTap(f) }, onLongClick = { onLong(f) }),
                            ) {
                                FileRow(client, f, c?.thumbs == true, sel, tv)
                            }
                            fileMenu(f, shown)
                        }
                    }
                    item { FolderSummary(shown) }
                }
            }
        }
        }
    }

    val sel = entries.filter { it.path in selected }
    when (dialog) {
        "mkdir" -> NameDialog(stringResource(R.string.fm_new_folder), "", onDismiss = { dialog = null }) { name ->
            dialog = null
            run("mkdir", dst = path, name = name)
        }
        "rename" -> sel.singleOrNull()?.let { f ->
            NameDialog(stringResource(R.string.rename), f.name, onDismiss = { dialog = null }) { name ->
                dialog = null
                run("rename", listOf(f.path), name = name) { selecting = false; selected = emptySet() }
            }
        }
        "delete" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(R.string.delete)) },
            text = { Text(stringResource(R.string.fm_delete_text, sel.size) + "\n" + sel.take(5).joinToString("\n") { "• " + it.name } + if (sel.size > 5) "\n…" else "") },
            confirmButton = {
                TextButton(onClick = {
                    dialog = null
                    run("delete", sel.map { it.path }) { selecting = false; selected = emptySet() }
                }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** Seçilen dosyalar tek tek, klasörler ZIP (tek klasör: /zip, birden çok: /zipsel). */
fun downloadFiles(g: com.alis.core.data.Graph, server: Server, sel: List<FileEntry>) {
    val c = g.servers.client(server)
    sel.filter { !it.dir }.forEach { f ->
        g.transfers.download(server.id, c.rawUrl(f.path), f.name, f.size, null, f.kind?.let { k -> if (k == Kind.VIDEO) "v" else if (k == Kind.AUDIO) "a" else if (k == Kind.IMAGE) "i" else "" } ?: "")
    }
    val dirs = sel.filter { it.dir }
    if (dirs.size == 1) g.transfers.download(server.id, c.zipUrl(dirs[0].path), dirs[0].name + ".zip", -1, "application/zip")
    else if (dirs.size > 1) g.transfers.download(server.id, c.base + "/zipsel", "Alis.zip", -1, "application/zip", "", dirs.joinToString("&") { "p=" + c.enc(it.path) })
}

@Composable
private fun Act(icon: ImageVector, label: Int, tv: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    if (tv) {
        FilledTonalButton(onClick = onClick, enabled = enabled, modifier = Modifier.padding(horizontal = 3.dp).focusRing(CircleShape)) {
            Icon(icon, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(label))
        }
    } else {
        androidx.compose.material3.IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.focusRing(CircleShape)) { Icon(icon, stringResource(label)) }
    }
}

@Composable
private fun FileRow(client: com.alis.core.akis.AkisClient, f: FileEntry, thumbs: Boolean, selected: Boolean, tv: Boolean) {
    val cs = MaterialTheme.colorScheme
    val df = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = if (tv) 10.dp else 7.dp), verticalAlignment = Alignment.CenterVertically) {
        val showThumb = !f.dir && thumbs && (f.kind == Kind.IMAGE || f.kind == Kind.VIDEO || f.ext == "apk" || f.ext == "pdf")
        Box(Modifier.size(if (tv) 56.dp else 46.dp).clip(MaterialTheme.shapes.small)) {
            Thumb(if (showThumb) client.fileThumbUrl(f.path) else null, fileIcon(f), Modifier.fillMaxSize(), 24.dp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(f.name, style = if (tv) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (selected) FontWeight.SemiBold else null)
            val meta = listOfNotNull(
                if (f.dir) null else formatBytes(f.size),
                f.modified.takeIf { it > 0 }?.let { df.format(Date(it * 1000)) },
            ).joinToString(" · ")
            if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun EditBanner(server: Server, c: Capabilities) {
    val nav = nav()
    ExpressiveCard(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Lock, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Text(
                stringResource(
                    when {
                        server.remote -> R.string.fm_remote_hint
                        server.token == null || !c.paired -> R.string.fm_need_pair
                        !c.fileOpsSwitch -> R.string.fm_switch_off
                        else -> R.string.fm_need_permission
                    },
                ),
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f),
            )
            if (!server.remote && (server.token == null || !c.paired)) {
                TextButton(onClick = { nav.push(Route.Pair(server.id)) }, modifier = Modifier.focusRing(CircleShape)) {
                    Icon(Icons.Rounded.Link, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.pair_action))
                }
            }
        }
    }
}

@Composable
fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(text, { text = it }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { TextButton(onClick = { if (text.isNotBlank()) onOk(text.trim()) }) { Text(stringResource(R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun MenuEntry(label: Int, icon: ImageVector, onClick: () -> Unit) {
    androidx.compose.material3.DropdownMenuItem(
        text = { Text(stringResource(label)) },
        leadingIcon = { Icon(icon, null) },
        onClick = onClick,
    )
}

private val FILE_TYPES = listOf(
    "all" to R.string.ft_all, "image" to R.string.ft_images, "video" to R.string.ft_videos, "audio" to R.string.ft_music,
    "doc" to R.string.ft_docs, "archive" to R.string.ft_archives, "apk" to R.string.ft_apps,
)

private val DOC_EXT = setOf("pdf", "doc", "docx", "odt", "rtf", "txt", "md", "xls", "xlsx", "ods", "csv", "ppt", "pptx", "odp", "epub", "json", "xml", "html", "htm")
private val ARCHIVE_EXT = setOf("zip", "rar", "7z", "tar", "gz", "tgz", "bz2", "xz", "apks", "xapk")
private val CODE_EXT = setOf("kt", "java", "js", "ts", "py", "c", "cpp", "h", "css", "sh", "bat", "gradle", "kts", "go", "rs", "swift", "sql", "yml", "yaml", "toml", "ini", "conf", "properties")
private val SUB_EXT = setOf("srt", "vtt", "ass", "ssa", "sub")

/** Klasörler her türde görünür (içine girilebilsin). */
private fun matchesType(f: FileEntry, type: String): Boolean {
    if (type == "all" || f.dir) return true
    val e = f.ext.lowercase()
    return when (type) {
        "image" -> f.kind == Kind.IMAGE
        "video" -> f.kind == Kind.VIDEO
        "audio" -> f.kind == Kind.AUDIO
        "doc" -> e in DOC_EXT
        "archive" -> e in ARCHIVE_EXT
        "apk" -> e == "apk"
        else -> true
    }
}

/** Klasörler önce; ad / tarih / boyut / tür. */
private fun sortFiles(list: List<FileEntry>, by: String, desc: Boolean): List<FileEntry> {
    val c = when (by) {
        "date" -> compareBy<FileEntry> { it.modified }
        "size" -> compareBy { it.size }
        "type" -> compareBy<FileEntry> { it.ext.lowercase() }.thenBy { it.name.lowercase() }
        else -> compareBy { it.name.lowercase() }
    }
    val sorted = list.sortedWith(if (desc) c.reversed() else c)
    return sorted.filter { it.dir } + sorted.filter { !it.dir }
}

private fun mimeOf(name: String): String? =
    android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase())

/** Türe göre simge (PDF, arşiv, APK, belge, kod, altyazı…). */
fun fileIcon(f: FileEntry): ImageVector {
    if (f.dir) return Icons.Rounded.Folder
    val e = f.ext.lowercase()
    return when {
        f.kind != null && f.kind != Kind.OTHER -> kindIcon(f.kind)
        e == "pdf" -> Icons.Rounded.PictureAsPdf
        e == "apk" -> Icons.Rounded.Android
        e in ARCHIVE_EXT -> Icons.Rounded.FolderZip
        e in SUB_EXT -> Icons.Rounded.ClosedCaption
        e in CODE_EXT -> Icons.Rounded.Code
        e in DOC_EXT -> Icons.Rounded.Description
        else -> Icons.Rounded.InsertDriveFile
    }
}

/** Izgara kartı: büyük küçük resim ya da tür simgesi, altta ad ve boyut. */
@Composable
private fun FileCard(client: com.alis.core.akis.AkisClient, f: FileEntry, thumbs: Boolean, selected: Boolean, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    Column(modifier.padding(4.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(MaterialTheme.shapes.medium).background(cs.surfaceContainerHigh)) {
            val showThumb = !f.dir && thumbs && (f.kind == Kind.IMAGE || f.kind == Kind.VIDEO || f.ext == "apk" || f.ext == "pdf")
            Thumb(if (showThumb) client.fileThumbUrl(f.path) else null, fileIcon(f), Modifier.fillMaxSize(), 40.dp)
            if (selected) Box(Modifier.fillMaxSize().background(cs.primary.copy(alpha = 0.4f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.CheckCircle, null, tint = cs.onPrimary, modifier = Modifier.size(36.dp))
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(f.name, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = if (selected) FontWeight.SemiBold else null)
        if (!f.dir) Text(formatBytes(f.size), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
    }
}

/** Klasörün altında özet: kaç klasör, kaç dosya, toplam boyut. */
@Composable
private fun FolderSummary(list: List<FileEntry>) {
    val dirs = list.count { it.dir }
    val files = list.filter { !it.dir }
    Text(
        stringResource(R.string.fm_summary, dirs, files.size, formatBytes(files.sumOf { it.size })),
        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(16.dp),
    )
}
