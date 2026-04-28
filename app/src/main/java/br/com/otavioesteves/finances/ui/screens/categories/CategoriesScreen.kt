package br.com.otavioesteves.finances.ui.screens.categories

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.otavioesteves.finances.domain.model.Category
import br.com.otavioesteves.finances.domain.model.CategorySummary
import br.com.otavioesteves.finances.domain.model.CategoryType
import br.com.otavioesteves.finances.domain.model.MonthPeriod
import br.com.otavioesteves.finances.domain.model.Money
import br.com.otavioesteves.finances.presentation.AppViewModelProvider
import br.com.otavioesteves.finances.presentation.categories.CategoriesEvent
import br.com.otavioesteves.finances.presentation.categories.CategoriesUiState
import br.com.otavioesteves.finances.presentation.categories.CategoriesViewModel
import br.com.otavioesteves.finances.ui.components.EmptyState
import br.com.otavioesteves.finances.ui.theme.FinancesTheme
import br.com.otavioesteves.finances.utils.MoneyFormatter

@Composable
fun CategoriesScreen(
    onCategoryClick: (CategorySummary) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: CategoriesViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    when (val state = uiState) {
        is CategoriesUiState.Loading -> {
            CategoriesLoading(modifier = modifier)
        }
        is CategoriesUiState.Error -> {
            EmptyState(
                message = state.message,
                onActionClick = { viewModel.onEvent(CategoriesEvent.OnRetryClicked) },
                modifier = modifier.fillMaxSize()
            )
        }
        is CategoriesUiState.Empty -> {
            EmptyState(
                message = "Nenhuma categoria com transações encontrada para este mês.",
                onActionClick = { viewModel.onEvent(CategoriesEvent.OnRetryClicked) },
                actionLabel = "Recarregar",
                modifier = modifier.fillMaxSize()
            )
        }
        is CategoriesUiState.Success -> {
            CategoriesContent(
                state = state,
                formatCurrency = MoneyFormatter::format,
                onCategoryClick = { categorySummary ->
                    viewModel.onEvent(CategoriesEvent.OnCategoryClicked(categorySummary))
                    onCategoryClick(categorySummary)
                },
                modifier = modifier
            )
        }
    }
}

@Composable
private fun CategoriesLoading(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Preview(showBackground = true)
@Composable
private fun CategoriesScreenPreview() {
    FinancesTheme {
        val previewState = CategoriesUiState.Success(
            monthPeriod = MonthPeriod.now(),
            categories = listOf(
                CategorySummary(Category(1L, "Mercado", CategoryType.EXPENSE), Money.fromCents(125_075)),
                CategorySummary(Category(2L, "Água", CategoryType.EXPENSE), Money.fromCents(15_020))
            )
        )
        CategoriesContent(
            state = previewState,
            formatCurrency = MoneyFormatter::format,
            onCategoryClick = {}
        )
    }
}
