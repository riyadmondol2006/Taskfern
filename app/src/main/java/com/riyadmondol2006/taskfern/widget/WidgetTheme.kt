package com.riyadmondol2006.taskfern.widget

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.color.ColorProvider
import androidx.glance.color.ColorProviders
import androidx.glance.color.DynamicThemeColorProviders
import androidx.glance.material3.ColorProviders as materialColors
import androidx.glance.unit.ColorProvider
import com.riyadmondol2006.taskfern.TaskfernApp
import com.riyadmondol2006.taskfern.data.prefs.Accent
import com.riyadmondol2006.taskfern.data.prefs.Settings
import com.riyadmondol2006.taskfern.data.prefs.ThemeMode
import com.riyadmondol2006.taskfern.ui.theme.seed

/** The theme settings that affect widgets. */
data class WidgetLook(
    val mode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val accent: Accent = Accent.FERN,
) {
    companion object {
        fun from(settings: Settings) = WidgetLook(settings.themeMode, settings.dynamicColor, settings.accent)

        suspend fun load(context: Context): WidgetLook =
            from((context.applicationContext as TaskfernApp).container.settings.current())
    }
}

/** Colours with a fixed meaning (priority flags) in the current light/dark choice. */
data class WidgetAccents(val priorityHigh: ColorProvider, val priorityMedium: ColorProvider, val priorityLow: ColorProvider) {
    fun priority(priority: Int): ColorProvider? = when (priority) {
        3 -> priorityHigh
        2 -> priorityMedium
        1 -> priorityLow
        else -> null
    }
}

val LocalWidgetAccents = staticCompositionLocalOf { accentsFor(ThemeMode.SYSTEM) }

private val HighDay = Color(0xFFC0392B)
private val HighNight = Color(0xFFFFB4A9)
private val MediumDay = Color(0xFFB7771A)
private val MediumNight = Color(0xFFF2C063)
private val LowDay = Color(0xFF2F72A0)
private val LowNight = Color(0xFF98CBEF)

private fun pick(mode: ThemeMode, day: Color, night: Color): ColorProvider = when (mode) {
    ThemeMode.SYSTEM -> ColorProvider(day = day, night = night)
    ThemeMode.LIGHT -> ColorProvider(day)
    ThemeMode.DARK -> ColorProvider(night)
}

private fun accentsFor(mode: ThemeMode) = WidgetAccents(
    priorityHigh = pick(mode, HighDay, HighNight),
    priorityMedium = pick(mode, MediumDay, MediumNight),
    priorityLow = pick(mode, LowDay, LowNight),
)

/**
 * Material You colours on Android 12+ when dynamic colour is on, otherwise the app's accent palette; light, dark or
 * following the system as set in the app.
 */
fun widgetColors(context: Context, look: WidgetLook): ColorProviders {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && look.dynamicColor) {
        // Resource-backed colours follow wallpaper and night-mode changes without waking the app.
        if (look.mode == ThemeMode.SYSTEM) return DynamicThemeColorProviders
        return materialColors(dynamicScheme(context, dark = look.mode == ThemeMode.DARK))
    }
    val seed = look.accent.seed()
    return when (look.mode) {
        ThemeMode.SYSTEM -> materialColors(light = brandLightScheme(seed), dark = brandDarkScheme(seed))
        ThemeMode.LIGHT -> materialColors(brandLightScheme(seed))
        ThemeMode.DARK -> materialColors(brandDarkScheme(seed))
    }
}

@RequiresApi(Build.VERSION_CODES.S)
private fun dynamicScheme(context: Context, dark: Boolean): ColorScheme =
    if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

/** A tone of [seed] at the given HSL lightness, as the app theme builds its accent palettes. */
private fun tone(seed: Color, lightness: Float, saturationScale: Float = 1f): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(seed.toArgb(), hsl)
    hsl[1] = (hsl[1] * saturationScale).coerceIn(0f, 1f)
    hsl[2] = lightness
    return Color(ColorUtils.HSLToColor(hsl))
}

internal fun brandLightScheme(seed: Color): ColorScheme = lightColorScheme(
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
    outline = Color(0xFF717973),
    outlineVariant = Color(0xFFC1C9C2),
    error = Color(0xFFB3261E),
    errorContainer = Color(0xFFF9DEDC),
)

internal fun brandDarkScheme(seed: Color): ColorScheme = darkColorScheme(
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
    surface = tone(seed, 0.11f, 0.13f),
    onSurface = Color(0xFFE1E3DF),
    surfaceVariant = tone(seed, 0.20f, 0.10f),
    onSurfaceVariant = Color(0xFFC1C9C2),
    outline = Color(0xFF8B938C),
    outlineVariant = Color(0xFF414943),
    error = Color(0xFFF2B8B5),
    errorContainer = Color(0xFF8C1D18),
)

@Composable
fun TaskfernWidgetTheme(look: WidgetLook, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colors = remember(look) { widgetColors(context, look) }
    val accents = remember(look.mode) { accentsFor(look.mode) }
    GlanceTheme(colors = colors) {
        CompositionLocalProvider(LocalWidgetAccents provides accents, content = content)
    }
}
