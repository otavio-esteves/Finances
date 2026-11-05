package br.com.otavioesteves.finances.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable

// Muted hues shared by category cards, transaction badges and chart legends.
val CategoryPalette = listOf(
    Color(0xFFD18C79), Color(0xFFC5A477), Color(0xFF96B09C), Color(0xFF8DA6BE),
    Color(0xFFB39AB8), Color(0xFFC3B27C), Color(0xFF89B4B1), Color(0xFFBB919A),
    Color(0xFFA6A0C2), Color(0xFFA8AF85), Color(0xFFB39C8B), Color(0xFF92ADBA)
)
val OtherCategoryColor = Color(0xFF777780)

/** Stable color for a category, keyed by its id so the same category always gets the same slot. */
@Composable
fun categoryColor(categoryId: Long): Color {
    val palette = FinancesThemeTokens.colors.categoryPalette
    return palette[(categoryId % palette.size).toInt().let { if (it < 0) it + palette.size else it }]
}

/** A quieter version of the chart color for category surfaces and form accents. */
@Composable
fun categoryAccent(categoryId: Long): Color = androidx.compose.ui.graphics.lerp(
    androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
    categoryColor(categoryId),
    0.65f
)
