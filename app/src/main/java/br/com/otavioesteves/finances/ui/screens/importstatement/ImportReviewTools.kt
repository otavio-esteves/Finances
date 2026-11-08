package br.com.otavioesteves.finances.ui.screens.importstatement

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import br.com.otavioesteves.finances.domain.model.Category
import br.com.otavioesteves.finances.domain.model.CategorySuggestion
import br.com.otavioesteves.finances.domain.model.Money
import br.com.otavioesteves.finances.domain.model.Transaction
import br.com.otavioesteves.finances.domain.model.TransactionOrigin
import br.com.otavioesteves.finances.domain.model.TransactionType
import br.com.otavioesteves.finances.presentation.importstatement.ImportReviewFilter
import br.com.otavioesteves.finances.presentation.importstatement.ImportStatementUiState
import br.com.otavioesteves.finances.utils.DateFormatter
import br.com.otavioesteves.finances.utils.MoneyFormatter
import java.time.LocalDate

@Composable
internal fun ImportReviewTools(
    state: ImportStatementUiState.ReviewingSuggestions,
    onFilterSelected: (ImportReviewFilter) -> Unit,
    onSelectionModeChanged: (Boolean) -> Unit,
    onSelectVisibleEntries: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ImportReviewFilter.entries.forEach { filter ->
                FilterChip(
                    selected = state.filter == filter,
                    onClick = { onFilterSelected(filter) },
                    enabled = !state.isConfirming,
                    label = { Text("${filter.label} (${state.indicesFor(filter).size})") },
                    modifier = Modifier.testTag("review_filter_${filter.name}")
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { onSelectionModeChanged(!state.selectionMode) }, enabled = !state.isConfirming) {
                Text(if (state.selectionMode) "Cancelar seleção" else "Selecionar lançamentos")
            }
            if (state.selectionMode) TextButton(onClick = onSelectVisibleEntries, enabled = !state.isConfirming) {
                Text(if (state.selectedIndices.isNotEmpty() && state.visibleIndices.filterNot { it in state.excludedIndices }.toSet() == state.selectedIndices)
                    "Limpar seleção" else "Selecionar visíveis")
            }
        }
    }
}

@Composable
internal fun BulkCategoryDialog(
    categories: List<Category>,
    count: Int,
    onCategorySelected: (Category) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Categoria para $count lançamentos") },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                categories.forEach { category ->
                    TextButton(onClick = {
                        onCategorySelected(category)
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text(category.name)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
internal fun PotentialOverlapComparison(
    suggestion: CategorySuggestion,
    matches: List<Transaction>,
    categories: List<Category>
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Possível duplicata", style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.error)
        Text("Compare os registros. Compras iguais podem ser legítimas; exclua apenas se for o mesmo lançamento.",
            style = MaterialTheme.typography.bodySmall)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f).background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
                .padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Neste arquivo", style = MaterialTheme.typography.labelMedium)
                ComparisonDetails(suggestion.entry.description, suggestion.entry.date, suggestion.entry.amount,
                    suggestion.entry.type, suggestion.suggestedCategory?.name)
            }
            Column(Modifier.weight(1f).background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
                .padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("No histórico (${matches.size})", style = MaterialTheme.typography.labelMedium)
                matches.forEachIndexed { index, transaction ->
                    if (index > 0) HorizontalDivider()
                    ComparisonDetails(transaction.description, transaction.date, transaction.amount,
                        transaction.type, categories.firstOrNull { it.id == transaction.categoryId }?.name)
                    Text(transaction.sourceLabel ?: if (transaction.origin == TransactionOrigin.MANUAL)
                        "Lançamento manual" else "Importado", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun ComparisonDetails(description: String, date: LocalDate, amount: Money, type: TransactionType, category: String?) {
    Text(description, style = MaterialTheme.typography.bodyMedium)
    Text(DateFormatter.format(date), style = MaterialTheme.typography.bodySmall)
    Text(MoneyFormatter.format(amount), style = MaterialTheme.typography.titleSmall)
    Text(when (type) {
        TransactionType.EXPENSE -> "Despesa"
        TransactionType.INCOME -> "Receita"
        TransactionType.TRANSFER -> "Transferência"
    }, style = MaterialTheme.typography.bodySmall)
    category?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
}
