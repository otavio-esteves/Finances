package br.com.otavioesteves.finances.ui.screens.home

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import br.com.otavioesteves.finances.ui.screens.chat.ChatScreen
import br.com.otavioesteves.finances.ui.screens.dashboard.DashboardScreen

private const val PAGE_HOME = 0
private const val PAGE_CHAT = 1
private const val PAGE_COUNT = 2

/**
 * Root of the app's primary navigation: Home (dashboard) and Chat as a
 * two-page swipeable pager. All other screens are reached from Home as
 * secondary navigation, stacked on top of this pager by the outer NavHost.
 */
@Composable
fun HomeChatPager(
    onCategoriesClick: () -> Unit,
    onAddTransactionClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onImportStatementClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pagerState = rememberPagerState(initialPage = PAGE_HOME) { PAGE_COUNT }

    HorizontalPager(
        state = pagerState,
        modifier = modifier.fillMaxSize()
    ) { page ->
        when (page) {
            PAGE_CHAT -> ChatScreen()
            else -> DashboardScreen(
                onCategoriesClick = onCategoriesClick,
                onAddTransactionClick = onAddTransactionClick,
                onHistoryClick = onHistoryClick,
                onSettingsClick = onSettingsClick,
                onImportStatementClick = onImportStatementClick
            )
        }
    }
}
