package br.com.otavioesteves.finances.ui.screens.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.otavioesteves.finances.domain.model.Money
import br.com.otavioesteves.finances.presentation.AppViewModelProvider
import br.com.otavioesteves.finances.presentation.charts.ChartsViewModel
import br.com.otavioesteves.finances.ui.components.CategoryUsageChart
import br.com.otavioesteves.finances.ui.components.FinanceCard
import br.com.otavioesteves.finances.ui.components.MonthPeriodSelector
import br.com.otavioesteves.finances.ui.components.financeTopAppBarColors
import br.com.otavioesteves.finances.utils.MoneyFormatter

private val monthLabels = listOf("jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set", "out", "nov", "dez")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartsScreen(
    modifier: Modifier = Modifier,
    viewModel: ChartsViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
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
                            ChartTitle("Categorias")
                            CategoryUsageChart(state.categorySummaries, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
                item(key = "annual_chart") {
                    FinanceCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(18.dp)
                        ) {
                            ChartTitle("Gastos em ${state.monthPeriod.year}")
                            AnnualBars(state.annualExpenses)
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
                            ChartTitle("Por categoria")
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
                            AnnualBars(state.categoryAnnualExpenses)
                        }
                    }
                }
            }
        }
        CenterAlignedTopAppBar(
            title = { Text("Gráficos") },
            colors = financeTopAppBarColors(isAtTop),
            windowInsets = WindowInsets(0, 0, 0, 0),
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}

@Composable
private fun ChartTitle(title: String) {
    Text(
        text = title,
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun AnnualBars(totals: List<Long>) {
    val total = remember(totals) { totals.sum() }
    val barColor = MaterialTheme.colorScheme.primary
    val baselineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
    Text(
        text = MoneyFormatter.format(Money.fromCents(total)),
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center
    )
    val description = remember(totals) {
        monthLabels.mapIndexed { index, month ->
            "$month: ${MoneyFormatter.format(Money.fromCents(totals[index]))}"
        }.joinToString("; ")
    }
    Canvas(
        modifier = Modifier.fillMaxWidth().height(150.dp).semantics { contentDescription = description }
    ) {
        val maxValue = (totals.maxOrNull() ?: 0L).coerceAtLeast(1L).toFloat()
        val slot = size.width / 12f
        val barWidth = slot * 0.56f
        val bottom = size.height - 1.dp.toPx()
        drawLine(baselineColor, Offset(0f, bottom), Offset(size.width, bottom), 1.dp.toPx())
        totals.forEachIndexed { index, cents ->
            if (cents > 0L) {
                val barHeight = (cents.toFloat() / maxValue * (size.height - 8.dp.toPx()))
                    .coerceAtLeast(3.dp.toPx())
                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(slot * index + (slot - barWidth) / 2f, bottom - barHeight),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(barWidth / 3f, barWidth / 3f)
                )
            }
        }
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(0.dp)) {
        monthLabels.forEach { month ->
            Text(
                text = month,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}
