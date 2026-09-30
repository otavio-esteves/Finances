package br.com.otavioesteves.finances.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.SouthWest
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import br.com.otavioesteves.finances.domain.model.CategoryType
import br.com.otavioesteves.finances.ui.theme.FinancesThemeTokens

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
                .padding(horizontal = 18.dp, vertical = 17.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(FinancesThemeTokens.colors.heroSurface), contentAlignment = Alignment.Center) {
                Icon(
                    if (categoryType == CategoryType.INCOME) Icons.Filled.SouthWest else Icons.Filled.NorthEast,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(categoryName, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    if (categoryType == CategoryType.INCOME) "Receita" else "Despesa",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                AmountText(
                    amount = totalAmountFormatted,
                    isPositive = categoryType == CategoryType.INCOME,
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
