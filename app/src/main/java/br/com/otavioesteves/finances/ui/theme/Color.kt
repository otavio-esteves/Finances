package br.com.otavioesteves.finances.ui.theme

import androidx.compose.ui.graphics.Color

// shadcn/ui "zinc" theme, dark variant — https://ui.shadcn.com/themes
val ZincBackground = Color(0xFF09090B) // --background
val ZincForeground = Color(0xFFFAFAFA) // --foreground
val ZincCard = Color(0xFF09090B) // --card
val ZincCardForeground = Color(0xFFFAFAFA) // --card-foreground
val ZincSecondary = Color(0xFF27272A) // --secondary / --muted / --accent
val ZincSecondaryForeground = Color(0xFFFAFAFA) // --secondary-foreground / --accent-foreground
val ZincMutedForeground = Color(0xFFA1A1AA) // --muted-foreground
val ZincBorder = Color(0xFF27272A) // --border / --input
val ZincPrimary = Color(0xFFFAFAFA) // --primary
val ZincPrimaryForeground = Color(0xFF18181B) // --primary-foreground
val ZincDestructive = Color(0xFF7F1D1D) // --destructive
val ZincDestructiveForeground = Color(0xFFFAFAFA) // --destructive-foreground
val ZincRing = Color(0xFFD4D4D8) // --ring

// Domain-specific accents, kept close to the neutral palette above.
val AppIncome = Color(0xFF34D399) // emerald-400
val AppExpense = Color(0xFFF87171) // red-400

// Interactive accent (active tab, links, sent-message bubble) — same blue as
// the first slot of CategoryPalette, so the color reads consistently as
// "you/active" across the chart, chat and navigation.
val AppAccent = Color(0xFF3987E5)
val AppAccentForeground = Color(0xFFFAFAFA)
