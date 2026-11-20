package com.alis.app.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SyncAlt
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.alis.app.R
import com.alis.app.ui.graph
import com.alis.core.data.Feature
import com.alis.core.player.TrackTools
import com.alis.core.ui.focusRing
import com.alis.core.ui.formatDuration
import kotlinx.coroutines.delay
import kotlin.math.abs

private val Scrim = Color.Black.copy(alpha = 0.55f)

/**
 * VLC tarzı oynatıcı arayüzü (Alış temasında).
 * Dokunmatik: tek dokunuş kontroller, çift dokunuş ±sarma, yatay kaydırma sarma, solda dikey parlaklık, sağda dikey ses.
 * Kumanda: OK oynat/duraklat, ←/→ sarma (basılı tutunca hızlanır), ↑ kontroller, ↓ ses / altyazı menüsü.
 */
@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(c: VideoController, pip: Boolean, tv: Boolean, onExit: () -> Unit, onPip: () -> Unit) {
    val ctx = LocalContext.current
    val act = ctx as Activity
    val g = graph()
    val s = g.settings.current
    var controls by remember { mutableStateOf(true) }
    var panel by remember { mutableStateOf<String?>(null) }
    var locked by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }
    var feedback by remember { mutableStateOf<Pair<ImageVector, String>?>(null) }
    val rootFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }
    val step = s.seekStepSec * 1000L
    val audio = remember { ctx.getSystemService(AudioManager::class.java) }

    fun show() {
        controls = true
        tick++
    }

    // Kontroller oynarken 4 sn sonra gizlenir
    LaunchedEffect(controls, tick, c.playing, panel) {
        if (controls && c.playing && panel == null) {
            delay(4000)
            controls = false
        }
    }
    LaunchedEffect(controls) {
        if (controls && tv) runCatching { playFocus.requestFocus() } else runCatching { rootFocus.requestFocus() }
    }
    LaunchedEffect(feedback) { if (feedback != null) { delay(900); feedback = null } }
    LaunchedEffect(c.message) { if (c.message != null) { delay(3500); c.message = null } }

    BackHandler(enabled = panel != null) { panel = null }
    BackHandler(enabled = panel == null && locked) { locked = false }
    BackHandler(enabled = panel == null && !locked) { onExit() }

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                tick++
                if (panel != null || locked) return@onPreviewKeyEvent false
                if (controls) {
                    // Kontroller açıkken odak gezinmesi normal çalışır; yalnızca ↓ en alttaysa menü
                    return@onPreviewKeyEvent false
                }
                val repeat = e.nativeKeyEvent.repeatCount
                when (e.key) {
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.Spacebar -> { c.toggle(); show(); true }
                    Key.DirectionLeft -> {
                        c.seekBy(-step * (1 + repeat / 4)); feedback = Icons.Rounded.Replay10 to formatDuration(c.position()); true
                    }
                    Key.DirectionRight -> {
                        c.seekBy(step * (1 + repeat / 4)); feedback = Icons.Rounded.Forward10 to formatDuration(c.position()); true
                    }
                    Key.DirectionUp -> { show(); true }
                    Key.DirectionDown -> { panel = "tracks"; true }
                    else -> false
                }
            }
            .focusRequester(rootFocus)
            .focusable(),
    ) {
        // Görüntü
        val frameMod = when (c.aspect) {
            "16:9" -> Modifier.aspectRatio(16f / 9f)
            "4:3" -> Modifier.aspectRatio(4f / 3f)
            else -> Modifier.fillMaxSize()
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            AndroidView(
                factory = { context ->
                    PlayerView(context).apply {
                        useController = false
                        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                        subtitleView?.visibility = android.view.View.GONE
                        setShutterBackgroundColor(android.graphics.Color.BLACK)
                        setKeepContentOnPlayerReset(true)
                        isFocusable = false
                        player = c.player
                    }
                },
                update = { v ->
                    v.resizeMode = when (c.aspect) {
                        "fill", "16:9", "4:3" -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                        "zoom" -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                },
                modifier = frameMod,
            )
        }

        // Altyazı (Alış'ın kendi çizicisi: gecikme, boyut, renk, konum)
        c.subText?.let { text ->
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val size = (if (pip) 12f else if (tv) 30f else 20f) * s.subSize
                Text(
                    text,
                    color = Color(c.subColor),
                    textAlign = TextAlign.Center,
                    style = TextStyle(
                        fontSize = size.sp, fontWeight = FontWeight.Medium,
                        shadow = Shadow(Color.Black, blurRadius = 6f),
                    ),
                    modifier = Modifier.align(Alignment.BottomCenter)
                        .padding(bottom = maxHeight * c.subBottom + if (controls && !pip) 90.dp else 0.dp, start = 32.dp, end = 32.dp)
                        .then(if (s.subBackground) Modifier.background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(6.dp)) else Modifier)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }

        if (c.buffering && c.error == null) CircularProgressIndicator(Modifier.align(Alignment.Center).size(56.dp), color = MaterialTheme.colorScheme.primary)

        // Dokunmatik hareketler
        if (!pip && !tv) GestureLayer(c, enabled = s.on(Feature.GESTURES) && !locked, step = step, audio = audio, act = act,
            onTap = { if (locked) { controls = !controls } else if (controls) controls = false else show() },
            onFeedback = { feedback = it })

        // Kısa geri bildirim (sarma, ses, parlaklık)
        feedback?.let { (icon, text) ->
            Surface(color = Scrim, shape = MaterialTheme.shapes.large, modifier = Modifier.align(Alignment.Center)) {
                Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, null, tint = Color.White, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(text, color = Color.White, style = MaterialTheme.typography.titleLarge)
                }
            }
        }

        // Hata
        c.error?.let { err ->
            Surface(color = Scrim, shape = MaterialTheme.shapes.large, modifier = Modifier.align(Alignment.Center).widthIn(max = 520.dp).padding(24.dp)) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.pl_error), color = Color.White, style = MaterialTheme.typography.titleLarge)
                    Text(err, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, maxLines = 4)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { c.load(c.index, c.position()) }, modifier = Modifier.focusRing(CircleShape)) { Text(stringResource(R.string.retry)) }
                        TextButton(onClick = { com.alis.app.ui.Open.external(ctx, c.item.url, "video/*") }, modifier = Modifier.focusRing(CircleShape)) {
                            Text(stringResource(R.string.pl_open_other), color = Color.White)
                        }
                    }
                }
            }
        }

        // Bildirim (remux'a geçildi vb.)
        c.message?.let {
            Surface(color = Scrim, shape = CircleShape, modifier = Modifier.align(Alignment.TopCenter).padding(top = 72.dp)) {
                Text(it, color = Color.White, modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp))
            }
        }

        // Kaldığın yerden
        var resumeChip by remember(c.item.id) { mutableStateOf(true) }
        LaunchedEffect(c.item.id, c.resumedFrom) { if (c.resumedFrom > 0) { delay(7000); resumeChip = false } }
        if (c.resumedFrom > 0 && resumeChip && !pip) {
            Surface(color = Scrim, shape = CircleShape, modifier = Modifier.align(Alignment.BottomStart).padding(start = 24.dp, bottom = if (controls) 120.dp else 32.dp)) {
                Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.pl_resumed, formatDuration(c.resumedFrom)), color = Color.White, style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { c.seekTo(0); resumeChip = false }, modifier = Modifier.focusRing(CircleShape)) { Text(stringResource(R.string.pl_from_start)) }
                }
            }
        }

        // Kilit
        if (locked && controls && !pip) {
            IconButton(onClick = { locked = false; show() }, modifier = Modifier.align(Alignment.CenterStart).padding(24.dp).size(56.dp).background(Scrim, CircleShape)) {
                Icon(Icons.Rounded.Lock, stringResource(R.string.pl_unlock), tint = Color.White)
            }
        }

        // Kontroller
        AnimatedVisibility(controls && !locked && !pip && panel == null, enter = fadeIn(), exit = fadeOut()) {
            Controls(c, tv, playFocus, onExit, onPip,
                onPanel = { panel = it },
                onLock = { locked = true; controls = false },
                onInteract = { tick++ })
        }

        // Yan menüler
        AnimatedVisibility(
            panel != null && !pip,
            enter = slideInHorizontally { it } + fadeIn(),
            exit = slideOutHorizontally { it } + fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            SidePanel(c, panel ?: "", tv, onPanel = { panel = it }, onClose = { panel = null })
        }
    }
}

@Composable
private fun GestureLayer(
    c: VideoController,
    enabled: Boolean,
    step: Long,
    audio: AudioManager,
    act: Activity,
    onTap: () -> Unit,
    onFeedback: (Pair<ImageVector, String>) -> Unit,
) {
    var dragMode by remember { mutableIntStateOf(0) } // 0 yok, 1 sarma, 2 parlaklık, 3 ses
    var seekTarget by remember { mutableStateOf(0L) }
    var acc by remember { mutableFloatStateOf(0f) }
    var volF by remember { mutableFloatStateOf(0f) }
    val maxVol = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    Box(
        Modifier.fillMaxSize()
            .pointerInput(enabled) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { o ->
                        if (!enabled) return@detectTapGestures
                        if (o.x < size.width / 3f) { c.seekBy(-step); onFeedback(Icons.Rounded.Replay10 to "-${step / 1000}s") }
                        else if (o.x > size.width * 2f / 3f) { c.seekBy(step); onFeedback(Icons.Rounded.Forward10 to "+${step / 1000}s") }
                        else c.toggle()
                    },
                )
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectDragGestures(
                    onDragStart = { o ->
                        dragMode = 0
                        acc = 0f
                        seekTarget = c.position()
                        volF = audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
                        // Yön ilk harekette belirlenir; başlangıç noktası sol / sağ yarıyı seçer
                        acc = o.x
                    },
                    onDragEnd = {
                        if (dragMode == 1) c.seekTo(seekTarget)
                        dragMode = 0
                    },
                    onDragCancel = { dragMode = 0 },
                    onDrag = { change, d ->
                        change.consume()
                        if (dragMode == 0) {
                            dragMode = if (abs(d.x) > abs(d.y)) 1 else if (acc < size.width / 2f) 2 else 3
                        }
                        when (dragMode) {
                            1 -> {
                                val dur = c.duration().coerceAtLeast(1)
                                seekTarget = (seekTarget + (d.x / size.width) * minOf(dur, 600_000L)).toLong().coerceIn(0, dur)
                                onFeedback(Icons.Rounded.SyncAlt to formatDuration(seekTarget) + " / " + formatDuration(dur))
                            }
                            2 -> {
                                val lp = act.window.attributes
                                val cur = if (lp.screenBrightness < 0) 0.5f else lp.screenBrightness
                                lp.screenBrightness = (cur - d.y / size.height).coerceIn(0.01f, 1f)
                                act.window.attributes = lp
                                onFeedback(Icons.Rounded.BrightnessMedium to "${(lp.screenBrightness * 100).toInt()}%")
                            }
                            3 -> {
                                val cur = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                                volF = (volF - d.y / size.height * maxVol * 1.5f).coerceIn(0f, maxVol.toFloat())
                                val target = Math.round(volF).coerceIn(0, maxVol)
                                if (target != cur) audio.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
                                onFeedback(Icons.Rounded.VolumeUp to "${target * 100 / maxVol.coerceAtLeast(1)}%")
                            }
                        }
                    },
                )
            },
    )
}

@Composable
private fun Controls(
    c: VideoController,
    tv: Boolean,
    playFocus: FocusRequester,
    onExit: () -> Unit,
    onPip: () -> Unit,
    onPanel: (String) -> Unit,
    onLock: () -> Unit,
    onInteract: () -> Unit,
) {
    val ctx = LocalContext.current
    val act = ctx as Activity
    val g = graph()
    var dragging by remember { mutableStateOf<Float?>(null) }
    val dur = c.durationMs
    val pos = c.positionMs
    val big = if (tv) 64.dp else 56.dp
    Box(Modifier.fillMaxSize()) {
        // Üst
        Row(
            Modifier.fillMaxWidth().background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Scrim, Color.Transparent)))
                .padding(horizontal = if (tv) 40.dp else 12.dp, vertical = if (tv) 24.dp else 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CtlIcon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) { onExit() }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(c.item.title, color = Color.White, style = if (tv) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                c.server?.name?.let { Text(it + if (c.queue.size > 1) " · ${c.index + 1}/${c.queue.size}" else "", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelMedium) }
            }
            CtlIcon(Icons.Rounded.Audiotrack, stringResource(R.string.pl_audio)) { onPanel("audio") }
            CtlIcon(Icons.Rounded.ClosedCaption, stringResource(R.string.pl_subs)) { onPanel("subs") }
            CtlIcon(Icons.Rounded.Speed, stringResource(R.string.pl_speed)) { onPanel("speed") }
            if (c.queue.size > 1) CtlIcon(Icons.AutoMirrored.Rounded.PlaylistPlay, stringResource(R.string.pl_playlist)) { onPanel("queue") }
            if (!tv && g.settings.current.on(Feature.PIP)) CtlIcon(Icons.Rounded.PictureInPictureAlt, stringResource(R.string.pl_pip)) { onPip() }
            CtlIcon(Icons.Rounded.MoreVert, stringResource(R.string.more)) { onPanel("more") }
        }
        // Orta
        Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (tv) 28.dp else 18.dp)) {
            CtlIcon(Icons.Rounded.SkipPrevious, stringResource(R.string.pl_prev), size = big * 0.8f, enabled = c.hasPrev() || c.position() > 5000) { c.prev(); onInteract() }
            CtlIcon(Icons.Rounded.Replay10, stringResource(R.string.pl_back10), size = big * 0.8f) { c.seekBy(-g.settings.current.seekStepSec * 1000L); onInteract() }
            Surface(
                onClick = { c.toggle(); onInteract() },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(big * 1.35f).focusRequester(playFocus).focusRing(CircleShape, 1.1f, 4.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(if (c.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, stringResource(if (c.playing) R.string.pause else R.string.play), Modifier.size(big * 0.75f))
                }
            }
            CtlIcon(Icons.Rounded.Forward10, stringResource(R.string.pl_fwd10), size = big * 0.8f) { c.seekBy(g.settings.current.seekStepSec * 1000L); onInteract() }
            CtlIcon(Icons.Rounded.SkipNext, stringResource(R.string.pl_next), size = big * 0.8f, enabled = c.hasNext()) { c.next(); onInteract() }
        }
        // Alt
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, Scrim)))
                .padding(horizontal = if (tv) 48.dp else 16.dp, vertical = if (tv) 28.dp else 12.dp),
        ) {
            Slider(
                value = dragging ?: if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f,
                onValueChange = { dragging = it; onInteract() },
                onValueChangeFinished = { dragging?.let { f -> c.seekTo((f * dur).toLong()) }; dragging = null },
                colors = SliderDefaults.colors(inactiveTrackColor = Color.White.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth().focusRing(MaterialTheme.shapes.small, 1f),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatDuration(dragging?.let { (it * dur).toLong() } ?: pos), color = Color.White, style = MaterialTheme.typography.labelLarge)
                Text(" / " + formatDuration(dur), color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelLarge)
                if (dur > 0) Text("  (−" + formatDuration(dur - pos) + ")", color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.weight(1f))
                if (c.speed != 1f) Text("${c.speed}×", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(end = 8.dp))
                CtlIcon(if (c.repeat == 1) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, stringResource(R.string.repeat), tint = if (c.repeat > 0) MaterialTheme.colorScheme.primary else Color.White) {
                    c.repeat = (c.repeat + 1) % 3
                }
                if (c.queue.size > 1) CtlIcon(Icons.Rounded.Shuffle, stringResource(R.string.shuffle), tint = if (c.shuffle) MaterialTheme.colorScheme.primary else Color.White) { c.shuffle = !c.shuffle }
                CtlIcon(Icons.Rounded.AspectRatio, stringResource(R.string.pl_aspect)) { onPanel("aspect") }
                if (!tv) {
                    CtlIcon(Icons.Rounded.ScreenRotation, stringResource(R.string.pl_rotate)) {
                        act.requestedOrientation = when (act.requestedOrientation) {
                            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                            else -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        }
                    }
                    CtlIcon(Icons.Rounded.LockOpen, stringResource(R.string.pl_lock)) { onLock() }
                }
            }
        }
    }
}

@Composable
private fun CtlIcon(icon: ImageVector, label: String, size: androidx.compose.ui.unit.Dp = 44.dp, enabled: Boolean = true, tint: Color = Color.White, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(size).focusRing(CircleShape, 1.15f)) {
        Icon(icon, label, tint = if (enabled) tint else tint.copy(alpha = 0.35f), modifier = Modifier.size(size * 0.6f))
    }
}

// ---------------- Yan menüler ----------------

@Composable
private fun SidePanel(c: VideoController, which: String, tv: Boolean, onPanel: (String) -> Unit, onClose: () -> Unit) {
    val first = remember(which) { FocusRequester() }
    LaunchedEffect(which) { delay(80); runCatching { first.requestFocus() } }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.97f),
        shape = RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp),
        modifier = Modifier.fillMaxHeight().width(if (tv) 420.dp else 340.dp),
    ) {
        // Parça listesi değişince (yeni dosya, remux) menü yeniden kurulur
        androidx.compose.runtime.key(c.tracksVersion, c.remux, c.probeTracks.size, c.externalSubs.size) {
        LazyColumn(contentPadding = PaddingValues(vertical = 20.dp, horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            fun title(t: Int) = item { Text(stringResource(t), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 8.dp, bottom = 10.dp)) }
            when (which) {
                "tracks", "audio" -> {
                    title(R.string.pl_audio)
                    if (c.remux) {
                        val list = c.probeTracks.filter { !it.video }
                        itemsIndexed(list) { i, t ->
                            Choice(listOfNotNull(t.name.ifEmpty { null }, t.lang.ifEmpty { null }, t.codec.uppercase(), if (t.channels >= 6) "5.1" else null).joinToString(" · ").ifEmpty { "#${i + 1}" },
                                t.index == c.remuxAudio, if (i == 0) Modifier.focusRequester(first) else Modifier) { c.selectRemuxAudio(t) }
                        }
                    } else {
                        val opts = TrackTools.options(c.player, C.TRACK_TYPE_AUDIO)
                        itemsIndexed(opts) { i, o ->
                            Choice(o.label + if (!o.supported) " ⚠" else "", o.selected, if (i == 0) Modifier.focusRequester(first) else Modifier) { c.selectAudio(o) }
                        }
                        if (opts.isEmpty()) item { Text("—", Modifier.padding(12.dp).focusRequester(first).focusable()) }
                    }
                    if (which == "tracks") {
                        item { Spacer(Modifier.height(14.dp)) }
                        subtitleItems(c, null)
                    }
                }
                "subs" -> {
                    title(R.string.pl_subs)
                    subtitleItems(c, first)
                    item { Spacer(Modifier.height(10.dp)) }
                    item { MenuRow(Icons.Rounded.Timer, stringResource(R.string.pl_sub_delay_short), "${c.subDelayMs} ms") { onPanel("delay") } }
                    item { MenuRow(Icons.Rounded.ClosedCaption, stringResource(R.string.pl_sub_style), "") { onPanel("style") } }
                }
                "speed" -> {
                    title(R.string.pl_speed)
                    val speeds = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f, 4f)
                    itemsIndexed(speeds) { i, v -> Choice("${v}×", c.speed == v, if (v == 1f) Modifier.focusRequester(first) else Modifier) { c.setSpeed(v) } }
                }
                "aspect" -> {
                    title(R.string.pl_aspect)
                    val opts = listOf("fit" to R.string.ar_fit, "fill" to R.string.ar_fill, "zoom" to R.string.ar_zoom, "16:9" to R.string.ar_169, "4:3" to R.string.ar_43)
                    itemsIndexed(opts) { i, (k, l) -> Choice(stringResource(l), c.aspect == k, if (i == 0) Modifier.focusRequester(first) else Modifier) { c.aspect = k } }
                }
                "delay" -> {
                    title(R.string.pl_delays)
                    item { DelayRow(stringResource(R.string.pl_sub_delay), c.subDelayMs, 100, Modifier.focusRequester(first)) { c.subDelayMs = it } }
                    item { Text(stringResource(R.string.pl_sub_delay_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp)) }
                    item { DelayRow(stringResource(R.string.pl_audio_delay), c.audioDelayMs, 50, Modifier) { c.setAudioDelay(it) } }
                    item { Text(stringResource(R.string.pl_audio_delay_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp)) }
                }
                "style" -> {
                    title(R.string.pl_sub_style)
                    val colors = listOf(0xFFFFFFFF.toInt() to R.string.col_white, 0xFFFFEB3B.toInt() to R.string.col_yellow, 0xFF80DEEA.toInt() to R.string.col_cyan, 0xFFA5D6A7.toInt() to R.string.col_green)
                    itemsIndexed(colors) { i, (col, l) -> Choice(stringResource(l), c.subColor == col, if (i == 0) Modifier.focusRequester(first) else Modifier) { c.subColor = col } }
                    item {
                        Column(Modifier.padding(8.dp)) {
                            Text(stringResource(R.string.pl_sub_position), style = MaterialTheme.typography.labelLarge)
                            Slider(c.subBottom, { c.subBottom = it }, valueRange = 0f..0.4f, modifier = Modifier.focusRing(MaterialTheme.shapes.small, 1f))
                        }
                    }
                    item {
                        val g = graph()
                        var size by remember { mutableFloatStateOf(g.settings.current.subSize) }
                        Column(Modifier.padding(8.dp)) {
                            Text(stringResource(R.string.set_sub_size, (size * 100).toInt()), style = MaterialTheme.typography.labelLarge)
                            Slider(size, { size = it; g.settings.setSubSize(it) }, valueRange = 0.6f..2f, modifier = Modifier.focusRing(MaterialTheme.shapes.small, 1f))
                        }
                    }
                }
                "eq" -> {
                    title(R.string.pl_eq)
                    val presets = c.eqPresets()
                    item { Choice(stringResource(R.string.pl_eq_off), c.eqPreset < 0, Modifier.focusRequester(first)) { c.setEq(-1) } }
                    itemsIndexed(presets) { i, p -> Choice(p, c.eqPreset == i, Modifier) { c.setEq(i) } }
                    item {
                        Column(Modifier.padding(8.dp)) {
                            Text(stringResource(R.string.pl_boost, c.boost / 100), style = MaterialTheme.typography.labelLarge)
                            Slider(c.boost.toFloat(), { c.setBoostLevel(it.toInt()) }, valueRange = 0f..1500f, steps = 14, modifier = Modifier.focusRing(MaterialTheme.shapes.small, 1f))
                        }
                    }
                }
                "queue" -> {
                    title(R.string.pl_playlist)
                    itemsIndexed(c.queue) { i, it ->
                        Choice(it.title, i == c.index, if (i == c.index) Modifier.focusRequester(first) else Modifier) { c.load(i) }
                    }
                }
                "jump" -> {
                    title(R.string.pl_jump)
                    item {
                        var text by remember { mutableStateOf("") }
                        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                text, { text = it.filter { ch -> ch.isDigit() || ch == ':' } },
                                label = { Text("hh:mm:ss") }, singleLine = true,
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth().focusRequester(first),
                            )
                            FilledTonalButton(onClick = {
                                val parts = text.split(':').mapNotNull { it.toLongOrNull() }
                                val sec = parts.fold(0L) { a, b -> a * 60 + b }
                                c.seekTo(sec * 1000)
                                onClose()
                            }, modifier = Modifier.focusRing(CircleShape)) { Text(stringResource(R.string.ok)) }
                        }
                    }
                }
                "info" -> {
                    title(R.string.pl_info)
                    val rows = c.techInfo()
                    itemsIndexed(rows) { i, (k, v) ->
                        Column(Modifier.fillMaxWidth().padding(8.dp).then(if (i == 0) Modifier.focusRequester(first).focusable() else Modifier)) {
                            Text(k, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(v, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                else -> { // "more"
                    title(R.string.more)
                    item { MenuRow(Icons.Rounded.Timer, stringResource(R.string.pl_delays), "${c.subDelayMs} / ${c.audioDelayMs} ms", Modifier.focusRequester(first)) { onPanel("delay") } }
                    item { MenuRow(Icons.Rounded.ClosedCaption, stringResource(R.string.pl_sub_style), "") { onPanel("style") } }
                    item { MenuRow(Icons.Rounded.AspectRatio, stringResource(R.string.pl_aspect), "") { onPanel("aspect") } }
                    item { MenuRow(Icons.Rounded.Equalizer, stringResource(R.string.pl_eq), "") { onPanel("eq") } }
                    item { MenuRow(Icons.Rounded.SyncAlt, stringResource(R.string.pl_jump), "") { onPanel("jump") } }
                    if (c.queue.size > 1) item { MenuRow(Icons.AutoMirrored.Rounded.PlaylistPlay, stringResource(R.string.pl_playlist), "${c.index + 1}/${c.queue.size}") { onPanel("queue") } }
                    item { MenuRow(Icons.Rounded.Info, stringResource(R.string.pl_info), "") { onPanel("info") } }
                }
            }
        }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.subtitleItems(c: VideoController, first: FocusRequester?) {
    item { Choice(androidx.compose.ui.res.stringResource(R.string.pl_subs_off), c.subChoice == SubChoice.Off, if (first != null) Modifier.focusRequester(first) else Modifier) { c.subtitlesOff() } }
    itemsIndexed(c.externalSubs) { i, sub ->
        Choice(listOfNotNull(sub.label.ifEmpty { null }, sub.lang.ifEmpty { null }).joinToString(" · ").ifEmpty { "#${i + 1}" } + if (sub.forced) " (forced)" else "",
            c.subChoice == SubChoice.External(i), Modifier) { c.selectExternal(i) }
    }
    item {
        val emb = TrackTools.options(c.player, C.TRACK_TYPE_TEXT)
        Column {
            emb.forEach { o ->
                Choice(o.label + " · " + androidx.compose.ui.res.stringResource(R.string.pl_embedded), c.subChoice == SubChoice.Embedded(o.label), Modifier) { c.selectEmbedded(o) }
            }
        }
    }
}

@Composable
private fun Choice(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        modifier = modifier.fillMaxWidth().focusRing(MaterialTheme.shapes.medium, 1.02f),
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected, null)
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun MenuRow(icon: ImageVector, label: String, value: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = MaterialTheme.shapes.medium, color = Color.Transparent, modifier = modifier.fillMaxWidth().focusRing(MaterialTheme.shapes.medium, 1.02f)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(14.dp))
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            if (value.isNotEmpty()) Text(value, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** − / sıfırla / + düğmeleriyle gecikme ayarı (kumandayla da kolay). */
@Composable
private fun DelayRow(label: String, value: Long, stepMs: Long, modifier: Modifier, onChange: (Long) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(8.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilledTonalButton(onClick = { onChange(value - stepMs) }, modifier = modifier.focusRing(CircleShape)) { Text("−${stepMs}") }
            Text("$value ms", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            FilledTonalButton(onClick = { onChange(value + stepMs) }, modifier = Modifier.focusRing(CircleShape)) { Text("+${stepMs}") }
        }
        TextButton(onClick = { onChange(0) }, modifier = Modifier.focusRing(CircleShape)) { Text(stringResource(R.string.pl_reset)) }
    }
}
