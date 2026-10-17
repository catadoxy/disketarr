package io.github.catadoxy.floppy.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Material 3 palette generated from seed `#6545A0` with the Material Theme
 * Builder, so every role is a standard M3 tonal value rather than a hand-picked
 * colour.
 *
 * Two structural points are easy to get wrong:
 *
 *  - the app background is `#EBEBEB`, not white, and `surface` deliberately
 *    equals `background` - rows and cards sit directly on it with no card fill;
 *  - app bars use `surfaceContainer` (`#DEDBE3`), one step darker than the
 *    background, which is what gives the bottom bar a visible edge.
 */
object PurplePalette {

    // ---- Light -------------------------------------------------------------
    val LightPrimary = Color(0xFF6545A0)
    val LightOnPrimary = Color(0xFFFFFFFF)
    val LightPrimaryContainer = Color(0xFFB69DF8)
    val LightOnPrimaryContainer = Color(0xFF381E72)
    val LightSecondary = Color(0xFF00796A)
    val LightOnSecondary = Color(0xFFFFFFFF)
    val LightSecondaryContainer = Color(0xFFB3E6DE)
    val LightOnSecondaryContainer = Color(0xFF092A26)
    val LightTertiary = Color(0xFF7E525D)
    val LightOnTertiary = Color(0xFFFFFFFF)
    val LightTertiaryContainer = Color(0xFFFFD9E2)
    val LightOnTertiaryContainer = Color(0xFF32101B)
    val LightError = Color(0xFFBA1B1B)
    val LightErrorContainer = Color(0xFFFFDAD4)
    val LightOnError = Color(0xFFFFFFFF)
    val LightOnErrorContainer = Color(0xFF410001)
    val LightBackground = Color(0xFFEBEBEB)
    val LightOnBackground = Color(0xFF19161E)
    val LightSurfaceVariant = Color(0xFFE7E0EB)
    val LightOnSurfaceVariant = Color(0xFF49454E)
    val LightOutline = Color(0xFF7A757F)
    val LightOutlineVariant = Color(0xFFCBC4CF)
    val LightInverseOnSurface = Color(0xFFF5EFF4)
    val LightInverseSurface = Color(0xFF323033)
    val LightInversePrimary = Color(0xFFD5BBFF)
    val LightSurfaceTint = Color(0xFF6C4CA8)
    val LightSurfaceDim = Color(0xFFDED8E0)
    val LightSurfaceBright = Color(0xFFFEF7FF)
    val LightSurfaceContainerLowest = Color(0xFFFFFFFF)
    /** Buttons and cards. */
    val LightSurfaceContainerLow = Color(0xFFE3E0E6)
    /** App bars, including the bottom navigation bar. */
    val LightSurfaceContainer = Color(0xFFDEDBE3)
    /** Dialogs. */
    val LightSurfaceContainerHigh = Color(0xFFDCD9E3)

    // ---- Dark --------------------------------------------------------------
    val DarkPrimary = Color(0xFF927BBC)
    val DarkOnPrimary = Color(0xFF3D1976)
    val DarkPrimaryContainer = Color(0xFF54348E)
    val DarkOnPrimaryContainer = Color(0xFFECDCFF)
    val DarkSecondary = Color(0xFF009688)
    val DarkOnSecondary = Color(0xFF003730)
    val DarkSecondaryContainer = Color(0xFF005047)
    val DarkOnSecondaryContainer = Color(0xFF7BEFE0)
    val DarkTertiary = Color(0xFFF1B8C5)
    val DarkOnTertiary = Color(0xFF4B2530)
    val DarkTertiaryContainer = Color(0xFF643B46)
    val DarkOnTertiaryContainer = Color(0xFFFFD9E2)
    val DarkError = Color(0xFFFFB4A9)
    val DarkErrorContainer = Color(0xFF930006)
    val DarkOnError = Color(0xFF680003)
    val DarkOnErrorContainer = Color(0xFFFFDAD4)
    val DarkBackground = Color(0xFF19161E)
    val DarkOnBackground = Color(0xFFE6E1E5)
    val DarkSurfaceVariant = Color(0xFF49454E)
    val DarkOnSurfaceVariant = Color(0xFFCBC4CF)
    val DarkOutline = Color(0xFF948E99)
    val DarkOutlineVariant = Color(0xFF49454E)
    val DarkInverseOnSurface = Color(0xFF19161E)
    val DarkInverseSurface = Color(0xFFE6E1E5)
    val DarkInversePrimary = Color(0xFF6C4CA7)
    val DarkSurfaceTint = Color(0xFFD3BBFF)
    val DarkSurfaceDim = Color(0xFF151218)
    val DarkSurfaceBright = Color(0xFF3B383F)
    val DarkSurfaceContainerLowest = Color(0xFF0F0D13)
    val DarkSurfaceContainerLow = Color(0xFF1F1B26)
    val DarkSurfaceContainer = Color(0xFF221E2B)
    val DarkSurfaceContainerHigh = Color(0xFF25202E)
}

internal val PurpleLightScheme = lightColorScheme(
    primary = PurplePalette.LightPrimary,
    onPrimary = PurplePalette.LightOnPrimary,
    primaryContainer = PurplePalette.LightPrimaryContainer,
    onPrimaryContainer = PurplePalette.LightOnPrimaryContainer,
    secondary = PurplePalette.LightSecondary,
    onSecondary = PurplePalette.LightOnSecondary,
    secondaryContainer = PurplePalette.LightSecondaryContainer,
    onSecondaryContainer = PurplePalette.LightOnSecondaryContainer,
    tertiary = PurplePalette.LightTertiary,
    onTertiary = PurplePalette.LightOnTertiary,
    tertiaryContainer = PurplePalette.LightTertiaryContainer,
    onTertiaryContainer = PurplePalette.LightOnTertiaryContainer,
    error = PurplePalette.LightError,
    errorContainer = PurplePalette.LightErrorContainer,
    onError = PurplePalette.LightOnError,
    onErrorContainer = PurplePalette.LightOnErrorContainer,
    background = PurplePalette.LightBackground,
    onBackground = PurplePalette.LightOnBackground,
    surface = PurplePalette.LightBackground,
    onSurface = PurplePalette.LightOnBackground,
    surfaceVariant = PurplePalette.LightSurfaceVariant,
    onSurfaceVariant = PurplePalette.LightOnSurfaceVariant,
    outline = PurplePalette.LightOutline,
    outlineVariant = PurplePalette.LightOutlineVariant,
    inverseOnSurface = PurplePalette.LightInverseOnSurface,
    inverseSurface = PurplePalette.LightInverseSurface,
    inversePrimary = PurplePalette.LightInversePrimary,
    surfaceTint = PurplePalette.LightSurfaceTint,
    surfaceDim = PurplePalette.LightSurfaceDim,
    surfaceBright = PurplePalette.LightSurfaceBright,
    surfaceContainerLowest = PurplePalette.LightSurfaceContainerLowest,
    surfaceContainerLow = PurplePalette.LightSurfaceContainerLow,
    surfaceContainer = PurplePalette.LightSurfaceContainer,
    surfaceContainerHigh = PurplePalette.LightSurfaceContainerHigh,
    surfaceContainerHighest = PurplePalette.LightSurfaceVariant,
    scrim = Color(0xFF000000),
)

internal val PurpleDarkScheme = darkColorScheme(
    primary = PurplePalette.DarkPrimary,
    onPrimary = PurplePalette.DarkOnPrimary,
    primaryContainer = PurplePalette.DarkPrimaryContainer,
    onPrimaryContainer = PurplePalette.DarkOnPrimaryContainer,
    secondary = PurplePalette.DarkSecondary,
    onSecondary = PurplePalette.DarkOnSecondary,
    secondaryContainer = PurplePalette.DarkSecondaryContainer,
    onSecondaryContainer = PurplePalette.DarkOnSecondaryContainer,
    tertiary = PurplePalette.DarkTertiary,
    onTertiary = PurplePalette.DarkOnTertiary,
    tertiaryContainer = PurplePalette.DarkTertiaryContainer,
    onTertiaryContainer = PurplePalette.DarkOnTertiaryContainer,
    error = PurplePalette.DarkError,
    errorContainer = PurplePalette.DarkErrorContainer,
    onError = PurplePalette.DarkOnError,
    onErrorContainer = PurplePalette.DarkOnErrorContainer,
    background = PurplePalette.DarkBackground,
    onBackground = PurplePalette.DarkOnBackground,
    surface = PurplePalette.DarkBackground,
    onSurface = PurplePalette.DarkOnBackground,
    surfaceVariant = PurplePalette.DarkSurfaceVariant,
    onSurfaceVariant = PurplePalette.DarkOnSurfaceVariant,
    outline = PurplePalette.DarkOutline,
    outlineVariant = PurplePalette.DarkOutlineVariant,
    inverseOnSurface = PurplePalette.DarkInverseOnSurface,
    inverseSurface = PurplePalette.DarkInverseSurface,
    inversePrimary = PurplePalette.DarkInversePrimary,
    surfaceTint = PurplePalette.DarkSurfaceTint,
    surfaceDim = PurplePalette.DarkSurfaceDim,
    surfaceBright = PurplePalette.DarkSurfaceBright,
    surfaceContainerLowest = PurplePalette.DarkSurfaceContainerLowest,
    surfaceContainerLow = PurplePalette.DarkSurfaceContainerLow,
    surfaceContainer = PurplePalette.DarkSurfaceContainer,
    surfaceContainerHigh = PurplePalette.DarkSurfaceContainerHigh,
    surfaceContainerHighest = PurplePalette.DarkSurfaceVariant,
    scrim = Color(0xFF000000),
)
