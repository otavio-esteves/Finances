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
import androidx.compose.ui.text.style.TextOverflow
import br.com.otavioesteves.finances.domain.model.CategoryType
import br.com.otavioesteves.finances.domain.model.TransactionType
import br.com.otavioesteves.finances.ui.theme.categoryAccent
import androidx.compose.ui.graphics.lerp

@Composable
fun CategorySummaryRow(
    categoryName: String,
    totalAmountFormatted: String,
    categoryType: CategoryType,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    categoryId: Long = 0
) {
    val accent = categoryAccent(categoryId)
    FinanceCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        containerColor = lerp(MaterialTheme.colorScheme.surface, accent, 0.07f),
        borderColor = accent.copy(alpha = 0.28f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(accent.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                Icon(
                    transactionIcon(categoryName, TransactionType.valueOf(categoryType.name)),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = accent
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(categoryName, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    when (categoryType) {
                        CategoryType.INCOME -> "Receita"
                        CategoryType.EXPENSE -> "Despesa"
                        CategoryType.TRANSFER -> "Transferência"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (categoryType == CategoryType.TRANSFER) Text(
                    "Fora do saldo", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                ) else AmountText(
                    amount = totalAmountFormatted,
                    isPositive = when (categoryType) {
                        CategoryType.INCOME -> true
                        CategoryType.EXPENSE -> false
                        CategoryType.TRANSFER -> null
                    },
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
