package br.com.otavioesteves.finances.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.animateColorAsState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import br.com.otavioesteves.finances.ui.components.liquidGlass
import br.com.otavioesteves.finances.ui.components.glassBackdrop
import dev.chrisbanes.haze.HazeState

/** Three independent floating controls: menu, page switcher, and import. */
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
        modifier = modifier.fillMaxWidth().padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        GlassActionButton(Icons.Filled.Menu, "Abrir menu", hazeState, onMenuClick)

        Row(
            modifier = Modifier
                .glassBackdrop(hazeState)
                .liquidGlass()
                .padding(5.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            PageButton(
                tab = MainTab.HOME,
                icon = Icons.Filled.Home,
                label = "Início",
                selected = selectedTab == MainTab.HOME,
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

        GlassActionButton(Icons.Filled.UploadFile, "Importar fatura ou extrato", hazeState, onImportClick)
    }
}

@Composable
private fun GlassActionButton(icon: ImageVector, label: String, hazeState: HazeState, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(54.dp)
            .glassBackdrop(hazeState)
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
    val iconColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "Ícone da aba"
    )
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
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
