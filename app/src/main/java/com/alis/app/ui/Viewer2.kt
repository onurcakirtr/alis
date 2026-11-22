package com.alis.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Slideshow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import coil3.compose.SubcomposeAsyncImage
import com.alis.app.R
import com.alis.core.data.Feature
import com.alis.core.player.Audio
import com.alis.core.ui.LoadingBox
import com.alis.core.ui.focusRing
import com.alis.core.ui.formatDuration
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Foto görüntüleyici: kaydırma, iki parmakla / çift dokunuşla yakınlaştırma, slayt gösterisi.
 * Kumandada ←/→ önceki/sonraki, OK kontrolleri göster/gizle.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhotoViewer() {
    val g = graph()
    val nav = nav()
    val ctx = LocalContext.current
    val server = PhotoSession.server
    val items = PhotoSession.items
    if (server == null || items.isEmpty()) {
        LaunchedEffect(Unit) { nav.pop() }
        return
    }
    val pager = rememberPagerState(initialPage = PhotoSession.index) { items.size }
    val scope = rememberCoroutineScope()
    var chrome by remember { mutableStateOf(true) }
    var slideshow by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val slideOn = g.settings.current.on(Feature.SLIDESHOW)
    val seconds = g.settings.current.slideshowSec
    val queued = stringResource(R.string.download_queued)
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    LaunchedEffect(slideshow, pager.currentPage) {
        if (!slideshow) return@LaunchedEffect
        delay(seconds * 1000L)
        pager.animateScrollToPage(if (pager.currentPage + 1 < items.size) pager.currentPage + 1 else 0)
    }
    LaunchedEffect(pager.currentPage) { PhotoSession.index = pager.currentPage }

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .focusRequester(focus)
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (e.key) {
                    Key.DirectionLeft -> { scope.launch { pager.animateScrollToPage((pager.currentPage - 1).coerceAtLeast(0)) }; true }
                    Key.DirectionRight -> { scope.launch { pager.animateScrollToPage((pager.currentPage + 1).coerceAtMost(items.size - 1)) }; true }
                    Key.DirectionCenter, Key.Enter -> { chrome = !chrome; true }
                    Key.MediaPlayPause, Key.MediaPlay -> { if (slideOn) slideshow = !slideshow; true }
                    else -> false
                }
            }
            .focusable(),
    ) {
        HorizontalPager(pager, modifier = Modifier.fillMaxSize(), beyondViewportPageCount = 1) { page ->
            ZoomImage(items[page].url, onTap = { chrome = !chrome })
        }
        AnimatedVisibility(chrome, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter)) {
            Row(
                Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.45f)).statusBarsPadding().padding(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { nav.pop() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back), tint = Color.White) }
                Column(Modifier.weight(1f)) {
                    Text(items[pager.currentPage].title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                    Text("${pager.currentPage + 1} / ${items.size}", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelMedium)
                }
                if (slideOn) IconButton(onClick = { slideshow = !slideshow }) {
                    Icon(if (slideshow) Icons.Rounded.Stop else Icons.Rounded.Slideshow, stringResource(R.string.slideshow), tint = Color.White)
                }
                if (g.settings.current.on(Feature.DOWNLOADS)) IconButton(onClick = {
                    val it = items[pager.currentPage]
                    it.url?.let { u -> g.transfers.download(server.id, u, it.fileName, it.size, it.mime, "i") }
                    toast(ctx, queued)
                }) { Icon(Icons.Rounded.Download, stringResource(R.string.download), tint = Color.White) }
                if (server.isAkis) IconButton(onClick = {
                    val it = items[pager.currentPage]
                    nav.push(Route.Info(server.id, it.id, null, it.title))
                }) { Icon(Icons.Rounded.Info, stringResource(R.string.properties), tint = Color.White) }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ZoomImage(url: String?, onTap: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val state = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 6f)
        offset = if (scale <= 1f) Offset.Zero else offset + pan
    }
    SubcomposeAsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        loading = { LoadingBox() },
        modifier = Modifier.fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = {
                        if (scale > 1f) { scale = 1f; offset = Offset.Zero } else scale = 2.5f
                    },
                )
            }
            .transformable(state, canPan = { scale > 1f })
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            },
    )
}

/** Tam ekran müzik: kapak, ilerleme, kontroller, karışık / tekrar ve çalma sırası. */
@Composable
fun MusicScreen() {
    val now by Audio.now.collectAsStateWithLifecycle()
    val player by Audio.player.collectAsStateWithLifecycle()
    val p = player
    var pos by remember { mutableLongStateOf(0L) }
    var dur by remember { mutableLongStateOf(0L) }
    var dragging by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(p) {
        while (p != null) {
            pos = p.currentPosition
            dur = p.duration.coerceAtLeast(0)
            delay(500)
        }
    }
    val meta = now.item?.mediaMetadata
    val wide = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 700

    val controls: @Composable () -> Unit = {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.widthIn(max = 520.dp).padding(20.dp)) {
            Box(Modifier.fillMaxWidth(0.8f).aspectRatio(1f).clip(MaterialTheme.shapes.extraLarge)) {
                Thumb(meta?.artworkUri?.toString(), Icons.Rounded.Audiotrack, Modifier.fillMaxSize(), 96.dp)
            }
            Spacer(Modifier.height(6.dp))
            Text(meta?.title?.toString().orEmpty(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 2)
            Text(listOfNotNull(meta?.artist, meta?.albumTitle).joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Slider(
                value = dragging ?: if (dur > 0) pos.toFloat() / dur else 0f,
                onValueChange = { dragging = it },
                onValueChangeFinished = { dragging?.let { f -> p?.seekTo((f * dur).toLong()) }; dragging = null },
                modifier = Modifier.fillMaxWidth().focusRing(MaterialTheme.shapes.small, 1f),
            )
            Row(Modifier.fillMaxWidth()) {
                Text(formatDuration(pos), style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.weight(1f))
                Text(formatDuration(dur), style = MaterialTheme.typography.labelMedium)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IconButton(onClick = { p?.shuffleModeEnabled = !p.shuffleModeEnabled }, modifier = Modifier.focusRing(CircleShape)) {
                    Icon(Icons.Rounded.Shuffle, stringResource(R.string.shuffle), tint = if (now.shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { p?.seekToPreviousMediaItem() }, modifier = Modifier.focusRing(CircleShape)) { Icon(Icons.Rounded.SkipPrevious, null, Modifier.size(36.dp)) }
                FilledIconButton(
                    onClick = { if (now.playing) p?.pause() else p?.play() },
                    modifier = Modifier.size(72.dp).focusRing(CircleShape),
                    colors = IconButtonDefaults.filledIconButtonColors(),
                ) { Icon(if (now.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, Modifier.size(40.dp)) }
                IconButton(onClick = { p?.seekToNextMediaItem() }, modifier = Modifier.focusRing(CircleShape)) { Icon(Icons.Rounded.SkipNext, null, Modifier.size(36.dp)) }
                IconButton(onClick = {
                    p?.repeatMode = when (p.repeatMode) {
                        Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                        Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                        else -> Player.REPEAT_MODE_OFF
                    }
                }, modifier = Modifier.focusRing(CircleShape)) {
                    Icon(
                        if (now.repeat == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, stringResource(R.string.repeat),
                        tint = if (now.repeat != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
    val queue: @Composable (Modifier) -> Unit = { m ->
        val count = now.count
        LazyColumn(m, contentPadding = PaddingValues(12.dp)) {
            itemsIndexed(List(count) { it }) { i, _ ->
                val item = p?.getMediaItemAt(i)
                Surface(
                    onClick = { p?.seekTo(i, 0); p?.play() },
                    color = if (i == now.index) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().focusRing(MaterialTheme.shapes.medium, 1.01f),
                ) {
                    ItemRowContent(
                        item?.mediaMetadata?.title?.toString().orEmpty(), item?.mediaMetadata?.artist?.toString().orEmpty(),
                        item?.mediaMetadata?.artworkUri?.toString(), Icons.Rounded.Audiotrack, false,
                    )
                }
            }
        }
    }
    Column(Modifier.fillMaxSize()) {
        com.alis.app.mobile.TopBar(stringResource(R.string.now_playing), stringResource(R.string.queue_n, now.count))
        if (now.item == null) {
            com.alis.core.ui.MessageBox(Icons.Rounded.Audiotrack, stringResource(R.string.nothing_playing))
        } else if (wide) {
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { controls() }
                queue(Modifier.weight(1f))
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                item { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { controls() } }
                item { com.alis.core.ui.SectionTitle(stringResource(R.string.queue), Modifier.padding(horizontal = 16.dp)) }
                items(now.count) { i ->
                    val item = p?.getMediaItemAt(i)
                    Surface(
                        onClick = { p?.seekTo(i, 0); p?.play() },
                        color = if (i == now.index) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).focusRing(MaterialTheme.shapes.medium, 1.01f),
                    ) {
                        ItemRowContent(
                            item?.mediaMetadata?.title?.toString().orEmpty(), item?.mediaMetadata?.artist?.toString().orEmpty(),
                            item?.mediaMetadata?.artworkUri?.toString(), Icons.Rounded.Audiotrack, false,
                        )
                    }
                }
            }
        }
    }
}
