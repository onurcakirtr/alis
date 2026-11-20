package com.alis.app.ui.tools

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ClearAll
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alis.app.R
import com.alis.app.mobile.ActionIcon
import com.alis.app.mobile.TopBar
import com.alis.app.ui.Open
import com.alis.app.ui.PhotoSession
import com.alis.app.ui.Route
import com.alis.app.ui.graph
import com.alis.app.ui.nav
import com.alis.core.data.Transfer
import com.alis.core.data.TransferState
import com.alis.core.model.Item
import com.alis.core.model.Kind
import com.alis.core.player.Audio
import com.alis.core.player.Players
import com.alis.core.ui.ExpressiveCard
import com.alis.core.ui.MessageBox
import com.alis.core.ui.formatBytes

/** İndirmeler ve yüklemeler; bitmiş indirmeler çevrimdışı açılır / oynatılır. */
@Composable
fun TransfersScreen() {
    val g = graph()
    val nav = nav()
    val ctx = LocalContext.current
    val list by g.transfers.list.collectAsStateWithLifecycle()
    var removing by remember { mutableStateOf<Transfer?>(null) }

    fun open(t: Transfer) {
        val uri = t.target ?: return
        val kind = when (t.kind) { "v" -> Kind.VIDEO; "a" -> Kind.AUDIO; "i" -> Kind.IMAGE; else -> Kind.OTHER }
        val server = g.servers.byId(t.serverId) ?: com.alis.core.model.Server(t.serverId, "", "", com.alis.core.model.ServerKind.DLNA)
        val item = Item("offline:" + t.id, t.name.substringBeforeLast('.'), kind, server.id, url = uri, mime = t.mime, ext = t.name.substringAfterLast('.', ""))
        when (kind) {
            Kind.VIDEO -> Open.video(ctx, server, item, listOf(item), null)
            Kind.AUDIO -> { Audio.play(ctx, listOf(Players.toMediaItem(item)), 0); nav.push(Route.Music) }
            Kind.IMAGE -> { PhotoSession.server = server; PhotoSession.items = listOf(item); PhotoSession.index = 0; nav.push(Route.Photos) }
            else -> ctx.startActivity(
                Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(uri), t.mime ?: "*/*")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.tab_downloads), stringResource(R.string.transfers_sub), back = nav.canPop) {
            ActionIcon(Icons.Rounded.ClearAll, stringResource(R.string.transfers_clear)) { g.transfers.clearFinished() }
        }
        if (list.isEmpty()) {
            MessageBox(Icons.Rounded.CloudDownload, stringResource(R.string.transfers_empty), stringResource(R.string.transfers_empty_sub))
            return@Column
        }
        LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(list, key = { it.id }) { t ->
                ExpressiveCard(padding = 14.dp, onClick = if (t.state == TransferState.DONE && !t.upload) ({ open(t) }) else null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            when (t.state) {
                                TransferState.DONE -> Icons.Rounded.CheckCircle
                                TransferState.FAILED, TransferState.CANCELED -> Icons.Rounded.ErrorOutline
                                else -> if (t.upload) Icons.Rounded.CloudUpload else Icons.Rounded.CloudDownload
                            },
                            null,
                            tint = if (t.state == TransferState.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val status = when (t.state) {
                                TransferState.QUEUED -> stringResource(R.string.tr_queued)
                                TransferState.RUNNING -> formatBytes(t.done) + if (t.size > 0) " / " + formatBytes(t.size) else ""
                                TransferState.DONE -> formatBytes(if (t.size > 0) t.size else t.done) + " · " + stringResource(if (t.upload) R.string.tr_uploaded else R.string.tr_done)
                                TransferState.FAILED -> stringResource(R.string.tr_failed) + (t.error?.let { ": $it" } ?: "")
                                TransferState.CANCELED -> stringResource(R.string.tr_canceled)
                            }
                            Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                        }
                        when (t.state) {
                            TransferState.QUEUED, TransferState.RUNNING -> ActionIcon(Icons.Rounded.Close, stringResource(R.string.cancel)) { g.transfers.cancel(t.id) }
                            TransferState.FAILED, TransferState.CANCELED -> ActionIcon(Icons.Rounded.Refresh, stringResource(R.string.retry)) { g.transfers.retry(t.id) }
                            TransferState.DONE -> if (!t.upload) ActionIcon(Icons.Rounded.PlayArrow, stringResource(R.string.open)) { open(t) }
                        }
                        if (t.state != TransferState.RUNNING) ActionIcon(Icons.Rounded.DeleteOutline, stringResource(R.string.remove)) { removing = t }
                    }
                    if (t.state == TransferState.RUNNING) {
                        Spacer(Modifier.height(8.dp))
                        if (t.size > 0) LinearProgressIndicator(progress = { (t.done.toFloat() / t.size).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                        else LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
    removing?.let { t ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text(stringResource(R.string.remove)) },
            text = { Text(t.name) },
            confirmButton = {
                Row {
                    if (!t.upload && t.state == TransferState.DONE) TextButton(onClick = { g.transfers.remove(t.id, true); removing = null }) { Text(stringResource(R.string.tr_remove_file)) }
                    TextButton(onClick = { g.transfers.remove(t.id, false); removing = null }) { Text(stringResource(R.string.tr_remove_list)) }
                }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
