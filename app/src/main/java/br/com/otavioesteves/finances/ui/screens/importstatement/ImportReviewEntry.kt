package br.com.otavioesteves.finances.ui.screens.importstatement

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import br.com.otavioesteves.finances.domain.model.Category
import br.com.otavioesteves.finances.domain.model.CategorySuggestion
import br.com.otavioesteves.finances.domain.model.Transaction
import br.com.otavioesteves.finances.domain.model.TransactionType
import br.com.otavioesteves.finances.ui.components.AmountText
import br.com.otavioesteves.finances.ui.components.FinanceCard
import br.com.otavioesteves.finances.utils.DateFormatter
import br.com.otavioesteves.finances.utils.MoneyFormatter
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SuggestionRow(
    suggestion: CategorySuggestion,
    availableCategories: List<Category>,
    onCategorySelected: (Category) -> Unit,
    onTypeSelected: (TransactionType) -> Unit,
    onEntryEdited: (String, String, String) -> String?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    excluded: Boolean = false,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onSelected: (Boolean) -> Unit = {},
    onExcluded: (Boolean) -> Unit = {},
    matches: List<Transaction> = emptyList()
) {
    var showEditor by remember { mutableStateOf(false) }
    if (showEditor) EntryEditor(suggestion, onEntryEdited, onDismiss = { showEditor = false })
    FinanceCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (selectionMode && !excluded) {
                    Checkbox(checked = selected, onCheckedChange = onSelected, enabled = enabled,
                        modifier = Modifier.semantics {
                            contentDescription = "Selecionar ${suggestion.entry.description}"
                        })
                }
                Text(if (excluded) "Excluído da importação" else "Neste arquivo",
                    modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                TextButton(onClick = { onExcluded(!excluded) }, enabled = enabled) {
                    Text(if (excluded) "Reincluir" else "Excluir")
                }
            }
            if (suggestion.source == br.com.otavioesteves.finances.domain.model.SuggestionSource.MEMORY) {
                Text("Categoria lembrada da sua correção", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary)
            }
            if (!excluded) TextButton(onClick = { showEditor = true }, enabled = enabled) { Text("Corrigir dados") }
            if (matches.isNotEmpty() && !excluded) {
                PotentialOverlapComparison(suggestion, matches, availableCategories)
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = suggestion.entry.description,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = DateFormatter.format(suggestion.entry.date),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    AmountText(
                        amount = MoneyFormatter.format(suggestion.entry.amount),
                        isPositive = suggestion.entry.type == TransactionType.INCOME,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

            if (!excluded) {
                if (suggestion.confidence < 0.75f) {
                    Text("Sugestão incerta · confira a categoria", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                var typeExpanded by remember { mutableStateOf(false) }
                Box {
                    TextButton(onClick = { typeExpanded = true }, enabled = enabled) {
                        Text(
                            when (suggestion.entry.type) {
                                TransactionType.EXPENSE -> "Tipo: Despesa"
                                TransactionType.INCOME -> "Tipo: Receita"
                                TransactionType.TRANSFER -> "Tipo: Pagamento de fatura / transferência"
                            }
                        )
                    }
                    DropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                        listOf(
                            TransactionType.EXPENSE to "Despesa",
                            TransactionType.INCOME to "Receita",
                            TransactionType.TRANSFER to "Pagamento de fatura / transferência"
                        ).filter { (type, _) -> availableCategories.any { it.type.name == type.name } }
                            .forEach { (type, label) ->
                                DropdownMenuItem(text = { Text(label) }, enabled = enabled, onClick = {
                                    onTypeSelected(type)
                                    typeExpanded = false
                                })
                            }
                    }
                }

                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { if (enabled) expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = suggestion.suggestedCategory
                            ?.takeIf { it.type.name == suggestion.entry.type.name }
                            ?.name ?: "Selecione uma categoria",
                        onValueChange = {},
                        readOnly = true,
                        enabled = enabled,
                        label = { Text("Categoria") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        isError = suggestion.suggestedCategory?.type?.name != suggestion.entry.type.name,
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        shape = MaterialTheme.shapes.small
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        availableCategories.filter { it.type.name == suggestion.entry.type.name }.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category.name) },
                                enabled = enabled,
                                onClick = {
                                    onCategorySelected(category)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EntryEditor(suggestion: CategorySuggestion, onSave: (String, String, String) -> String?, onDismiss: () -> Unit) {
    var description by remember { mutableStateOf(suggestion.entry.description) }
    var amount by remember { mutableStateOf(BigDecimal.valueOf(suggestion.entry.amount.cents, 2).toPlainString().replace('.', ',')) }
    var date by remember { mutableStateOf(DateFormatter.format(suggestion.entry.date)) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Corrigir lançamento") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Descrição") })
            OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("Valor sem sinal") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            OutlinedTextField(value = date, onValueChange = { date = it }, label = { Text("Data (dd/MM/aaaa)") }, singleLine = true)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = {
        TextButton(onClick = {
            error = onSave(description, amount, date)
            if (error == null) onDismiss()
        }) { Text("Aplicar") }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}
