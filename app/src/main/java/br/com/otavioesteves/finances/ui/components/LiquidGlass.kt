package br.com.otavioesteves.finances.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import br.com.otavioesteves.finances.ui.theme.FinancesThemeTokens

private val GlassShape = RoundedCornerShape(50)

/** Blur only the background directly behind each floating control. */
@Composable
fun Modifier.glassBackdrop(state: HazeState?): Modifier {
    if (state == null) return this
    val backdrop = FinancesThemeTokens.colors.glassBackdrop
    return this.clip(GlassShape).hazeEffect(state) {
        backgroundColor = backdrop
        blurRadius = 24.dp
        tints = emptyList()
        noiseFactor = 0f
    }
}

@Composable
fun Modifier.liquidGlass(): Modifier = this
    .clip(GlassShape)
    .background(FinancesThemeTokens.colors.glassSurface.copy(alpha = 0.8f), GlassShape)
    .border(1.dp, FinancesThemeTokens.colors.border.copy(alpha = 0.35f), GlassShape)
