package br.com.otavioesteves.finances.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import br.com.otavioesteves.finances.domain.model.ThemePalette
import androidx.compose.ui.unit.dp

// Solid black, white and gray surfaces with red and orange accents.
private val DarkColorScheme = darkColorScheme(
    primary = AppPrimary,
    onPrimary = AppPrimaryForeground,
    primaryContainer = AppSecondary,
    onPrimaryContainer = AppForeground,
    background = AppBackground,
    onBackground = AppForeground,
    surface = AppCard,
    onSurface = AppCardForeground,
    surfaceDim = AppBackground,
    surfaceBright = AppSecondary,
    surfaceContainerLowest = AppBackground,
    surfaceContainerLow = AppCard,
    surfaceContainer = AppCard,
    surfaceContainerHigh = AppSecondary,
    surfaceContainerHighest = AppSecondary,
    surfaceVariant = AppSecondary,
    onSurfaceVariant = AppMutedForeground,
    secondary = AppSecondary,
    onSecondary = AppSecondaryForeground,
    secondaryContainer = AppSecondary,
    onSecondaryContainer = AppForeground,
    error = AppDestructive,
    onError = AppDestructiveForeground,
    tertiary = AppIncome,
    onTertiary = Color.Black,
    outline = AppBorder,
    outlineVariant = AppBorder,
    inverseSurface = AppForeground,
    inverseOnSurface = AppBackground,
    inversePrimary = AppSecondary,
    scrim = Color.Black,
    surfaceTint = Color.Transparent
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFFD93039),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD6C9),
    onPrimaryContainer = Color(0xFF5D101A),
    background = Color(0xFFFAF8F7),
    onBackground = Color(0xFF171A1E),
    surface = Color.White,
    onSurface = Color(0xFF171A1E),
    surfaceDim = Color(0xFFF2EEEC),
    surfaceBright = Color.White,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFFFCFB),
    surfaceContainer = Color(0xFFF4EFED),
    surfaceContainerHigh = Color(0xFFEDE6E3),
    surfaceContainerHighest = Color(0xFFE4DAD6),
    surfaceVariant = Color(0xFFF4EFED),
    onSurfaceVariant = Color(0xFF626970),
    secondary = Color(0xFFECEFF2),
    onSecondary = Color(0xFF20242A),
    secondaryContainer = Color(0xFFECEFF2),
    onSecondaryContainer = Color(0xFF20242A),
    error = Color(0xFFBC3544),
    onError = Color.White,
    tertiary = Color(0xFFC05C00),
    onTertiary = Color.White,
    outline = Color(0xFFBCC3CA),
    outlineVariant = Color(0xFFD8DDE2),
    inverseSurface = Color(0xFF20242A),
    inverseOnSurface = Color.White,
    inversePrimary = Color(0xFFECEFF2),
    scrim = Color.Black,
    surfaceTint = Color.Transparent
)

@Immutable
data class FinancesExtraColors(
    val heroSurface: Color,
    val heroLabel: Color,
    val income: Color,
    val expense: Color,
    val border: Color,
    val glassSurface: Color,
    val glassBackdrop: Color,
    val categoryPalette: List<Color>,
    val otherCategory: Color
)

private val DarkExtraColors = FinancesExtraColors(
    heroSurface = AppHeroSurface,
    heroLabel = AppHeroLabel,
    income = AppIncome,
    expense = AppExpense,
    border = AppBorder,
    glassSurface = AppCard,
    glassBackdrop = Color.Black,
    categoryPalette = CategoryPalette,
    otherCategory = OtherCategoryColor
)

private val LightExtraColors = FinancesExtraColors(
    heroSurface = Color(0xFFE9EDF1),
    heroLabel = Color(0xFF626970),
    income = Color(0xFFB95700),
    expense = Color(0xFFD22E42),
    border = Color(0xFFBCC3CA),
    glassSurface = Color.White,
    glassBackdrop = Color.White,
    categoryPalette = listOf(
        Color(0xFFD93039), Color(0xFFE67800), Color(0xFFB81936), Color(0xFFE45325),
        Color(0xFFA72C3B), Color(0xFF29292F), Color(0xFF777780), Color(0xFFB86400)
    ),
    otherCategory = Color(0xFF696A73)
)

private val LocalFinancesExtraColors = staticCompositionLocalOf { DarkExtraColors }

object FinancesThemeTokens {
    val colors: FinancesExtraColors
        @Composable get() = LocalFinancesExtraColors.current
}

// Rounded controls and cards soften the dense financial information.
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun FinancesTheme(
    darkTheme: Boolean = true,
    palette: ThemePalette = ThemePalette.RED,
    content: @Composable () -> Unit
) {
    val baseScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val baseExtras = if (darkTheme) DarkExtraColors else LightExtraColors
    val accent = paletteAccent(palette, darkTheme)
    val scheme = if (palette == ThemePalette.RED) baseScheme else baseScheme.copy(
        primary = accent,
        onPrimary = if (accent.luminance() > 0.179f) Color.Black else Color.White,
        primaryContainer = androidx.compose.ui.graphics.lerp(baseScheme.surface, accent, 0.2f),
        onPrimaryContainer = baseScheme.onSurface,
        tertiary = accent,
        onTertiary = if (accent.luminance() > 0.179f) Color.Black else Color.White
    )
    val extras = if (palette == ThemePalette.RED) baseExtras else baseExtras.copy(
        heroSurface = androidx.compose.ui.graphics.lerp(baseScheme.surface, accent, 0.16f),
        categoryPalette = listOf(accent, paletteAccent(palette, !darkTheme)) + baseExtras.categoryPalette.drop(2)
    )
    CompositionLocalProvider(LocalFinancesExtraColors provides extras) {
        MaterialTheme(
            colorScheme = scheme,
            typography = Typography,
            shapes = AppShapes,
            content = content
        )
    }
}


fun paletteAccent(palette: ThemePalette, darkTheme: Boolean): Color = when (palette) {
    ThemePalette.RED -> if (darkTheme) AppPrimary else Color(0xFFD93039)
    ThemePalette.ORANGE -> if (darkTheme) Color(0xFFFF9D26) else Color(0xFFAE5100)
    ThemePalette.BLUE -> if (darkTheme) Color(0xFF55AAFF) else Color(0xFF1565C0)
    ThemePalette.GREEN -> if (darkTheme) Color(0xFF47D78A) else Color(0xFF137B45)
    ThemePalette.PURPLE -> if (darkTheme) Color(0xFFC18AFF) else Color(0xFF7933C5)
}
