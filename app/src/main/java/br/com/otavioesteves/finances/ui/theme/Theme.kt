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
import androidx.compose.ui.unit.dp

// Solid black, white and gray surfaces, with green and red for amounts.
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
    onTertiary = AppPrimaryForeground,
    outline = AppBorder,
    outlineVariant = AppBorder,
    inverseSurface = AppForeground,
    inverseOnSurface = AppBackground,
    inversePrimary = AppSecondary,
    scrim = Color.Black,
    surfaceTint = Color.Transparent
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF20242A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6E9ED),
    onPrimaryContainer = Color(0xFF20242A),
    background = Color(0xFFF7F8FA),
    onBackground = Color(0xFF171A1E),
    surface = Color.White,
    onSurface = Color(0xFF171A1E),
    surfaceDim = Color(0xFFF0F2F5),
    surfaceBright = Color.White,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFBFCFD),
    surfaceContainer = Color(0xFFF2F4F6),
    surfaceContainerHigh = Color(0xFFECEFF2),
    surfaceContainerHighest = Color(0xFFE5E9ED),
    surfaceVariant = Color(0xFFECEFF2),
    onSurfaceVariant = Color(0xFF626970),
    secondary = Color(0xFFECEFF2),
    onSecondary = Color(0xFF20242A),
    secondaryContainer = Color(0xFFECEFF2),
    onSecondaryContainer = Color(0xFF20242A),
    error = Color(0xFFBC3544),
    onError = Color.White,
    tertiary = Color(0xFF137A49),
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
    income = Color(0xFF137A49),
    expense = Color(0xFFBC3544),
    border = Color(0xFFBCC3CA),
    glassSurface = Color.White,
    glassBackdrop = Color.White,
    categoryPalette = listOf(
        Color(0xFF30343B), Color(0xFF717985), Color(0xFF218459), Color(0xFFBF4A55),
        Color(0xFF9DA6B0), Color(0xFF555E68), Color(0xFF57A47B), Color(0xFFCE7880)
    ),
    otherCategory = Color(0xFF87909A)
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
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalFinancesExtraColors provides if (darkTheme) DarkExtraColors else LightExtraColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
            typography = Typography,
            shapes = AppShapes,
            content = content
        )
    }
}
