package br.com.otavioesteves.finances.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.roundToInt
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import br.com.otavioesteves.finances.ui.components.liquidGlass
import br.com.otavioesteves.finances.ui.components.glassBackdrop
import dev.chrisbanes.haze.HazeState

private val ActionSize = 57.5.dp
private val TabSize = 52.9.dp
private val TabGap = 2.3.dp
private val PillPadding = 3.45.dp

/** One shared blur clipped to the three control outlines, leaving the gaps transparent. */
private class NavbarBackdropShape(private val scale: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline = with(density) {
        val action = (ActionSize * scale).roundToPx().toFloat()
        val tab = (TabSize * scale).roundToPx()
        val padding = (PillPadding * scale).roundToPx()
        val pillWidth = (tab * MainTab.entries.size + (TabGap * scale).roundToPx() * (MainTab.entries.size - 1) + padding * 2).toFloat()
        val pillHeight = (tab + padding * 2).toFloat()
        val centerLeft = ((size.width - pillWidth) / 2f).roundToInt().toFloat()
        val path = Path()
        fun capsule(left: Float, width: Float, height: Float) {
            val top = ((size.height - height) / 2f).roundToInt().toFloat()
            path.addRoundRect(RoundRect(Rect(left, top, left + width, top + height), CornerRadius(height / 2f)))
        }
        capsule(0f, action, action)
        capsule(if (layoutDirection == LayoutDirection.Ltr) centerLeft else size.width - pillWidth - centerLeft, pillWidth, pillHeight)
        capsule(size.width - action, action, action)
        Outline.Generic(path)
    }
}

/** Menu, primary pages, and import remain reachable on narrow screens. */
@Composable
fun FinancesBottomBar(
    selectedTab: MainTab?,
    onTabSelected: (MainTab) -> Unit,
    onMenuClick: () -> Unit,
    onImportClick: () -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth().padding(horizontal = 10.dp)) {
        // Preserve the proportions while retaining a little space between controls on narrow phones.
        val scale = (maxWidth / 350.dp).coerceAtMost(1f)
        val backdropShape = remember(scale) { NavbarBackdropShape(scale) }
        Row(
            modifier = Modifier.fillMaxWidth().glassBackdrop(hazeState, backdropShape),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlassActionButton(Icons.Filled.Menu, "Abrir menu", onMenuClick, scale)

            Row(
                modifier = Modifier
                    .liquidGlass()
                    .padding(PillPadding * scale),
                horizontalArrangement = Arrangement.spacedBy(TabGap * scale)
            ) {
                PageButton(
                    tab = MainTab.HOME,
                    icon = Icons.Filled.Home,
                    label = "Início",
                    selected = selectedTab == MainTab.HOME,
                    scale = scale,
                    onClick = onTabSelected
                )
                PageButton(
                    tab = MainTab.CHARTS,
                    icon = Icons.Filled.BarChart,
                    label = "Gráficos",
                    selected = selectedTab == MainTab.CHARTS,
                    scale = scale,
                    onClick = onTabSelected
                )
                PageButton(
                    tab = MainTab.TRANSACTIONS,
                    icon = Icons.AutoMirrored.Filled.ReceiptLong,
                    label = "Transações",
                    selected = selectedTab == MainTab.TRANSACTIONS,
                    scale = scale,
                    onClick = onTabSelected
                )
                PageButton(
                    tab = MainTab.CATEGORIES,
                    icon = Icons.Filled.Sell,
                    label = "Categorias",
                    selected = selectedTab == MainTab.CATEGORIES,
                    scale = scale,
                    onClick = onTabSelected
                )
            }

            GlassActionButton(Icons.Filled.UploadFile, "Importar fatura ou extrato", onImportClick, scale)
        }
    }
}

@Composable
private fun GlassActionButton(icon: ImageVector, label: String, onClick: () -> Unit, scale: Float) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(ActionSize * scale)
            .liquidGlass()
    ) {
        Icon(imageVector = icon, contentDescription = label, modifier = Modifier.size(27.6.dp * scale), tint = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun PageButton(
    tab: MainTab,
    icon: ImageVector,
    label: String,
    selected: Boolean,
    scale: Float,
    onClick: (MainTab) -> Unit
) {
    val iconColor = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .size(TabSize * scale)
            .clip(CircleShape)
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f) else androidx.compose.ui.graphics.Color.Transparent)
            .selectable(selected = selected, role = Role.Tab, onClick = { onClick(tab) }),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(26.45.dp * scale),
            tint = iconColor
        )
    }
}
