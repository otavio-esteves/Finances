package br.com.otavioesteves.finances.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.com.otavioesteves.finances.domain.model.Transaction
import br.com.otavioesteves.finances.domain.model.TransactionType
import br.com.otavioesteves.finances.ui.theme.categoryAccent
import br.com.otavioesteves.finances.utils.DateFormatter
import br.com.otavioesteves.finances.utils.MoneyFormatter

/**
 * Financial information stays in one readable column; secondary actions occupy only the header.
 * [uniformHeight] reserves two description lines at the normal font size. Larger fonts and
 * unusually long amounts can grow the card instead of clipping financial information.
 */
@Composable
fun TransactionListRow(
    transaction: Transaction,
    categoryName: String?,
    modifier: Modifier = Modifier,
    formattedAmount: String? = null,
    formattedDate: String? = null,
    uniformHeight: Boolean = false,
    trailing: @Composable RowScope.() -> Unit = {}
) {
    val amountText = formattedAmount ?: remember(transaction.amount.cents) { MoneyFormatter.format(transaction.amount) }
    val dateText = formattedDate ?: remember(transaction.date) { DateFormatter.format(transaction.date) }
    val amountMeaning = when (transaction.type) {
        TransactionType.INCOME -> "Receita"
        TransactionType.EXPENSE -> "Despesa"
        TransactionType.TRANSFER -> "Transferência"
    }
    val minimumHeight = 120.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)
    Row(
        modifier = modifier.fillMaxWidth()
            .then(if (uniformHeight) Modifier.heightIn(min = minimumHeight) else Modifier)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TransactionCategoryIcon(transaction, categoryName)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Text(
                    text = transaction.description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    minLines = if (uniformHeight) 2 else 1,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                trailing()
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = categoryName ?: "Sem categoria",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = dateText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            transaction.sourceLabel?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            AmountText(
                amount = amountText,
                isPositive = when (transaction.type) {
                    TransactionType.INCOME -> true
                    TransactionType.EXPENSE -> false
                    TransactionType.TRANSFER -> null
                },
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { contentDescription = "$amountMeaning: $amountText" }
            )
        }
    }
}

@Composable
private fun TransactionCategoryIcon(transaction: Transaction, categoryName: String?) {
    val iconColor = categoryAccent(transaction.categoryId)
    Icon(
        imageVector = remember(categoryName, transaction.type, transaction.description, transaction.iconKey) {
            transactionIcon(categoryName, transaction.type, transaction.description, transaction.iconKey)
        },
        contentDescription = null,
        tint = iconColor,
        modifier = Modifier.size(40.dp).clip(CircleShape).background(iconColor.copy(alpha = 0.14f)).padding(9.dp)
    )
}
