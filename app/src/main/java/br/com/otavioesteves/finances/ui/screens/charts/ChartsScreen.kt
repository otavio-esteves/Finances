package br.com.otavioesteves.finances.ui.screens.charts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.otavioesteves.finances.presentation.AppViewModelProvider
import br.com.otavioesteves.finances.presentation.charts.ChartsViewModel
import br.com.otavioesteves.finances.ui.components.AnnualChartStyle
import br.com.otavioesteves.finances.ui.components.AnnualExpenseChart
import br.com.otavioesteves.finances.ui.components.CategoryChartStyle
import br.com.otavioesteves.finances.ui.components.CategoryUsageChart
import br.com.otavioesteves.finances.ui.components.FinanceCard
import br.com.otavioesteves.finances.ui.components.MonthPeriodSelector
import br.com.otavioesteves.finances.ui.components.financeTopAppBarColors

private val categoryStyles = CategoryChartStyle.entries.map { it.label }
private val annualStyles = AnnualChartStyle.entries.map { it.label }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartsScreen(
    modifier: Modifier = Modifier,
    viewModel: ChartsViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val exampleAction by viewModel.exampleAction.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var categoryStyle by rememberSaveable { mutableStateOf(CategoryChartStyle.DONUT) }
    var annualStyle by rememberSaveable { mutableStateOf(AnnualChartStyle.BARS) }
    var categoryAnnualStyle by rememberSaveable { mutableStateOf(AnnualChartStyle.BARS) }
    val isAtTop by remember {
        derivedStateOf { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0 }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 64.dp, bottom = 112.dp)
        ) {
            item(key = "month_selector") {
                MonthPeriodSelector(
                    monthPeriod = state.monthPeriod,
                    onPreviousClick = { viewModel.onMonthSelected(state.monthPeriod.previousMonth()) },
                    onNextClick = { viewModel.onMonthSelected(state.monthPeriod.nextMonth()) },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp)
                )
            }
            if (state.monthPeriod.year == 2026) {
                item(key = "examples_2026") {
                    FinanceCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Exemplos de janeiro a outubro de 2026", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Lançamentos fictícios identificados por [EXEMPLO]. Eles entram nos totais e podem ser removidos aqui.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (state.exampleCount > 0) {
                                Text("${state.exampleCount} exemplos adicionados", style = MaterialTheme.typography.labelMedium)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = viewModel::addExamples, enabled = !exampleAction.isBusy) {
                                    Text(if (state.exampleCount == 0) "Adicionar exemplos" else "Completar exemplos")
                                }
                                if (state.exampleCount > 0) {
                                    TextButton(onClick = viewModel::removeExamples, enabled = !exampleAction.isBusy) {
                                        Text("Remover")
                                    }
                                }
                            }
                            exampleAction.error?.let { error ->
                                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
            if (state.isLoading) {
                item(key = "loading") {
                    Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            } else {
                item(key = "category_chart") {
                    FinanceCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            ChartTitle("Categorias", categoryStyle.label, categoryStyles) { index ->
                                categoryStyle = CategoryChartStyle.entries[index]
                            }
                            CategoryUsageChart(state.categorySummaries, modifier = Modifier.fillMaxWidth(), style = categoryStyle)
                        }
                    }
                }
                item(key = "annual_chart") {
                    FinanceCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(18.dp)
                        ) {
                            ChartTitle("Gastos em ${state.monthPeriod.year}", annualStyle.label, annualStyles) { index ->
                                annualStyle = AnnualChartStyle.entries[index]
                            }
                            AnnualExpenseChart(state.annualExpenses, annualStyle)
                        }
                    }
                }
                item(key = "category_annual_chart") {
                    FinanceCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            ChartTitle("Por categoria", categoryAnnualStyle.label, annualStyles) { index ->
                                categoryAnnualStyle = AnnualChartStyle.entries[index]
                            }
                            var menuExpanded by remember { mutableStateOf(false) }
                            val selectedCategory = state.categories.firstOrNull { it.id == state.selectedCategoryId }
                            Box {
                                OutlinedButton(
                                    onClick = { menuExpanded = true },
                                    enabled = state.categories.isNotEmpty()
                                ) {
                                    Text(selectedCategory?.name ?: "Nenhuma categoria")
                                    Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                                }
                                DropdownMenu(
                                    expanded = menuExpanded,
                                    onDismissRequest = { menuExpanded = false }
                                ) {
                                    state.categories.forEach { category ->
                                        DropdownMenuItem(
                                            text = { Text(category.name) },
                                            onClick = {
                                                viewModel.onCategorySelected(category.id)
                                                menuExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                            AnnualExpenseChart(state.categoryAnnualExpenses, categoryAnnualStyle)
                        }
                    }
                }
            }
        }
        CenterAlignedTopAppBar(
            title = { Text("Gráficos", modifier = Modifier.testTag("charts_title")) },
            colors = financeTopAppBarColors(isAtTop),
            windowInsets = WindowInsets(0, 0, 0, 0),
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}

@Composable
private fun ChartTitle(
    title: String,
    selectedStyle: String,
    styles: List<String>,
    onStyleSelected: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(
            text = title,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        // Overlay the new control so it does not increase the existing title/card height.
        Box(modifier = Modifier.matchParentSize(), contentAlignment = Alignment.CenterEnd) {
            Box {
                IconButton(onClick = { expanded = true }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Filled.ArrowDropDown,
                        contentDescription = "Tipo do gráfico $title: $selectedStyle",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    styles.forEachIndexed { index, label ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            trailingIcon = {
                                if (label == selectedStyle) Icon(Icons.Filled.Check, contentDescription = "Selecionado")
                            },
                            onClick = {
                                onStyleSelected(index)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}
