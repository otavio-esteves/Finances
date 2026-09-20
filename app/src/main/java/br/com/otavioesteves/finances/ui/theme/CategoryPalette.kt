package br.com.otavioesteves.finances.ui.theme

import androidx.compose.ui.graphics.Color

// Categorical palette validated for the app's dark surface (#141414) with the
// dataviz skill's validator: fixed hue order, never cycled or reassigned by value.
val CategoryPalette = listOf(
    Color(0xFF3987E5), // blue
    Color(0xFFD95926), // orange
    Color(0xFF199E70), // aqua
    Color(0xFFC98500), // yellow
    Color(0xFFD55181), // magenta
    Color(0xFF008300), // green
    Color(0xFF9085E9), // violet
    Color(0xFFE66767) // red
)
val OtherCategoryColor = Color(0xFF5C5C5C)

/** Stable color for a category, keyed by its id so the same category always gets the same slot. */
fun categoryColor(categoryId: Long): Color =
    CategoryPalette[(categoryId % CategoryPalette.size).toInt().let { if (it < 0) it + CategoryPalette.size else it }]
