package br.com.otavioesteves.finances.ui.screens.categories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.otavioesteves.finances.domain.model.CategorySummary
import br.com.otavioesteves.finances.presentation.AppViewModelProvider
import br.com.otavioesteves.finances.presentation.categories.CategoriesEvent
import br.com.otavioesteves.finances.presentation.categories.CategoriesUiState
import br.com.otavioesteves.finances.presentation.categories.CategoriesViewModel
import br.com.otavioesteves.finances.ui.components.CategorySummaryRow
import br.com.otavioesteves.finances.ui.components.EmptyState
import br.com.otavioesteves.finances.ui.components.MonthPeriodSelector
import br.com.otavioesteves.finances.ui.components.financeTopAppBarColors
import br.com.otavioesteves.finances.utils.MoneyFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(
    onCategoryClick: (CategorySummary) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: CategoriesViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val monthPeriod by viewModel.selectedMonthPeriod.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val isAtTop by remember {
        derivedStateOf { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0 }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 64.dp, bottom = 112.dp)
        ) {
            item(key = "month_selector") {
                MonthPeriodSelector(
                    monthPeriod = monthPeriod,
                    onPreviousClick = { viewModel.onEvent(CategoriesEvent.OnMonthChanged(monthPeriod.previousMonth())) },
                    onNextClick = { viewModel.onEvent(CategoriesEvent.OnMonthChanged(monthPeriod.nextMonth())) },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 20.dp)
                )
            }
            when (val state = uiState) {
                CategoriesUiState.Loading -> item(key = "loading") {
                    Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
                is CategoriesUiState.Error -> item(key = "error") {
                    EmptyState(
                        message = state.message,
                        onActionClick = { viewModel.onEvent(CategoriesEvent.OnRetryClicked) }
                    )
                }
                is CategoriesUiState.Empty -> item(key = "empty") {
                    EmptyState(message = "Nenhuma categoria com transações neste mês.")
                }
                is CategoriesUiState.Success -> items(
                    items = state.categories,
                    key = { it.category.id },
                    contentType = { "category" }
                ) { summary ->
                    CategorySummaryRow(
                        categoryName = summary.category.name,
                        totalAmountFormatted = remember(summary.totalAmount.cents) { MoneyFormatter.format(summary.totalAmount) },
                        categoryType = summary.category.type,
                        onClick = {
                            viewModel.onEvent(CategoriesEvent.OnCategoryClicked(summary))
                            onCategoryClick(summary)
                        }
                    )
                }
            }
        }
        CenterAlignedTopAppBar(
            title = { Text("Categorias") },
            colors = financeTopAppBarColors(isAtTop),
            windowInsets = WindowInsets(0, 0, 0, 0),
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}
