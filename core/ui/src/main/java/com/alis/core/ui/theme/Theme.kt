package com.alis.core.ui.theme

import android.content.Context
import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alis.core.ui.R

/** Arka plan deseni. */
enum class Backdrop { NONE, DOTS, GRID, SCANLINES }

/**
 * Bir tema: renkler, şekiller, yazı tipi ve küçük stil ayrıntıları.
 * Yeni tema eklemek için [Themes.ALL] listesine bir kayıt ve strings.xml'e adını (theme_<id>) eklemek yeter.
 */
data class AppTheme(
    val id: String,
    @param:StringRes val name: Int,
    /** null ise tema yalnızca koyu. */
    val light: ColorScheme?,
    val dark: ColorScheme,
    /** Android 12+ duvar kâğıdı renkleri (Material You). */
    val dynamic: Boolean = false,
    val shapes: Shapes = ExpressiveShapes,
    /** İkon kabının şekli. */
    val badge: Shape = RoundedCornerShape(percent = 34),
    val font: FontFamily = FontFamily.Default,
    val mono: Boolean = false,
    val uppercaseTitles: Boolean = false,
    val backdrop: Backdrop = Backdrop.DOTS,
    val backdropAlpha: Float = 0.05f,
    /** Kartlarda ince çerçeve. */
    val cardBorder: Boolean = false,
    /** Alt menü zeminle aynı renkte. */
    val flatNav: Boolean = false,
) {
    val darkOnly: Boolean get() = light == null

    fun isDark(darkMode: String, systemDark: Boolean): Boolean = darkOnly || when (darkMode) {
        "light" -> false
        "dark" -> true
        else -> systemDark
    }

    /** Material You için duvar kâğıdı renkleri; diğerlerinde sabit renkler. */
    fun scheme(ctx: Context, dark: Boolean): ColorScheme = when {
        dynamic && Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark || light == null -> this.dark
        else -> light
    }
}

val LocalAppTheme = staticCompositionLocalOf { Themes.ALL.first() }

val NothingRed = Color(0xFFD71921)

// ---------------- Şekiller ----------------

private val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(30.dp),
    extraLarge = RoundedCornerShape(40.dp),
)

private val OneUiShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

private val CupertinoShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(22.dp),
)

private val CutShapes = Shapes(
    extraSmall = CutCornerShape(3.dp),
    small = CutCornerShape(5.dp),
    medium = CutCornerShape(8.dp),
    large = CutCornerShape(topStart = 16.dp, bottomEnd = 16.dp),
    extraLarge = CutCornerShape(topStart = 24.dp, bottomEnd = 24.dp),
)

private val SquareShapes = Shapes(
    extraSmall = RoundedCornerShape(1.dp),
    small = RoundedCornerShape(2.dp),
    medium = RoundedCornerShape(2.dp),
    large = RoundedCornerShape(3.dp),
    extraLarge = RoundedCornerShape(4.dp),
)

private val SoftShapes = Shapes(
    extraSmall = RoundedCornerShape(14.dp),
    small = RoundedCornerShape(20.dp),
    medium = RoundedCornerShape(28.dp),
    large = RoundedCornerShape(36.dp),
    extraLarge = RoundedCornerShape(48.dp),
)

private val PaperShapes = Shapes(
    extraSmall = RoundedCornerShape(3.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(6.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(10.dp),
)

// ---------------- Renkler ----------------

/** Kısa renk tanımı: kartlar için 5 kademe (en düşük → en yüksek) zorunlu. */
private fun palette(
    dark: Boolean,
    primary: Long, onPrimary: Long, primaryContainer: Long, onPrimaryContainer: Long,
    secondaryContainer: Long, onSecondaryContainer: Long,
    tertiaryContainer: Long, onTertiaryContainer: Long,
    background: Long, onBackground: Long, onSurfaceVariant: Long,
    containers: List<Long>, outline: Long, outlineVariant: Long,
    secondary: Long = onSecondaryContainer, tertiary: Long = onTertiaryContainer,
    error: Long = if (dark) 0xFFFF6B5F else 0xFFBA1A1A,
): ColorScheme {
    fun c(v: Long) = Color(v)
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = c(primary), onPrimary = c(onPrimary),
        primaryContainer = c(primaryContainer), onPrimaryContainer = c(onPrimaryContainer),
        secondary = c(secondary), onSecondary = c(background),
        secondaryContainer = c(secondaryContainer), onSecondaryContainer = c(onSecondaryContainer),
        tertiary = c(tertiary), onTertiary = c(background),
        tertiaryContainer = c(tertiaryContainer), onTertiaryContainer = c(onTertiaryContainer),
        background = c(background), onBackground = c(onBackground),
        surface = c(background), onSurface = c(onBackground),
        surfaceVariant = c(containers[3]), onSurfaceVariant = c(onSurfaceVariant),
        surfaceContainerLowest = c(containers[0]), surfaceContainerLow = c(containers[1]),
        surfaceContainer = c(containers[2]), surfaceContainerHigh = c(containers[3]),
        surfaceContainerHighest = c(containers[4]),
        outline = c(outline), outlineVariant = c(outlineVariant),
        error = c(error),
        inverseSurface = c(onBackground), inverseOnSurface = c(background), inversePrimary = c(primaryContainer),
    )
}

private val NothingDark = palette(
    true, 0xFFD71921, 0xFFFFFFFF, 0xFF2B0B0C, 0xFFFFDAD6, 0xFF262626, 0xFFFFFFFF, 0xFF1F1F1F, 0xFFFFFFFF,
    0xFF000000, 0xFFFFFFFF, 0xFFA3A3A3, listOf(0xFF000000, 0xFF0B0B0B, 0xFF121212, 0xFF1A1A1A, 0xFF232323), 0xFF3C3C3C, 0xFF262626,
    secondary = 0xFFEDEDED, tertiary = 0xFFBDBDBD,
)
private val NothingLight = palette(
    false, 0xFFD71921, 0xFFFFFFFF, 0xFFFFDAD6, 0xFF410002, 0xFFE6E6E6, 0xFF000000, 0xFFE6E6E6, 0xFF000000,
    0xFFF2F2F2, 0xFF000000, 0xFF5C5C5C, listOf(0xFFFFFFFF, 0xFFFAFAFA, 0xFFFFFFFF, 0xFFEDEDED, 0xFFE5E5E5), 0xFFBDBDBD, 0xFFDDDDDD,
    secondary = 0xFF1A1A1A, tertiary = 0xFF555555,
)

// Material You kapalı / Android 11 ve altı için yedek renkler
private val FallbackDark = darkColorScheme(primary = Color(0xFFB4C5FF), secondary = Color(0xFFC1C6DD), tertiary = Color(0xFFE2BBDB))
private val FallbackLight = lightColorScheme(primary = Color(0xFF3E5BA9), secondary = Color(0xFF585E71), tertiary = Color(0xFF735470))

private val OneUiLight = palette(
    false, 0xFF3E91FF, 0xFFFFFFFF, 0xFFD9E8FF, 0xFF002A5C, 0xFFE7EEF8, 0xFF1B2A3D, 0xFFE3F4EC, 0xFF0D3324,
    0xFFF4F4F6, 0xFF111111, 0xFF6E6E73, listOf(0xFFFFFFFF, 0xFFFAFAFC, 0xFFFFFFFF, 0xFFEEEEF2, 0xFFE4E4EA), 0xFFC7C7CC, 0xFFE2E2E7,
)
private val OneUiDark = palette(
    true, 0xFF5EA3FF, 0xFF001B3D, 0xFF0F2E57, 0xFFD5E5FF, 0xFF1F2733, 0xFFD6E2F2, 0xFF17302A, 0xFFBDEBD6,
    0xFF010101, 0xFFF2F2F2, 0xFF9A9AA0, listOf(0xFF000000, 0xFF0E0E0E, 0xFF171717, 0xFF222224, 0xFF2C2C2F), 0xFF3A3A3D, 0xFF262628,
)

private val CyberpunkDark = palette(
    true, 0xFFFCEE0A, 0xFF0A0A12, 0xFF2A2700, 0xFFFCEE0A, 0xFF002A30, 0xFF7DF9FF, 0xFF3A0A1C, 0xFFFF8FB0,
    0xFF0A0A12, 0xFFE8E8F0, 0xFF8A8AA8, listOf(0xFF07070D, 0xFF0E0E18, 0xFF12121F, 0xFF1A1A2E, 0xFF22223A), 0xFFFF2A6D, 0xFF2A2A48,
    secondary = 0xFF00F0FF, tertiary = 0xFFFF2A6D,
)

private val CupertinoLight = palette(
    false, 0xFF007AFF, 0xFFFFFFFF, 0xFFD6E8FF, 0xFF00346E, 0xFFE5E5EA, 0xFF1C1C1E, 0xFFDFF5E3, 0xFF0C3418,
    0xFFF2F2F7, 0xFF000000, 0xFF6C6C70, listOf(0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFE5E5EA, 0xFFD1D1D6), 0xFFC6C6C8, 0xFFE5E5EA,
    error = 0xFFFF3B30,
)
private val CupertinoDark = palette(
    true, 0xFF0A84FF, 0xFFFFFFFF, 0xFF0A2E57, 0xFFCFE3FF, 0xFF2C2C2E, 0xFFF2F2F7, 0xFF16331F, 0xFFC4F2CF,
    0xFF000000, 0xFFFFFFFF, 0xFF98989F, listOf(0xFF000000, 0xFF101012, 0xFF1C1C1E, 0xFF2C2C2E, 0xFF3A3A3C), 0xFF48484A, 0xFF2C2C2E,
    error = 0xFFFF453A,
)

private val TerminalDark = palette(
    true, 0xFF33FF66, 0xFF001A08, 0xFF0F3A1A, 0xFF9CFFB4, 0xFF13261A, 0xFF7CFF9C, 0xFF2E2200, 0xFFFFD27A,
    0xFF050805, 0xFFB8FFC8, 0xFF5FAF75, listOf(0xFF030503, 0xFF080D08, 0xFF0B120B, 0xFF102010, 0xFF152A16), 0xFF1F5A2E, 0xFF143A1E,
    tertiary = 0xFFFFB000, error = 0xFFFF5555,
)

private val DraculaDark = palette(
    true, 0xFFBD93F9, 0xFF282A36, 0xFF4B3A6E, 0xFFE9DCFF, 0xFF5A2E4C, 0xFFFFD0EA, 0xFF1F4A2E, 0xFFB6FFC9,
    0xFF282A36, 0xFFF8F8F2, 0xFFA3A8C3, listOf(0xFF21222C, 0xFF2B2D3A, 0xFF303241, 0xFF383A4A, 0xFF44475A), 0xFF6272A4, 0xFF44475A,
    secondary = 0xFFFF79C6, tertiary = 0xFF50FA7B, error = 0xFFFF5555,
)

private val OceanLight = palette(
    false, 0xFF0077B6, 0xFFFFFFFF, 0xFFCDEBFF, 0xFF002B45, 0xFFDDEEF7, 0xFF0F2A3A, 0xFFD3F6EE, 0xFF00382D,
    0xFFEEF6FB, 0xFF06202F, 0xFF4E6575, listOf(0xFFFFFFFF, 0xFFF7FBFD, 0xFFFFFFFF, 0xFFE3EFF7, 0xFFD6E7F2), 0xFFA8C3D4, 0xFFD3E3EE,
)
private val OceanDark = palette(
    true, 0xFF4CC9F0, 0xFF00121F, 0xFF003A57, 0xFFBDEBFF, 0xFF0D2B45, 0xFFC5DFF2, 0xFF00382D, 0xFF80FFDB,
    0xFF000814, 0xFFE3F2FD, 0xFF8AA9C2, listOf(0xFF00040A, 0xFF000F21, 0xFF001D3D, 0xFF0A2A4D, 0xFF12355E), 0xFF24507A, 0xFF12355E,
)

private val SunsetLight = palette(
    false, 0xFFE8590C, 0xFFFFFFFF, 0xFFFFDBC8, 0xFF3A1400, 0xFFFFD8E4, 0xFF3E0A20, 0xFFEBDDFF, 0xFF26124A,
    0xFFFFF4EC, 0xFF2A1408, 0xFF7A5A48, listOf(0xFFFFFFFF, 0xFFFFFAF6, 0xFFFFFFFF, 0xFFFFE9DC, 0xFFFFDCCB), 0xFFE0B9A2, 0xFFF3DACB,
)
private val SunsetDark = palette(
    true, 0xFFFF8C42, 0xFF2A1000, 0xFF5A2A12, 0xFFFFD3B5, 0xFF4E1A33, 0xFFFFC2D4, 0xFF3A2558, 0xFFE3CCFF,
    0xFF1A0E1F, 0xFFFFEDE3, 0xFFC2A2AE, listOf(0xFF120914, 0xFF1F1125, 0xFF24142B, 0xFF2E1A36, 0xFF3A2143), 0xFF6A4258, 0xFF3A2143,
    secondary = 0xFFFF5E8A, tertiary = 0xFFB57BFF,
)

private val SakuraLight = palette(
    false, 0xFFE8668B, 0xFFFFFFFF, 0xFFFFD9E3, 0xFF3E0019, 0xFFF3E5FF, 0xFF2D1446, 0xFFDFF5E8, 0xFF0F2E1D,
    0xFFFFF5F7, 0xFF2E1A20, 0xFF7E6068, listOf(0xFFFFFFFF, 0xFFFFFAFB, 0xFFFFFFFF, 0xFFFFE8EE, 0xFFFFDCE5), 0xFFE9BCC8, 0xFFF7DCE3,
)
private val SakuraDark = palette(
    true, 0xFFFFB1C8, 0xFF4A0E26, 0xFF6A2440, 0xFFFFD9E3, 0xFF3D2A4D, 0xFFEBD9FF, 0xFF1F3A2B, 0xFFC4EED6,
    0xFF1E1418, 0xFFFBE9EE, 0xFFC4A6AF, listOf(0xFF160E11, 0xFF23181D, 0xFF2A1C22, 0xFF34242B, 0xFF3F2C34), 0xFF6E4C57, 0xFF3F2C34,
)

private val ForestLight = palette(
    false, 0xFF2E7D32, 0xFFFFFFFF, 0xFFC8E6C9, 0xFF0B2E0E, 0xFFEADFCB, 0xFF2E2414, 0xFFD7E8E0, 0xFF0F2C22,
    0xFFF3F6EF, 0xFF16200F, 0xFF5B6852, listOf(0xFFFFFFFF, 0xFFF8FAF5, 0xFFFFFFFF, 0xFFE9EFE2, 0xFFDEE7D4), 0xFFB3C2A6, 0xFFDCE4D2,
)
private val ForestDark = palette(
    true, 0xFF81C784, 0xFF0B2E0E, 0xFF1E4D23, 0xFFC8E6C9, 0xFF3D3222, 0xFFEADFCB, 0xFF1C3A30, 0xFFBFE6D4,
    0xFF0E140F, 0xFFE3EDDD, 0xFF9AAB90, listOf(0xFF090D0A, 0xFF121A13, 0xFF162018, 0xFF1D2A20, 0xFF253428), 0xFF3F5A42, 0xFF253428,
)

private val PaperLight = palette(
    false, 0xFF1A1A1A, 0xFFF5F1E8, 0xFFE0D9C8, 0xFF1A1A1A, 0xFFE6DFCF, 0xFF1A1A1A, 0xFFEAE3D3, 0xFF1A1A1A,
    0xFFF5F1E8, 0xFF1A1A1A, 0xFF5A554C, listOf(0xFFFBF8F1, 0xFFF8F4EC, 0xFFFBF8F1, 0xFFEFE9DC, 0xFFE6DFCF), 0xFFB8AF9C, 0xFFD6CEBC,
    error = 0xFF9E2A1E,
)
private val PaperDark = palette(
    true, 0xFFEDE6D6, 0xFF1C1B19, 0xFF3A3731, 0xFFEDE6D6, 0xFF33302B, 0xFFEDE6D6, 0xFF35322C, 0xFFEDE6D6,
    0xFF1C1B19, 0xFFEDE6D6, 0xFFA8A090, listOf(0xFF161513, 0xFF201F1C, 0xFF262421, 0xFF2E2C28, 0xFF373430), 0xFF5A554C, 0xFF373430,
)

/** Tüm temalar; seçim ekranında bu sırayla görünür. */
object Themes {
    val ALL = listOf(
        AppTheme("material", R.string.theme_material, FallbackLight, FallbackDark, dynamic = true),
        AppTheme(
            "nothing", R.string.theme_nothing, NothingLight, NothingDark,
            mono = true, uppercaseTitles = true, backdropAlpha = 0.13f, cardBorder = true, flatNav = true,
        ),
        AppTheme(
            "samsung", R.string.theme_samsung, OneUiLight, OneUiDark,
            shapes = OneUiShapes, badge = RoundedCornerShape(percent = 30), backdrop = Backdrop.NONE, flatNav = true,
        ),
        AppTheme(
            "cyberpunk", R.string.theme_cyberpunk, null, CyberpunkDark,
            shapes = CutShapes, badge = CutCornerShape(30), mono = true, uppercaseTitles = true,
            backdrop = Backdrop.GRID, backdropAlpha = 0.10f, cardBorder = true, flatNav = true,
        ),
        AppTheme(
            "ios", R.string.theme_ios, CupertinoLight, CupertinoDark,
            shapes = CupertinoShapes, badge = RoundedCornerShape(percent = 24), backdrop = Backdrop.NONE,
        ),
        AppTheme(
            "terminal", R.string.theme_terminal, null, TerminalDark,
            shapes = SquareShapes, badge = RoundedCornerShape(2.dp), mono = true, uppercaseTitles = true,
            backdrop = Backdrop.SCANLINES, backdropAlpha = 0.07f, cardBorder = true, flatNav = true,
        ),
        AppTheme("dracula", R.string.theme_dracula, null, DraculaDark, backdropAlpha = 0.06f),
        AppTheme("ocean", R.string.theme_ocean, OceanLight, OceanDark, backdropAlpha = 0.06f),
        AppTheme("sunset", R.string.theme_sunset, SunsetLight, SunsetDark, backdropAlpha = 0.06f),
        AppTheme("sakura", R.string.theme_sakura, SakuraLight, SakuraDark, shapes = SoftShapes, badge = RoundedCornerShape(percent = 50), backdropAlpha = 0.06f),
        AppTheme("forest", R.string.theme_forest, ForestLight, ForestDark, backdrop = Backdrop.NONE),
        AppTheme(
            "paper", R.string.theme_paper, PaperLight, PaperDark,
            shapes = PaperShapes, badge = RoundedCornerShape(4.dp), font = FontFamily.Serif,
            backdrop = Backdrop.NONE, cardBorder = true, flatNav = true,
        ),
    )

    fun byId(id: String): AppTheme = ALL.firstOrNull { it.id == id } ?: ALL.first()
}

private fun typography(t: AppTheme): Typography {
    val base = Typography()
    if (!t.mono) {
        fun TextStyle.f(w: FontWeight? = null, ls: Float? = null) =
            copy(fontFamily = t.font, fontWeight = w ?: fontWeight, letterSpacing = ls?.sp ?: letterSpacing)
        return base.copy(
            displayLarge = base.displayLarge.f(),
            displayMedium = base.displayMedium.f(),
            displaySmall = base.displaySmall.f(FontWeight.Bold),
            headlineLarge = base.headlineLarge.f(FontWeight.Bold, -0.5f),
            headlineMedium = base.headlineMedium.f(FontWeight.SemiBold),
            headlineSmall = base.headlineSmall.f(),
            titleLarge = base.titleLarge.f(FontWeight.SemiBold),
            titleMedium = base.titleMedium.f(),
            titleSmall = base.titleSmall.f(),
            bodyLarge = base.bodyLarge.f(),
            bodyMedium = base.bodyMedium.f(),
            bodySmall = base.bodySmall.f(),
            labelLarge = base.labelLarge.f(FontWeight.SemiBold),
            labelMedium = base.labelMedium.f(),
            labelSmall = base.labelSmall.f(),
        )
    }
    val mono = FontFamily.Monospace
    fun TextStyle.m(w: FontWeight? = null, ls: Float? = null) =
        copy(fontFamily = mono, fontWeight = w ?: fontWeight, letterSpacing = ls?.sp ?: letterSpacing)
    return base.copy(
        displayLarge = base.displayLarge.m(FontWeight.Bold),
        displayMedium = base.displayMedium.m(FontWeight.Bold),
        displaySmall = base.displaySmall.m(FontWeight.Bold),
        headlineLarge = base.headlineLarge.m(FontWeight.Bold, 1f),
        headlineMedium = base.headlineMedium.m(FontWeight.Bold, 0.5f),
        headlineSmall = base.headlineSmall.m(FontWeight.Medium),
        titleLarge = base.titleLarge.m(FontWeight.Medium),
        titleMedium = base.titleMedium.m(FontWeight.Medium),
        titleSmall = base.titleSmall.m(),
        labelLarge = base.labelLarge.m(FontWeight.Medium, 1.2f),
        labelMedium = base.labelMedium.m(ls = 1.2f),
        labelSmall = base.labelSmall.m(ls = 1.2f),
    )
}

@Composable
fun AlisTheme(theme: AppTheme, dark: Boolean, content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    CompositionLocalProvider(LocalAppTheme provides theme) {
        MaterialTheme(
            colorScheme = theme.scheme(ctx, dark),
            typography = typography(theme),
            shapes = theme.shapes,
            content = content,
        )
    }
}
