package br.com.otavioesteves.finances.ui.screens.categories

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.otavioesteves.finances.domain.model.Category
import br.com.otavioesteves.finances.domain.model.CategorySummary
import br.com.otavioesteves.finances.domain.model.MonthPeriod
import br.com.otavioesteves.finances.domain.model.Money
import br.com.otavioesteves.finances.ui.state.CategoriesUiState
import br.com.otavioesteves.finances.ui.theme.FinancesTheme
import br.com.otavioesteves.finances.utils.formatCurrency
import br.com.otavioesteves.finances.viewmodels.CategoriesViewModel

@Composable
fun CategoriesScreen(
    modifier: Modifier = Modifier,
    viewModel: CategoriesViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    when (val state = uiState) {
        is CategoriesUiState.Loading -> {
            CategoriesLoading(modifier = modifier)
        }
        is CategoriesUiState.Error -> {
            CategoriesError(
                message = state.message,
                modifier = modifier
            )
        }
        is CategoriesUiState.Success -> {
            CategoriesContent(
                state = state,
                formatCurrency = ::formatCurrency,
                onCategoryClick = { },
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
        CircularProgressIndicator()
    }
}

@Composable
private fun CategoriesError(
    message: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun CategoriesScreenPreview() {
    FinancesTheme {
        val previewState = CategoriesUiState.Success(
            monthPeriod = MonthPeriod(year = 2026, month = 1),
            categories = listOf(
                CategorySummary(Category(1, "Mercado"), Money.fromCents(125_075)),
                CategorySummary(Category(2, "Água"), Money.fromCents(15_020))
            )
        )
        CategoriesContent(
            state = previewState,
            formatCurrency = ::formatCurrency,
            onCategoryClick = {}
        )
    }
}
