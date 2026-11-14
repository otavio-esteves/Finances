package br.com.otavioesteves.finances.ui.screens.importstatement

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import br.com.otavioesteves.finances.domain.model.Category
import br.com.otavioesteves.finances.domain.model.TransactionType
import br.com.otavioesteves.finances.presentation.importstatement.ImportReviewFilter
import br.com.otavioesteves.finances.presentation.importstatement.ImportStatementUiState
import br.com.otavioesteves.finances.ui.components.AmountText
import br.com.otavioesteves.finances.ui.components.PrimaryActionButton
import br.com.otavioesteves.finances.utils.MoneyFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReviewContent(
    state: ImportStatementUiState.ReviewingSuggestions,
    onCategorySelected: (Int, Category) -> Unit,
    onTypeSelected: (Int, TransactionType) -> Unit,
    onOverlapAcknowledged: (Boolean) -> Unit,
    onOcrAcknowledged: (Boolean) -> Unit,
    onEntryEdited: (Int, String, String, String) -> String?,
    onSourceLabelChanged: (String) -> Unit,
    onExpectedTotalChanged: (String) -> Unit,
    onDifferenceAcknowledged: (Boolean) -> Unit,
    onFilterSelected: (ImportReviewFilter) -> Unit,
    onSelectionModeChanged: (Boolean) -> Unit,
    onEntrySelectionChanged: (Int, Boolean) -> Unit,
    onSelectVisibleEntries: () -> Unit,
    onBulkCategorySelected: (Category) -> Unit,
    onEntryExcluded: (Int, Boolean) -> Unit,
    onConfirmClick: () -> Unit,
    onCancelClick: () -> Unit,
    onScrollTopChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    var showBulkCategories by remember { mutableStateOf(false) }
    val isAtTop by remember {
        derivedStateOf { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0 }
    }
    LaunchedEffect(isAtTop) { onScrollTopChange(isAtTop) }
    LaunchedEffect(state.selectionMode) { if (!state.selectionMode) showBulkCategories = false }
    if (showBulkCategories && state.selectionMode && state.bulkCategories.isNotEmpty()) {
        BulkCategoryDialog(state.bulkCategories, state.selectedIndices.size, onCategorySelected = {
            onBulkCategorySelected(it)
            showBulkCategories = false
        }, onDismiss = { showBulkCategories = false })
    }
    Column(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "review_summary") {
                Column(modifier = Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Confira os lançamentos",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = state.fileName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val expenses = state.expenseTotal
                    state.referenceYear?.let { year ->
                        Text("Ano informado para datas sem ano: $year. Confira as datas abaixo; o período explícito do PDF tem prioridade.",
                            style = MaterialTheme.typography.bodySmall)
                    }
                    val incomes = state.incomeTotal
                    Text("${state.includedSuggestions.size} para importar · ${state.excludedIndices.size} excluídos",
                        style = MaterialTheme.typography.bodyMedium)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("Despesas", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            AmountText(expenses?.let(MoneyFormatter::format) ?: "Total indisponível", isPositive = false, style = MaterialTheme.typography.titleSmall)
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Receitas", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            AmountText(incomes?.let(MoneyFormatter::format) ?: "Total indisponível", isPositive = true, style = MaterialTheme.typography.titleSmall)
                        }
                    }
                    state.amountError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (state.potentialOverlapCount > 0) TextButton(
                        onClick = { onFilterSelected(ImportReviewFilter.OVERLAPS) }, enabled = !state.isConfirming
                    ) { Text("Conferir ${state.potentialOverlapCount} possíveis duplicatas") }
                }
            }
            item(key = "review_tools") {
                ImportReviewTools(state, onFilterSelected, onSelectionModeChanged, onSelectVisibleEntries)
            }
            item {
                ImportReviewChecks(state, onSourceLabelChanged, onExpectedTotalChanged, onDifferenceAcknowledged)
            }
            if (state.visibleIndices.isEmpty()) item {
                Text("Nenhum lançamento neste filtro.", modifier = Modifier.padding(vertical = 16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(state.visibleIndices, key = { it }) { index ->
                val suggestion = state.suggestions[index]
                SuggestionRow(
                    suggestion = suggestion,
                    availableCategories = state.availableCategories,
                    enabled = !state.isConfirming,
                    excluded = index in state.excludedIndices,
                    selectionMode = state.selectionMode,
                    selected = index in state.selectedIndices,
                    onSelected = { onEntrySelectionChanged(index, it) },
                    onExcluded = { onEntryExcluded(index, it) },
                    matches = state.overlapMatches[index].orEmpty(),
                    modifier = Modifier.testTag("review_entry_$index"),
                    onCategorySelected = { category -> onCategorySelected(index, category) },
                    onEntryEdited = { description, amount, date -> onEntryEdited(index, description, amount, date) },
                    onTypeSelected = { type -> onTypeSelected(index, type) }
                )
            }
            item(key = "review_acknowledgments") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (state.requiresOcrReview) {
                        Text("Este PDF passou por reconhecimento de imagem. Confira datas, valores e descrições; use Corrigir dados em qualquer lançamento.", style = MaterialTheme.typography.bodySmall)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = state.ocrAcknowledged, onCheckedChange = onOcrAcknowledged, enabled = !state.isConfirming,
                                modifier = Modifier.testTag("review_ocr_acknowledgment"))
                            Text("Conferi os dados reconhecidos", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    state.validationError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (state.includedSuggestions.isEmpty()) {
                        Text("Reinclua pelo menos um lançamento para importar.", style = MaterialTheme.typography.bodySmall)
                    }
                    if (state.potentialOverlapCount > 0) {
                        Text(
                            "${state.potentialOverlapCount} lançamento(s) parecem já existir no histórico. Confira antes de continuar.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = state.overlapAcknowledged, onCheckedChange = onOverlapAcknowledged, enabled = !state.isConfirming,
                                modifier = Modifier.testTag("review_overlap_acknowledgment"))
                            Text("Revisei os possíveis lançamentos repetidos")
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.isConfirming || state.isCheckingOverlaps) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(if (state.isConfirming) "Salvando seus lançamentos…" else "Conferindo possíveis repetições…",
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
            if (state.selectionMode) {
                Text(if (state.selectedIndices.isNotEmpty() && state.bulkCategories.isEmpty())
                    "Selecione lançamentos do mesmo tipo para categorizar em lote."
                    else "${state.selectedIndices.size} selecionados", style = MaterialTheme.typography.bodySmall)
                PrimaryActionButton(
                    text = "Categorizar selecionados", onClick = { showBulkCategories = true },
                    enabled = !state.isConfirming && state.selectedIndices.isNotEmpty() && state.bulkCategories.isNotEmpty()
                )
                TextButton(onClick = { onSelectionModeChanged(false) }, enabled = !state.isConfirming) { Text("Encerrar seleção") }
            } else {
                PrimaryActionButton(
                    text = if (state.isConfirming) "Confirmando..." else "Confirmar Importação",
                    onClick = onConfirmClick,
                    enabled = state.canConfirm
                )
                TextButton(onClick = onCancelClick, enabled = !state.isConfirming) {
                    Text("Cancelar")
                }
            }
        }
    }
}
