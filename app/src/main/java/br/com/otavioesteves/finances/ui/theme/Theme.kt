package br.com.otavioesteves.finances.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

// shadcn/ui "zinc" theme, dark variant — the app is dark-only by design.
private val DarkColorScheme = darkColorScheme(
    primary = ZincPrimary,
    onPrimary = ZincPrimaryForeground,
    background = ZincBackground,
    onBackground = ZincForeground,
    surface = ZincCard,
    onSurface = ZincCardForeground,
    surfaceVariant = ZincSecondary,
    onSurfaceVariant = ZincMutedForeground,
    secondary = ZincSecondary,
    onSecondary = ZincSecondaryForeground,
    error = ZincDestructive,
    onError = ZincDestructiveForeground,
    tertiary = AppIncome,
    outline = ZincBorder,
    outlineVariant = ZincBorder
)

// shadcn radius scale: --radius: 0.5rem (8dp), sm/md/lg/xl derived from it.
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp)
)

@Composable
fun FinancesTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content
    )
}
