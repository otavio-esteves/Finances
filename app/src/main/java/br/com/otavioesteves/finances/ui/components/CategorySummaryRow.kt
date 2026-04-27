package br.com.otavioesteves.finances.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import br.com.otavioesteves.finances.domain.model.CategoryType

@Composable
fun CategorySummaryRow(
    categoryName: String,
    totalAmountFormatted: String,
    categoryType: CategoryType,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FinanceCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = categoryName,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            AmountText(
                amount = totalAmountFormatted,
                isPositive = categoryType == CategoryType.INCOME,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}
