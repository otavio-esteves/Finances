package br.com.otavioesteves.finances.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable

// Neutral category colors with a few green and red accents.
val CategoryPalette = listOf(
    Color(0xFFF1F1F1),
    Color(0xFF9D9D9D),
    Color(0xFF43D17E),
    Color(0xFFF06B6B),
    Color(0xFFCFCFCF),
    Color(0xFF737373),
    Color(0xFF2FA462),
    Color(0xFFBF5050)
)
val OtherCategoryColor = Color(0xFF555555)

/** Stable color for a category, keyed by its id so the same category always gets the same slot. */
@Composable
fun categoryColor(categoryId: Long): Color {
    val palette = FinancesThemeTokens.colors.categoryPalette
    return palette[(categoryId % palette.size).toInt().let { if (it < 0) it + palette.size else it }]
}
