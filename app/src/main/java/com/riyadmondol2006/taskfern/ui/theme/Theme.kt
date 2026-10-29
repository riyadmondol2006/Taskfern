package com.riyadmondol2006.taskfern.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowCompat
import com.riyadmondol2006.taskfern.data.prefs.Accent
import com.riyadmondol2006.taskfern.data.prefs.ThemeMode

/** Seed colours of the accent palettes offered when dynamic colour is off. */
fun Accent.seed(): Color = when (this) {
    Accent.FERN -> Color(0xFF2F6B4F)
    Accent.MOSS -> Color(0xFF5B6B2A)
    Accent.OCEAN -> Color(0xFF256A7C)
    Accent.DUSK -> Color(0xFF5A5891)
    Accent.CLAY -> Color(0xFF9A5A3A)
}

/** Colours that carry meaning in the app and stay recognisable in every theme. */
data class TaskfernColors(
    val priorityHigh: Color,
    val priorityMedium: Color,
    val priorityLow: Color,
    val overdue: Color,
    val success: Color,
    /** Palette for user-chosen list colours (index stored in the database). */
    val listColors: List<Color>,
)

val LocalTaskfernColors = staticCompositionLocalOf {
    TaskfernColors(
        Color(0xFFC0392B), Color(0xFFD08A1E), Color(0xFF3A7CA5), Color(0xFFB3261E), Color(0xFF2F6B4F), emptyList(),
    )
}

private val listPaletteLight = listOf(
    Color(0xFF2F6B4F), Color(0xFF256A7C), Color(0xFF5A5891), Color(0xFF9A5A3A), Color(0xFFB0475E),
    Color(0xFF8A6D1F), Color(0xFF4E6A2C), Color(0xFF4A5C6A),
)
private val listPaletteDark = listOf(
    Color(0xFF8FD3AE), Color(0xFF86CFE0), Color(0xFFC1BFFA), Color(0xFFF0B796), Color(0xFFF4A9B8),
    Color(0xFFE6C66E), Color(0xFFB7D47E), Color(0xFFB2C5D3),
)

/** A tone of [seed] at the given HSL lightness (0..1), keeping hue and capping saturation. Simple but predictable. */
private fun tone(seed: Color, lightness: Float, saturationScale: Float = 1f): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(seed.toArgb(), hsl)
    hsl[1] = (hsl[1] * saturationScale).coerceIn(0f, 1f)
    hsl[2] = lightness
    return Color(ColorUtils.HSLToColor(hsl))
}

private fun lightScheme(seed: Color): ColorScheme = lightColorScheme(
    primary = tone(seed, 0.30f),
    onPrimary = Color.White,
    primaryContainer = tone(seed, 0.87f, 0.55f),
    onPrimaryContainer = tone(seed, 0.12f),
    secondary = tone(seed, 0.35f, 0.25f),
    onSecondary = Color.White,
    secondaryContainer = tone(seed, 0.89f, 0.30f),
    onSecondaryContainer = tone(seed, 0.13f, 0.30f),
    tertiary = Color(0xFF7A5A2E),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF8DDB5),
    onTertiaryContainer = Color(0xFF2A1800),
    background = tone(seed, 0.975f, 0.20f),
    onBackground = Color(0xFF191C1A),
    surface = tone(seed, 0.975f, 0.20f),
    onSurface = Color(0xFF191C1A),
    surfaceVariant = tone(seed, 0.90f, 0.15f),
    onSurfaceVariant = Color(0xFF414943),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = tone(seed, 0.955f, 0.18f),
    surfaceContainer = tone(seed, 0.94f, 0.18f),
    surfaceContainerHigh = tone(seed, 0.925f, 0.16f),
    surfaceContainerHighest = tone(seed, 0.905f, 0.15f),
    outline = Color(0xFF717973),
    outlineVariant = Color(0xFFC1C9C2),
    error = Color(0xFFB3261E),
    errorContainer = Color(0xFFF9DEDC),
)

private fun darkScheme(seed: Color, amoled: Boolean): ColorScheme {
    val base = darkColorScheme(
        primary = tone(seed, 0.72f, 0.55f),
        onPrimary = tone(seed, 0.13f),
        primaryContainer = tone(seed, 0.24f),
        onPrimaryContainer = tone(seed, 0.88f, 0.55f),
        secondary = tone(seed, 0.75f, 0.25f),
        onSecondary = tone(seed, 0.15f, 0.30f),
        secondaryContainer = tone(seed, 0.25f, 0.20f),
        onSecondaryContainer = tone(seed, 0.88f, 0.30f),
        tertiary = Color(0xFFE9C08B),
        onTertiary = Color(0xFF442B05),
        tertiaryContainer = Color(0xFF5E4119),
        onTertiaryContainer = Color(0xFFFFDDB4),
        background = tone(seed, 0.065f, 0.15f),
        onBackground = Color(0xFFE1E3DF),
        surface = tone(seed, 0.065f, 0.15f),
        onSurface = Color(0xFFE1E3DF),
        surfaceVariant = tone(seed, 0.20f, 0.10f),
        onSurfaceVariant = Color(0xFFC1C9C2),
        surfaceContainerLowest = tone(seed, 0.045f, 0.15f),
        surfaceContainerLow = tone(seed, 0.09f, 0.14f),
        surfaceContainer = tone(seed, 0.11f, 0.13f),
        surfaceContainerHigh = tone(seed, 0.14f, 0.12f),
        surfaceContainerHighest = tone(seed, 0.17f, 0.11f),
        outline = Color(0xFF8B938C),
        outlineVariant = Color(0xFF414943),
        error = Color(0xFFF2B8B5),
        errorContainer = Color(0xFF8C1D18),
    )
    return if (amoled) base.amoled() else base
}

/** True black backgrounds for OLED screens; containers stay just above black so cards remain visible. */
private fun ColorScheme.amoled(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0B0D0C),
    surfaceContainer = Color(0xFF111413),
    surfaceContainerHigh = Color(0xFF171A19),
    surfaceContainerHighest = Color(0xFF1D201F),
)

val TaskfernTypography = Typography().let { t ->
    t.copy(
        displaySmall = t.displaySmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
        headlineLarge = t.headlineLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.25).sp),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

val TaskfernShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

/** Small text used for metadata (due labels, counts). */
val MetaTextStyle: TextStyle @Composable get() = MaterialTheme.typography.labelMedium

@Composable
fun TaskfernTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    amoledBlack: Boolean = false,
    accent: Accent = Accent.FERN,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val useDynamic = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val scheme = when {
        useDynamic && dark -> dynamicDarkColorScheme(context).let { if (amoledBlack) it.amoled() else it }
        useDynamic -> dynamicLightColorScheme(context)
        dark -> darkScheme(accent.seed(), amoledBlack)
        else -> lightScheme(accent.seed())
    }
    val semantic = if (dark) {
        TaskfernColors(Color(0xFFFFB4A9), Color(0xFFF2C063), Color(0xFF98CBEF), Color(0xFFFFB4AB), Color(0xFF8FD3AE), listPaletteDark)
    } else {
        TaskfernColors(Color(0xFFC0392B), Color(0xFFB7771A), Color(0xFF2F72A0), Color(0xFFB3261E), Color(0xFF2F6B4F), listPaletteLight)
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    androidx.compose.runtime.CompositionLocalProvider(LocalTaskfernColors provides semantic) {
        MaterialTheme(colorScheme = scheme, typography = TaskfernTypography, shapes = TaskfernShapes, content = content)
    }
}
