package br.com.otavioesteves.finances.ui.screens.categories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import br.com.otavioesteves.finances.domain.model.CategorySummary
import br.com.otavioesteves.finances.domain.model.Money
import br.com.otavioesteves.finances.presentation.categories.CategoriesUiState
import br.com.otavioesteves.finances.ui.components.CategorySummaryRow
import br.com.otavioesteves.finances.ui.components.SectionTitle
import br.com.otavioesteves.finances.utils.formatMonthPeriod

@Composable
fun CategoriesContent(
    state: CategoriesUiState.Success,
    formatCurrency: (Money) -> String,
    onCategoryClick: (CategorySummary) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Column {
            Text(
                text = "Categorias",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = formatMonthPeriod(state.monthPeriod),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        SectionTitle(
            title = "Resumo por categoria",
            modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
        )

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(state.categories) { categorySummary ->
                CategorySummaryRow(
                    categoryName = categorySummary.category.name,
                    totalAmountFormatted = formatCurrency(categorySummary.totalAmount),
                    categoryType = categorySummary.category.type,
                    onClick = { onCategoryClick(categorySummary) }
                )
            }
        }
    }
}
