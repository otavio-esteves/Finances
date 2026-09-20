package br.com.otavioesteves.finances.ui.screens.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import br.com.otavioesteves.finances.domain.model.CategorySummary
import br.com.otavioesteves.finances.ui.navigation.FinancesBottomBar
import br.com.otavioesteves.finances.ui.navigation.MainTab
import br.com.otavioesteves.finances.ui.screens.categories.CategoriesScreen
import br.com.otavioesteves.finances.ui.screens.chat.ChatScreen
import br.com.otavioesteves.finances.ui.screens.dashboard.DashboardScreen
import br.com.otavioesteves.finances.ui.screens.settings.SettingsScreen

/**
 * Root of the app's primary navigation: a persistent bottom bar switching
 * between Home, Chat, Categorias and Config. All other screens (add/edit
 * transaction, import, history, AI model, category details) are reached from
 * here and stacked on top by the outer NavHost.
 */
@Composable
fun MainTabsScreen(
    onAddTransactionClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onImportStatementClick: () -> Unit,
    onCategoryClick: (CategorySummary) -> Unit,
    onAiModelClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by rememberSaveable { mutableStateOf(MainTab.HOME) }

    Scaffold(
        modifier = modifier,
        bottomBar = {
            FinancesBottomBar(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (selectedTab) {
                MainTab.HOME -> DashboardScreen(
                    onAddTransactionClick = onAddTransactionClick,
                    onHistoryClick = onHistoryClick,
                    onImportStatementClick = onImportStatementClick
                )
                MainTab.CHAT -> ChatScreen()
                MainTab.CATEGORIES -> CategoriesScreen(onCategoryClick = onCategoryClick)
                MainTab.SETTINGS -> SettingsScreen(onAiModelClick = onAiModelClick)
            }
        }
    }
}
