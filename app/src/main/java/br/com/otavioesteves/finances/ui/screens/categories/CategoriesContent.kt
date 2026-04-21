package br.com.otavioesteves.finances.ui.screens.categories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import br.com.otavioesteves.finances.domain.model.CategorySummary
import br.com.otavioesteves.finances.domain.model.Money
import br.com.otavioesteves.finances.ui.components.CategorySummaryRow
import br.com.otavioesteves.finances.ui.state.CategoriesUiState
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
            .padding(16.dp)
    ) {
        Text(
            text = formatMonthPeriod(state.monthPeriod),
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (state.categories.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("Nenhuma despesa encontrada.")
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.categories) { category ->
                    CategorySummaryRow(
                        categoryName = category.category.name,
                        totalAmountFormatted = formatCurrency(category.totalAmount),
                        onClick = { onCategoryClick(category) }
                    )
                }
            }
        }
    }
}
