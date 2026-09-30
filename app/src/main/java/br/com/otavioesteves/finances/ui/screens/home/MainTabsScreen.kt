package br.com.otavioesteves.finances.ui.screens.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.otavioesteves.finances.domain.model.CategorySummary
import br.com.otavioesteves.finances.ui.navigation.FinancesBottomBar
import br.com.otavioesteves.finances.ui.navigation.MainTab
import br.com.otavioesteves.finances.ui.screens.categories.CategoriesScreen
import br.com.otavioesteves.finances.ui.screens.chat.ChatScreen
import br.com.otavioesteves.finances.ui.screens.charts.ChartsScreen
import br.com.otavioesteves.finances.ui.screens.dashboard.DashboardScreen
import br.com.otavioesteves.finances.ui.screens.settings.SettingsScreen
import kotlinx.coroutines.launch
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import androidx.compose.runtime.remember
import br.com.otavioesteves.finances.ui.theme.FinancesThemeTokens

private enum class Section { MAIN, CATEGORIES, SETTINGS }

/** Home, charts and chat share the primary swipeable surface. */
@Composable
fun MainTabsScreen(
    onAddTransactionClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onImportStatementClick: () -> Unit,
    onCategoryClick: (CategorySummary) -> Unit,
    onAiModelClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pagerState = rememberPagerState(pageCount = { MainTab.entries.size })
    val hazeState = remember { HazeState() }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var section by rememberSaveable { mutableStateOf(Section.MAIN) }

    BackHandler(enabled = section != Section.MAIN) {
        section = Section.MAIN
    }

    fun showTab(tab: MainTab) {
        section = Section.MAIN
        scope.launch {
            drawerState.close()
            pagerState.animateScrollToPage(tab.ordinal)
        }
    }

    fun showSection(value: Section) {
        scope.launch {
            drawerState.close()
            section = value
        }
    }

    fun openRoute(action: () -> Unit) {
        scope.launch {
            drawerState.close()
            action()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = false,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(304.dp),
                drawerContainerColor = MaterialTheme.colorScheme.background,
                drawerShape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(horizontal = 18.dp, vertical = 24.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 36.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier.size(46.dp).clip(RoundedCornerShape(15.dp)).background(FinancesThemeTokens.colors.heroSurface),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.AccountBalanceWallet, contentDescription = null, modifier = Modifier.size(23.dp))
                        }
                        Column {
                            Text("Finances", style = MaterialTheme.typography.titleLarge)
                            Text("Seu espaço financeiro", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        DrawerLabel("VISÃO GERAL")
                        DrawerMenuItem("Início", Icons.Filled.Home,
                            section == Section.MAIN && pagerState.currentPage == MainTab.HOME.ordinal) { showTab(MainTab.HOME) }
                        DrawerMenuItem("Gráficos", Icons.Filled.BarChart,
                            section == Section.MAIN && pagerState.currentPage == MainTab.CHARTS.ordinal) { showTab(MainTab.CHARTS) }
                        DrawerMenuItem("Chat", Icons.AutoMirrored.Filled.Chat,
                            section == Section.MAIN && pagerState.currentPage == MainTab.CHAT.ordinal) { showTab(MainTab.CHAT) }
                        DrawerMenuItem("Categorias", Icons.Filled.PieChart, section == Section.CATEGORIES) {
                            showSection(Section.CATEGORIES)
                        }
                        DrawerMenuItem("Histórico", Icons.AutoMirrored.Outlined.ReceiptLong, false) {
                            openRoute(onHistoryClick)
                        }

                        Spacer(Modifier.height(28.dp))
                        DrawerLabel("AÇÕES")
                        DrawerMenuItem("Importar extrato", Icons.Filled.UploadFile, false) {
                            openRoute(onImportStatementClick)
                        }
                        DrawerMenuItem("Novo lançamento", Icons.Filled.Add, false) {
                            openRoute(onAddTransactionClick)
                        }

                        Spacer(Modifier.height(28.dp))
                        DrawerLabel("PREFERÊNCIAS")
                        DrawerMenuItem("Configurações", Icons.Filled.Settings, section == Section.SETTINGS) {
                            showSection(Section.SETTINGS)
                        }
                    }

                    Text(
                        "PRIVADO · NO SEU APARELHO",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 14.dp, top = 20.dp, bottom = 4.dp)
                    )
                }
            }
        },
        modifier = modifier
    ) {
        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing.only(
                WindowInsetsSides.Top + WindowInsetsSides.Horizontal
            )
        ) { paddingValues ->
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                when (section) {
                    Section.MAIN -> HorizontalPager(
                        state = pagerState,
                        // Keep adjacent pages composed during a swipe.
                        beyondViewportPageCount = 1,
                        modifier = Modifier.fillMaxSize().hazeSource(hazeState)
                    ) { page ->
                        when (MainTab.entries[page]) {
                            MainTab.HOME -> DashboardScreen(
                                onHistoryClick = onHistoryClick,
                                hazeState = hazeState
                            )
                            MainTab.CHARTS -> ChartsScreen()
                            MainTab.CHAT -> ChatScreen()
                        }
                    }
                    Section.CATEGORIES -> CategoriesScreen(
                        onCategoryClick = onCategoryClick,
                        modifier = Modifier.fillMaxSize().hazeSource(hazeState)
                    )
                    Section.SETTINGS -> SettingsScreen(
                        onAiModelClick = onAiModelClick,
                        modifier = Modifier.fillMaxSize().hazeSource(hazeState)
                    )
                }
                FinancesBottomBar(
                    selectedTab = if (section == Section.MAIN) MainTab.entries[pagerState.currentPage] else null,
                    onTabSelected = ::showTab,
                    onMenuClick = { scope.launch { drawerState.open() } },
                    onImportClick = onImportStatementClick,
                    hazeState = hazeState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 10.dp)
                )
            }
        }
    }
}

@Composable
private fun DrawerLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.4.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 14.dp, bottom = 9.dp)
    )
}

@Composable
private fun DrawerMenuItem(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    val background by animateColorAsState(
        if (selected) FinancesThemeTokens.colors.heroSurface else Color.Transparent,
        animationSpec = tween(180),
        label = "Fundo do menu"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(shape)
            .background(background)
            .border(1.dp, if (selected) MaterialTheme.colorScheme.outline.copy(alpha = 0.3f) else Color.Transparent, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(21.dp),
            tint = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
        if (selected) Box(Modifier.size(5.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface))
    }
}
