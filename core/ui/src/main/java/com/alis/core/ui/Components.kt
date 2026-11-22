package com.alis.core.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.alis.core.ui.theme.Backdrop
import com.alis.core.ui.theme.LocalAppTheme
import com.alis.core.ui.theme.Themes
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

// ---------------- Odak (kumanda / klavye) ----------------

/**
 * Odak halkası: odaklanınca tema renginde çerçeve ve hafif büyüme (yaylı).
 * Tıklanabilir öğenin ÖNÜNE konur: Modifier.focusRing().clickable { … }
 */
fun Modifier.focusRing(shape: Shape? = null, scale: Float = 1.04f, width: Dp = 3.dp): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    val s by animateFloatAsState(if (focused) scale else 1f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow), label = "focus")
    val sh = shape ?: MaterialTheme.shapes.large
    val color = MaterialTheme.colorScheme.primary
    this
        .graphicsLayer {
            scaleX = s
            scaleY = s
        }
        .then(if (focused) Modifier.border(width, color, sh) else Modifier)
        .onFocusChanged { focused = it.isFocused || it.hasFocus }
}

/**
 * Kumanda / klavye ile uzun basma: OK basılı tutulunca ya da Menü tuşuyla [onLong].
 * (Compose'un dokunmatik uzun basması tuşlarda çalışmaz.) Uzun basıştan sonraki tuş bırakma tıklama sayılmaz.
 */
fun Modifier.dpadLongPress(onLong: () -> Unit): Modifier = composed {
    var fired by remember { mutableStateOf(false) }
    this.onPreviewKeyEvent { e ->
        val center = e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter
        when {
            e.key == Key.Menu && e.type == KeyEventType.KeyUp -> { onLong(); true }
            center && e.type == KeyEventType.KeyDown && e.nativeKeyEvent.repeatCount >= 1 -> {
                if (!fired) {
                    fired = true
                    onLong()
                }
                true
            }
            center && e.type == KeyEventType.KeyUp && fired -> { fired = false; true }
            else -> false
        }
    }
}

// ---------------- Zemin ----------------

/** Temanın arka plan deseni (nokta ızgarası, çizgi ızgarası, tarama çizgileri ya da hiçbiri). */
fun Modifier.backdrop(kind: Backdrop, color: Color): Modifier = when (kind) {
    Backdrop.NONE -> this
    Backdrop.DOTS -> dotGrid(color)
    Backdrop.GRID -> drawWithCache {
        val step = 28.dp.toPx()
        val w = 1.dp.toPx()
        onDrawBehind {
            var x = 0f
            while (x < size.width) { drawLine(color, Offset(x, 0f), Offset(x, size.height), w); x += step }
            var y = 0f
            while (y < size.height) { drawLine(color, Offset(0f, y), Offset(size.width, y), w); y += step }
        }
    }
    Backdrop.SCANLINES -> drawWithCache {
        val step = 4.dp.toPx()
        val w = 1.dp.toPx()
        onDrawBehind {
            var y = 0f
            while (y < size.height) { drawLine(color, Offset(0f, y), Offset(size.width, y), w); y += step }
        }
    }
}

/** Nothing tarzı nokta ızgarası arka planı. */
fun Modifier.dotGrid(color: Color, spacing: Dp = 18.dp, dot: Dp = 2.dp): Modifier = drawWithCache {
    val step = spacing.toPx()
    val pts = ArrayList<Offset>()
    var y = step / 2
    while (y < size.height) {
        var x = step / 2
        while (x < size.width) {
            pts += Offset(x, y)
            x += step
        }
        y += step
    }
    val w = dot.toPx()
    onDrawBehind { drawPoints(pts, PointMode.Points, color, strokeWidth = w, cap = StrokeCap.Round) }
}

/** Seçili temanın zemini (tam ekran kapsayıcılar için). */
@Composable
fun Modifier.themedBackdrop(): Modifier {
    val t = LocalAppTheme.current
    val cs = MaterialTheme.colorScheme
    return this.background(cs.background).backdrop(t.backdrop, (if (t.backdrop == Backdrop.DOTS) cs.onSurface else cs.primary).copy(alpha = t.backdropAlpha))
}

// ---------------- Temel bileşenler ----------------

/** Yanıp sönen nokta (canlı yayın / çevrim içi ışığı). */
@Composable
fun LiveDot(active: Boolean, size: Dp = 10.dp, color: Color = MaterialTheme.colorScheme.primary) {
    val inf = rememberInfiniteTransition(label = "dot")
    val a by inf.animateFloat(1f, 0.3f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a")
    Box(Modifier.size(size).clip(CircleShape).background(if (active) color.copy(alpha = a) else MaterialTheme.colorScheme.outline))
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    val upper = LocalAppTheme.current.uppercaseTitles
    Text(
        if (upper) text.uppercase() else text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 6.dp, top = 10.dp, bottom = 2.dp),
    )
}

/** Büyük yuvarlak kart; tıklanabilirse kumandayla odaklanır (odak halkası). */
@Composable
fun ExpressiveCard(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
    onClick: (() -> Unit)? = null,
    padding: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val border = if (LocalAppTheme.current.cardBorder) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null
    val shape = MaterialTheme.shapes.large
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier.fillMaxWidth().focusRing(shape, 1.02f), shape = shape, color = color, border = border) {
            Column(Modifier.padding(padding), content = content)
        }
    } else {
        Surface(modifier = modifier.fillMaxWidth(), shape = shape, color = color, border = border) {
            Column(Modifier.padding(padding), content = content)
        }
    }
}

/** Yuvarlatılmış kare içinde ikon — "şekilli" ikon kabı. */
@Composable
fun IconBadge(
    icon: ImageVector,
    tint: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    bg: Color = MaterialTheme.colorScheme.primaryContainer,
    size: Dp = 48.dp,
) {
    Surface(shape = LocalAppTheme.current.badge, color = bg, contentColor = tint, modifier = Modifier.size(size)) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(size * 0.5f)) }
    }
}

@Composable
fun StatPill(icon: ImageVector, value: String, label: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
fun KeyValueRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.45f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(0.55f), textAlign = TextAlign.End)
    }
}

@Composable
fun CardDivider() = HorizontalDivider(Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

/** Noktalardan oluşan QR kod (Nothing havası, ama okunabilir). */
@Composable
fun DotQrCode(text: String, modifier: Modifier = Modifier) {
    val matrix = remember(text) { QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 0)) }
    val color = MaterialTheme.colorScheme.onSurface
    Canvas(modifier) {
        val n = matrix.width
        val cell = size.minDimension / n
        fun finder(x: Int, y: Int) = (x < 7 && y < 7) || (x >= n - 7 && y < 7) || (x < 7 && y >= n - 7)
        for (y in 0 until n) for (x in 0 until n) {
            if (!matrix.get(x, y)) continue
            if (finder(x, y)) {
                drawRoundRect(color, topLeft = Offset(x * cell, y * cell), size = Size(cell + 0.5f, cell + 0.5f), cornerRadius = CornerRadius(cell * 0.15f))
            } else {
                drawCircle(color, radius = cell * 0.48f, center = Offset(x * cell + cell / 2, y * cell + cell / 2))
            }
        }
    }
}

// ---------------- Ayar satırları ----------------

@Composable
fun SettingRow(
    icon: ImageVector?,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val content: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                IconBadge(icon, size = 40.dp)
                Spacer(Modifier.width(14.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                if (!subtitle.isNullOrEmpty()) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(10.dp))
                trailing()
            }
        }
    }
    if (onClick != null) {
        Surface(onClick = onClick, color = Color.Transparent, shape = MaterialTheme.shapes.medium, modifier = Modifier.focusRing(MaterialTheme.shapes.medium, 1.01f)) { content() }
    } else {
        content()
    }
}

/**
 * Özellik aç/kapa satırı (Akış'taki kalıp); açıkken altındaki ek ayarlar (extra) yaylı animasyonla açılır.
 * Satırın tamamı tek odak noktasıdır: kumandada OK ile açılıp kapanır.
 */
@Composable
fun FeatureRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    divider: Boolean = false,
    enabled: Boolean = true,
    extra: (@Composable ColumnScope.() -> Unit)? = null,
    onChange: (Boolean) -> Unit,
) {
    if (divider) CardDivider()
    SettingRow(icon, title, subtitle, onClick = if (enabled) ({ onChange(!checked) }) else null) {
        // Düğme odak almaz; satırın kendisi odaklanır (kumandada çift odak olmasın)
        Switch(checked, null, enabled = enabled)
    }
    if (extra != null) {
        AnimatedVisibility(
            visible = checked,
            enter = expandVertically(spring(dampingRatio = 0.8f, stiffness = 500f)) + fadeIn(),
            exit = shrinkVertically(spring(stiffness = 700f)) + fadeOut(),
        ) {
            Column(Modifier.fillMaxWidth().padding(start = 58.dp, bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = extra)
        }
    }
}

@Composable
fun Segmented(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, (value, label) ->
            SegmentedButton(
                selected = value == selected,
                onClick = { onSelect(value) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
            ) { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

/** Tema seçimi: her tema kendi renkleriyle küçük bir önizleme kartı; [columns] sütunda. */
@Composable
fun ThemeGrid(selected: String, darkMode: String, columns: Int = 3, onSelect: (String) -> Unit) {
    val ctx = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Themes.ALL.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { t ->
                    val cs = t.scheme(ctx, t.isDark(darkMode, systemDark))
                    val on = t.id == selected
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            onClick = { onSelect(t.id) },
                            shape = t.shapes.medium,
                            color = cs.background,
                            border = BorderStroke(
                                if (on) 3.dp else 1.dp,
                                if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            ),
                            modifier = Modifier.fillMaxWidth().height(84.dp).focusRing(t.shapes.medium, 1.06f),
                        ) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(14.dp).clip(t.badge).background(cs.primary))
                                    Spacer(Modifier.width(6.dp))
                                    Box(Modifier.height(6.dp).weight(1f).clip(t.shapes.extraSmall).background(cs.onSurface.copy(alpha = 0.7f)))
                                }
                                Box(Modifier.fillMaxWidth().height(20.dp).clip(t.shapes.small).background(cs.surfaceContainerHigh))
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Box(Modifier.height(12.dp).weight(1f).clip(t.shapes.extraSmall).background(cs.primaryContainer))
                                    Box(Modifier.height(12.dp).weight(1f).clip(t.shapes.extraSmall).background(cs.tertiaryContainer))
                                }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            androidx.compose.ui.res.stringResource(t.name),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

// ---------------- Durum ekranları ----------------

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

/** Boş / hata durumu: ikon, başlık, açıklama ve isteğe bağlı düğme. */
@Composable
fun MessageBox(
    icon: ImageVector,
    title: String,
    text: String? = null,
    action: String? = null,
    modifier: Modifier = Modifier,
    onAction: (() -> Unit)? = null,
) {
    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            IconBadge(icon, size = 72.dp)
            Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            if (text != null) Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            if (action != null && onAction != null) {
                FilledTonalButton(onClick = onAction, modifier = Modifier.focusRing(CircleShape)) { Text(action) }
            }
        }
    }
}

// ---------------- Biçimlendirme ----------------

fun formatBytes(b: Long): String = when {
    b < 0 -> "—"
    b >= 1L shl 30 -> String.format("%.2f GB", b / 1073741824.0)
    b >= 1L shl 20 -> String.format("%.1f MB", b / 1048576.0)
    b >= 1L shl 10 -> String.format("%d KB", b / 1024)
    else -> "$b B"
}

fun formatDuration(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600
    val m = (s / 60) % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s % 60) else String.format("%02d:%02d", m, s % 60)
}
