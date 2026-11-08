package br.com.otavioesteves.finances.ui.screens.importstatement

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import br.com.otavioesteves.finances.presentation.importstatement.ImportStatementUiState
import br.com.otavioesteves.finances.utils.MoneyFormatter

@Composable
internal fun ImportReviewChecks(
    state: ImportStatementUiState.ReviewingSuggestions,
    onSourceChanged: (String) -> Unit,
    onTotalChanged: (String) -> Unit,
    onDifferenceAcknowledged: (Boolean) -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = { expanded = !expanded }) {
            Text(if (expanded) "Ocultar conferência" else "Identificar conta e conferir total")
        }
        if (expanded) {
        OutlinedTextField(value = state.sourceLabel, onValueChange = onSourceChanged,
            label = { Text("Conta ou cartão (opcional)") }, placeholder = { Text("Ex.: Nubank · cartão pessoal") },
            enabled = !state.isConfirming, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = state.expectedExpenseTotal, onValueChange = onTotalChanged,
            label = { Text("Total de despesas no documento (opcional)") },
            supportingText = { Text("Compare com as despesas que serão importadas; itens excluídos ficam fora da soma. Não use o saldo nem inclua pagamentos de fatura ou transferências.") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            isError = state.expectedExpenseTotal.isNotBlank() && !state.expectedTotalValid,
            enabled = !state.isConfirming, singleLine = true, modifier = Modifier.fillMaxWidth())
        state.expenseDifference?.let { difference ->
            Text(if (difference.isZero()) "Total de despesas conferido."
                else "Diferença (lançamentos menos documento): ${MoneyFormatter.format(difference)}",
                color = if (difference.isZero()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium)
            if (!difference.isZero()) Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = state.differenceAcknowledged, onCheckedChange = onDifferenceAcknowledged,
                    enabled = !state.isConfirming)
                Text("Conferi a diferença e quero continuar", style = MaterialTheme.typography.bodySmall)
            }
        }
        }
        Text("Pagamentos de fatura devem ser Transferência para não contar as compras duas vezes. Confira o tipo antes de salvar.",
            style = MaterialTheme.typography.bodySmall)
    }
}
