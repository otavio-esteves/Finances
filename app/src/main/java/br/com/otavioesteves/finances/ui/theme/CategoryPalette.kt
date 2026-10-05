package br.com.otavioesteves.finances.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable

// Saturated warm colors balanced with black, gray and white.
val CategoryPalette = listOf(
    Color(0xFFFF4D32),
    Color(0xFFFF9D26),
    Color(0xFFFF354B),
    Color(0xFFF26538),
    Color(0xFFCF3346),
    Color(0xFFE8E8E8),
    Color(0xFF696A73),
    Color(0xFFFFBF58)
)
val OtherCategoryColor = Color(0xFF777780)

/** Stable color for a category, keyed by its id so the same category always gets the same slot. */
@Composable
fun categoryColor(categoryId: Long): Color {
    val palette = FinancesThemeTokens.colors.categoryPalette
    return palette[(categoryId % palette.size).toInt().let { if (it < 0) it + palette.size else it }]
}
