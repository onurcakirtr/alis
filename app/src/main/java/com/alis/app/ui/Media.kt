package com.alis.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.alis.core.model.Item
import com.alis.core.model.Kind
import com.alis.core.player.Audio
import com.alis.core.ui.formatBytes
import com.alis.core.ui.formatDuration

fun kindIcon(k: Kind?): ImageVector = when (k) {
    Kind.FOLDER -> Icons.Rounded.Folder
    Kind.VIDEO -> Icons.Rounded.Movie
    Kind.AUDIO -> Icons.Rounded.Audiotrack
    Kind.IMAGE -> Icons.Rounded.Image
    else -> Icons.Rounded.InsertDriveFile
}

/** Küçük resim; yüklenemezse türün ikonu. */
@Composable
fun Thumb(url: String?, icon: ImageVector, modifier: Modifier = Modifier, iconSize: Dp = 36.dp, crop: Boolean = true) {
    val placeholder: @Composable () -> Unit = {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(iconSize), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (url == null) {
        Box(modifier) { placeholder() }
        return
    }
    SubcomposeAsyncImage(
        model = url,
        contentDescription = null,
        contentScale = if (crop) ContentScale.Crop else ContentScale.Fit,
        modifier = modifier,
        loading = { placeholder() },
        error = { placeholder() },
    )
}

/** Kitaplık öğesinin alt satırı: süre / boyut / sanatçı. */
fun itemMeta(i: Item): String = listOfNotNull(
    i.artist,
    i.durationMs.takeIf { it > 0 }?.let { formatDuration(it) },
    if (i.kind == Kind.FOLDER) i.childCount.takeIf { it >= 0 }?.toString() else i.size.takeIf { it > 0 }?.let { formatBytes(it) },
).joinToString(" · ")

/** Izgara kartı: video 16:9, foto kare, müzik kare kapak; ilerleme çubuğu ve izlendi işareti. */
@Composable
fun MediaCard(item: Item, selected: Boolean, modifier: Modifier = Modifier, aspect: Float = if (item.kind == Kind.VIDEO) 16f / 10f else 1f) {
    val cs = MaterialTheme.colorScheme
    Column(modifier) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(aspect).clip(MaterialTheme.shapes.medium)
                .background(cs.surfaceContainerHigh),
        ) {
            Thumb(if (item.isFolder) null else item.thumb, kindIcon(item.kind), Modifier.fillMaxSize())
            if (item.progress > 0f) {
                LinearProgressIndicator(
                    progress = { item.progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(4.dp).align(Alignment.BottomCenter),
                    color = cs.primary,
                    trackColor = Color.Black.copy(alpha = 0.4f),
                )
            }
            if (item.watched) {
                Icon(
                    Icons.Rounded.CheckCircle, null,
                    Modifier.align(Alignment.TopEnd).padding(6.dp).size(22.dp).background(Color.Black.copy(alpha = 0.45f), CircleShape),
                    tint = Color.White,
                )
            }
            if (selected) {
                Box(Modifier.fillMaxSize().background(cs.primary.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.CheckCircle, null, Modifier.size(40.dp), tint = cs.onPrimary)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(item.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        val meta = itemMeta(item)
        if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, maxLines = 1)
    }
}

/** Liste satırı. */
@Composable
fun ItemRowContent(title: String, subtitle: String, thumb: String?, icon: ImageVector, selected: Boolean, trailing: (@Composable () -> Unit)? = null) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(52.dp).clip(MaterialTheme.shapes.small)) {
            Thumb(thumb, icon, Modifier.fillMaxSize(), 24.dp)
            if (selected) Box(Modifier.fillMaxSize().background(cs.primary.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.CheckCircle, null, tint = cs.onPrimary)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotEmpty()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1)
        }
        trailing?.invoke()
    }
}

/** Sunucu simgesi (Akış'ın kendi simgesi ya da DLNA tanımındaki). */
@Composable
fun ServerIcon(url: String?, size: Dp = 48.dp) {
    Box(Modifier.size(size).clip(MaterialTheme.shapes.medium)) {
        Thumb(url, Icons.Rounded.Dns, Modifier.fillMaxSize(), size * 0.5f, crop = false)
    }
}

/** Çalan şarkı çubuğu (mobil ve TV). */
@Composable
fun MiniPlayer(now: Audio.Now, modifier: Modifier = Modifier, onOpen: () -> Unit) {
    val p = Audio.player.value
    val meta = now.item?.mediaMetadata
    Surface(
        onClick = onOpen,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        tonalElevation = 4.dp,
        shadowElevation = 6.dp,
        modifier = modifier,
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(44.dp).clip(MaterialTheme.shapes.small)) {
                Thumb(meta?.artworkUri?.toString(), Icons.Rounded.Audiotrack, Modifier.fillMaxSize(), 22.dp)
            }
            Column(Modifier.weight(1f)) {
                Text(meta?.title?.toString().orEmpty(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                meta?.artist?.let { Text(it.toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
            }
            IconButton(onClick = { if (now.playing) p?.pause() else p?.play() }) {
                Icon(if (now.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null)
            }
            IconButton(onClick = { p?.seekToNextMediaItem() }, enabled = now.index < now.count - 1) { Icon(Icons.Rounded.SkipNext, null) }
        }
    }
}
