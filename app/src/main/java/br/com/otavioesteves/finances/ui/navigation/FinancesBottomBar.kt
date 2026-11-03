package br.com.otavioesteves.finances.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
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

private val ActionSize = 50.dp
private val TabSize = 46.dp
private val TabGap = 2.dp
private val PillPadding = 3.dp

/** One shared blur clipped to the three control outlines, leaving the gaps transparent. */
private object NavbarBackdropShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline = with(density) {
        val action = ActionSize.roundToPx().toFloat()
        val tab = TabSize.roundToPx()
        val padding = PillPadding.roundToPx()
        val pillWidth = (tab * MainTab.entries.size + TabGap.roundToPx() * (MainTab.entries.size - 1) + padding * 2).toFloat()
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

/** Menu, four primary pages, and import remain reachable on narrow screens. */
@Composable
fun FinancesBottomBar(
    selectedTab: MainTab?,
    onTabSelected: (MainTab) -> Unit,
    onMenuClick: () -> Unit,
    onImportClick: () -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 10.dp)
            .glassBackdrop(hazeState, NavbarBackdropShape),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        GlassActionButton(Icons.Filled.Menu, "Abrir menu", onMenuClick)

        Row(
            modifier = Modifier
                .liquidGlass()
                .padding(PillPadding),
            horizontalArrangement = Arrangement.spacedBy(TabGap)
        ) {
            PageButton(
                tab = MainTab.HOME,
                icon = Icons.Filled.Home,
                label = "Início",
                selected = selectedTab == MainTab.HOME,
                onClick = onTabSelected
            )
            PageButton(
                tab = MainTab.CHARTS,
                icon = Icons.Filled.BarChart,
                label = "Gráficos",
                selected = selectedTab == MainTab.CHARTS,
                onClick = onTabSelected
            )
            PageButton(
                tab = MainTab.TRANSACTIONS,
                icon = Icons.AutoMirrored.Filled.ReceiptLong,
                label = "Transações",
                selected = selectedTab == MainTab.TRANSACTIONS,
                onClick = onTabSelected
            )
            PageButton(
                tab = MainTab.CHAT,
                icon = Icons.AutoMirrored.Filled.Chat,
                label = "Chat",
                selected = selectedTab == MainTab.CHAT,
                onClick = onTabSelected
            )
        }

        GlassActionButton(Icons.Filled.UploadFile, "Importar fatura ou extrato", onImportClick)
    }
}

@Composable
private fun GlassActionButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(ActionSize)
            .liquidGlass()
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun PageButton(
    tab: MainTab,
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: (MainTab) -> Unit
) {
    val iconColor = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .size(TabSize)
            .clip(CircleShape)
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f) else androidx.compose.ui.graphics.Color.Transparent)
            .selectable(selected = selected, role = Role.Tab, onClick = { onClick(tab) }),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(23.dp),
            tint = iconColor
        )
    }
}
