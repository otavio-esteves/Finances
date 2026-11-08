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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.otavioesteves.finances.domain.model.MonthPeriod
import br.com.otavioesteves.finances.ui.components.LocalFinanceGlassState
import br.com.otavioesteves.finances.domain.model.CategorySummary
import br.com.otavioesteves.finances.ui.navigation.FinancesBottomBar
import br.com.otavioesteves.finances.ui.navigation.MainTab
import br.com.otavioesteves.finances.ui.screens.categories.CategoriesScreen
import br.com.otavioesteves.finances.ui.screens.charts.ChartsScreen
import br.com.otavioesteves.finances.ui.screens.dashboard.DashboardScreen
import br.com.otavioesteves.finances.ui.screens.settings.SettingsScreen
import br.com.otavioesteves.finances.ui.screens.transactions.TransactionsScreen
import br.com.otavioesteves.finances.domain.model.Transaction
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import androidx.compose.runtime.remember
import br.com.otavioesteves.finances.ui.theme.FinancesThemeTokens

private enum class Section { MAIN, SETTINGS }

/** Primary pages share the swipeable surface. */
@Composable
fun MainTabsScreen(
    onHistoryClick: (MonthPeriod) -> Unit,
    onTransactionClick: (Transaction) -> Unit,
    onImportStatementClick: () -> Unit,
    onCategoryClick: (CategorySummary, MonthPeriod) -> Unit,
    onAiModelClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pagerState = rememberPagerState(pageCount = { MainTab.entries.size })
    val settledPage by remember { derivedStateOf { pagerState.settledPage } }
    val hazeState = remember { HazeState() }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val screenStateHolder = rememberSaveableStateHolder()
    var section by rememberSaveable { mutableStateOf(Section.MAIN) }
    var tabNavigationJob by remember { mutableStateOf<Job?>(null) }

    fun showTab(tab: MainTab) {
        if (section == Section.MAIN && pagerState.currentPage == tab.ordinal && !pagerState.isScrollInProgress) {
            if (drawerState.isOpen) scope.launch { drawerState.close() }
            return
        }
        section = Section.MAIN
        if (drawerState.isOpen) scope.launch { drawerState.close() }
        tabNavigationJob?.cancel()
        tabNavigationJob = scope.launch {
            // A tab click selects a destination directly; only a horizontal gesture moves the full viewport.
            pagerState.scrollToPage(tab.ordinal)
        }
    }

    BackHandler(enabled = section != Section.MAIN || settledPage != MainTab.HOME.ordinal || pagerState.isScrollInProgress) {
        if (section != Section.MAIN) section = Section.MAIN else showTab(MainTab.HOME)
    }

    fun showSection(value: Section) {
        scope.launch {
            drawerState.close()
            section = value
        }
    }

    CompositionLocalProvider(LocalFinanceGlassState provides hazeState) {
    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen,
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
                    Text("Finances", style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(start = 14.dp, bottom = 32.dp))
                    Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
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
                screenStateHolder.SaveableStateProvider(section.name) {
                    when (section) {
                        Section.MAIN -> HorizontalPager(
                            state = pagerState,
                            // Keep neighbouring layouts ready; their financial collectors stay paused until visible.
                            beyondViewportPageCount = 1,
                            modifier = Modifier.fillMaxSize().hazeSource(hazeState)
                        ) { page ->
                            ActivePage(
                                active = page == pagerState.currentPage ||
                                    (pagerState.isScrollInProgress && page == pagerState.targetPage)
                            ) {
                                when (MainTab.entries[page]) {
                                    MainTab.HOME -> DashboardScreen(
                                        onImportStatementClick = onImportStatementClick,
                                        onCategoryClick = onCategoryClick,
                                        onHistoryClick = onHistoryClick,
                                        onTransactionClick = onTransactionClick,
                                        hazeState = hazeState
                                    )
                                    MainTab.CHARTS -> ChartsScreen(
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    MainTab.CATEGORIES -> CategoriesScreen(
                                        onCategoryClick = onCategoryClick,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    MainTab.TRANSACTIONS -> TransactionsScreen(
                                        onBackClick = { showTab(MainTab.HOME) },
                                        onTransactionClick = onTransactionClick,
                                        embeddedInTabs = true,
                                        hazeState = hazeState
                                    )
                                }
                            }
                        }
                        Section.SETTINGS -> SettingsScreen(
                            onAiModelClick = onAiModelClick,
                            modifier = Modifier.fillMaxSize().hazeSource(hazeState)
                        )
                    }
                }
                FinancesBottomBar(
                    selectedTab = if (section == Section.MAIN) MainTab.entries.getOrNull(settledPage) else null,
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
}

@Composable
private fun ActivePage(active: Boolean, content: @Composable () -> Unit) {
    val parent = LocalLifecycleOwner.current
    val owner = remember(parent) { PageLifecycleOwner(parent.lifecycle, active) }
    DisposableEffect(parent, owner) {
        parent.lifecycle.addObserver(owner)
        onDispose {
            parent.lifecycle.removeObserver(owner)
            owner.dispose()
        }
    }
    SideEffect { owner.setActive(active) }
    CompositionLocalProvider(LocalLifecycleOwner provides owner, content = content)
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
