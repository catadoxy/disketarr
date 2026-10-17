package io.github.catadoxy.disketarr.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

/**
 * Material 3 defaults with slightly rounder surfaces and no elevation tinting.
 */
private val DisketarrShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun DisketarrClientTheme(
    themeId: String = ThemeCatalog.DEFAULT_ID,
    content: @Composable () -> Unit
) {
    val themeOption = ThemeCatalog.getOption(themeId)
    val colorScheme = if (themeId == "light" || !isSystemInDarkTheme()) {
        themeOption.lightScheme
    } else {
        themeOption.darkScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MaterialTheme.typography,
        shapes = DisketarrShapes,
        content = content,
    )
}
