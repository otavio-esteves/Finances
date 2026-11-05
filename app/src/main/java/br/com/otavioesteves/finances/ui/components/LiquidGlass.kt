package br.com.otavioesteves.finances.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import dev.chrisbanes.haze.hazeSource
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInputScale
import br.com.otavioesteves.finances.ui.theme.FinancesThemeTokens

val LocalFinanceGlassState = compositionLocalOf<HazeState?> { null }

private val GlassShape = RoundedCornerShape(50)

/** Blur only the background directly behind each floating control. */
@Composable
@OptIn(ExperimentalHazeApi::class)
fun Modifier.glassBackdrop(state: HazeState?, shape: Shape = GlassShape): Modifier {
    val source = state ?: LocalFinanceGlassState.current ?: return this
    val backdrop = FinancesThemeTokens.colors.glassBackdrop
    return this.clip(shape).hazeEffect(source) {
        backgroundColor = backdrop
        blurRadius = 24.dp
        tints = emptyList()
        noiseFactor = 0f
        // Only the blurred backdrop is downsampled; text, borders and icons keep their native resolution.
        inputScale = HazeInputScale.Fixed(0.5f)
    }
}

@Composable
fun Modifier.liquidGlass(): Modifier = this
    .clip(GlassShape)
    .background(FinancesThemeTokens.colors.glassSurface.copy(alpha = 0.58f), GlassShape)
    .border(1.dp, FinancesThemeTokens.colors.border.copy(alpha = 0.35f), GlassShape)

/** Secondary routes own a backdrop only while displayed; the main pager keeps its shared source. */
@Composable
fun FinanceGlassPage(content: @Composable () -> Unit) {
    val state = remember { HazeState() }
    CompositionLocalProvider(LocalFinanceGlassState provides state) {
        Box(Modifier.fillMaxSize().hazeSource(state)) { content() }
    }
}
