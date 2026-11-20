package com.alis.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alis.app.R
import com.alis.core.data.Feature
import com.alis.core.model.Item
import com.alis.core.model.Kind
import com.alis.core.model.Server
import com.alis.core.player.Audio
import com.alis.core.player.Players
import com.alis.core.ui.KeyValueRow
import com.alis.core.ui.focusRing
import com.alis.core.ui.formatBytes
import com.alis.core.ui.formatDuration
import java.text.DateFormat
import java.util.Date

/**
 * Klasörde "kaldığın yerden devam": yarım kalan ilk video; yoksa en son izlenenden sonraki video.
 * Hiç izlenmemişse null (düğme gösterilmez).
 */
fun resumeTarget(items: List<Item>): Item? {
    val videos = items.filter { it.kind == Kind.VIDEO }
    if (videos.size < 2 && videos.none { it.progress > 0f }) return null
    videos.firstOrNull { it.progress > 0f && !it.watched }?.let { return it }
    val last = videos.indexOfLast { it.watched }
    return if (last >= 0 && last < videos.size - 1) videos[last + 1] else null
}

/** Uzun basınca: öğenin ayrıntısı ve hızlı eylemler. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ItemDetailDialog(server: Server, item: Item, siblings: List<Item>, folderId: String?, onSelect: () -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val g = graph()
    val nav = nav()
    val df = DateFormat.getDateInstance(DateFormat.MEDIUM)
    val queued = stringResource(R.string.download_queued)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.title) },
        text = {
            Column {
                if (!item.isFolder && item.thumb != null) {
                    Box(Modifier.fillMaxWidth().aspectRatio(if (item.kind == Kind.VIDEO) 16f / 9f else 1.5f).clip(MaterialTheme.shapes.medium)) {
                        Thumb(item.thumb, kindIcon(item.kind), Modifier.fillMaxSize(), crop = item.kind != Kind.IMAGE)
                    }
                    androidx.compose.foundation.layout.Spacer(Modifier.height(10.dp))
                }
                if (item.progress > 0f && !item.watched) {
                    LinearProgressIndicator(progress = { item.progress }, modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp))
                    Text(stringResource(R.string.detail_progress, (item.progress * 100).toInt(), formatDuration(item.posMs)), style = MaterialTheme.typography.labelMedium)
                } else if (item.watched) {
                    Text(stringResource(R.string.detail_watched), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                if (item.durationMs > 0) KeyValueRow(stringResource(R.string.info_duration), formatDuration(item.durationMs))
                if (item.width > 0) KeyValueRow(stringResource(R.string.detail_resolution), "${item.width}×${item.height}")
                item.artist?.let { KeyValueRow(stringResource(R.string.detail_artist), it) }
                item.album?.let { KeyValueRow(stringResource(R.string.detail_album), it) }
                if (item.size > 0) KeyValueRow(stringResource(R.string.info_size), formatBytes(item.size))
                if (item.ext.isNotEmpty()) KeyValueRow(stringResource(R.string.info_type), item.ext.uppercase())
                if (item.date > 0) KeyValueRow(stringResource(R.string.info_date), df.format(Date(item.date * 1000)))
                androidx.compose.foundation.layout.Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilledTonalButton(onClick = { onDismiss(); Open.item(ctx, nav, server, item, siblings, folderId) }, modifier = Modifier.focusRing(CircleShape)) {
                        Icon(Icons.Rounded.PlayArrow, null)
                        Text(stringResource(if (item.playable) R.string.play else R.string.open))
                    }
                    if (item.kind == Kind.AUDIO) TextButton(onClick = { onDismiss(); Audio.enqueue(ctx, listOf(Players.toMediaItem(Open.offline(ctx, item)))) }, modifier = Modifier.focusRing(CircleShape)) {
                        Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, null)
                        Text(stringResource(R.string.add_to_queue))
                    }
                    if (!item.isFolder && item.url != null && g.settings.current.on(Feature.DOWNLOADS)) TextButton(onClick = {
                        onDismiss()
                        g.transfers.download(server.id, item.url!!, item.fileName, item.size.takeIf { it > 0 } ?: -1, item.mime, when (item.kind) { Kind.VIDEO -> "v"; Kind.AUDIO -> "a"; Kind.IMAGE -> "i"; else -> "" })
                        toast(ctx, queued)
                    }, modifier = Modifier.focusRing(CircleShape)) {
                        Icon(Icons.Rounded.Download, null)
                        Text(stringResource(R.string.download))
                    }
                    TextButton(onClick = { onDismiss(); onSelect() }, modifier = Modifier.focusRing(CircleShape)) {
                        Icon(Icons.Rounded.Checklist, null)
                        Text(stringResource(R.string.select))
                    }
                    if (server.isAkis && !item.isFolder) TextButton(onClick = { onDismiss(); nav.push(Route.Info(server.id, item.id, null, item.title)) }, modifier = Modifier.focusRing(CircleShape)) {
                        Icon(Icons.Rounded.Info, null)
                        Text(stringResource(R.string.properties))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}
