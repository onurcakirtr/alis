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
@OptIn(ExperimentalFoundationApi::class)
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
    var dialog by remember { mutableStateOf<String?>(null) } // mkdir | rename | delete
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

    val dir = load.value
    val entries = dir?.items.orEmpty()
    val title = dir?.crumbs?.lastOrNull()?.title ?: stringResource(R.string.tool_files)

    Column(Modifier.fillMaxSize()) {
        header(if (selecting) stringResource(R.string.selected_n, selected.size) else title) {
            if (!selecting) {
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
        LoadView(load, server, Modifier.weight(1f)) { d ->
            if (d.items.isEmpty()) {
                MessageBox(Icons.Rounded.FolderOff, stringResource(R.string.folder_empty))
                return@LoadView
            }
            LazyColumn(contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp), modifier = Modifier.fillMaxSize()) {
                items(d.items, key = { it.path }) { f ->
                    val sel = f.path in selected
                    Surface(
                        color = if (sel) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth().focusRing(MaterialTheme.shapes.medium, 1.01f).combinedClickable(
                            onClick = {
                                if (selecting) selected = if (sel) selected - f.path else selected + f.path
                                else Open.file(ctx, nav, server, f, d.items, path)
                            },
                            onLongClick = {
                                if (path.isEmpty()) return@combinedClickable
                                selecting = true
                                selected = selected + f.path
                            },
                        ),
                    ) {
                        FileRow(client, f, c?.thumbs == true, sel, tv)
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
            Thumb(if (showThumb) client.fileThumbUrl(f.path) else null, if (f.dir) kindIcon(Kind.FOLDER) else kindIcon(f.kind), Modifier.fillMaxSize(), 24.dp)
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
