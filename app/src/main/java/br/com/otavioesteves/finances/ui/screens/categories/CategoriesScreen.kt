package br.com.otavioesteves.finances.ui.screens.categories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.otavioesteves.finances.domain.model.MonthPeriod
import br.com.otavioesteves.finances.domain.model.CategorySummary
import br.com.otavioesteves.finances.domain.model.CategoryType
import br.com.otavioesteves.finances.presentation.AppViewModelProvider
import br.com.otavioesteves.finances.presentation.categories.CategoriesEvent
import br.com.otavioesteves.finances.presentation.categories.CategoriesUiState
import br.com.otavioesteves.finances.presentation.categories.CategoriesViewModel
import br.com.otavioesteves.finances.ui.components.CategorySummaryRow
import br.com.otavioesteves.finances.ui.components.EmptyState
import br.com.otavioesteves.finances.ui.components.MonthPeriodSelector
import br.com.otavioesteves.finances.ui.components.financeTopAppBarColors
import br.com.otavioesteves.finances.utils.MoneyFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CategoriesScreen(
    onCategoryClick: (CategorySummary, MonthPeriod) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
    viewModel: CategoriesViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val loadedState by viewModel.uiState.collectAsStateWithLifecycle()
    val monthPeriod by viewModel.selectedMonthPeriod.collectAsStateWithLifecycle()
    val loadedPeriod = when (val value = loadedState) {
        is CategoriesUiState.Success -> value.monthPeriod
        is CategoriesUiState.Empty -> value.monthPeriod
        is CategoriesUiState.Error -> value.monthPeriod
        CategoriesUiState.Loading -> null
    }
    val uiState = if (loadedPeriod == monthPeriod) loadedState else CategoriesUiState.Loading
    val creation by viewModel.creationState.collectAsStateWithLifecycle()
    if (creation.isOpen) {
        var categoryName by rememberSaveable { mutableStateOf("") }
        var categoryType by rememberSaveable { mutableStateOf(CategoryType.EXPENSE) }
        AlertDialog(
            onDismissRequest = viewModel::dismissCreateCategory,
            title = { Text("Criar categoria") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = categoryName,
                        onValueChange = { categoryName = it },
                        label = { Text("Nome da categoria") },
                        singleLine = true,
                        enabled = !creation.isSaving,
                        isError = creation.error != null,
                        modifier = Modifier.fillMaxWidth()
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        CategoryType.entries.forEach { type ->
                            FilterChip(
                                selected = categoryType == type,
                                onClick = { categoryType = type },
                                enabled = !creation.isSaving,
                                label = { Text(when (type) {
                                    CategoryType.EXPENSE -> "Despesa"
                                    CategoryType.INCOME -> "Receita"
                                    CategoryType.TRANSFER -> "Transf."
                                }) }
                            )
                        }
                    }
                    creation.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.createCategory(categoryName, categoryType) }, enabled = !creation.isSaving) {
                    Text(if (creation.isSaving) "Salvando…" else "Criar")
                }
            },
            dismissButton = { TextButton(onClick = viewModel::dismissCreateCategory, enabled = !creation.isSaving) { Text("Cancelar") } }
        )
    }
    val listState = rememberLazyListState()
    val isAtTop by remember {
        derivedStateOf { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0 }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 64.dp, bottom = 128.dp)
        ) {
            item(key = "month_selector") {
                MonthPeriodSelector(
                    monthPeriod = monthPeriod,
                    onPreviousClick = { viewModel.onEvent(CategoriesEvent.OnMonthChanged(monthPeriod.previousMonth())) },
                    onNextClick = { viewModel.onEvent(CategoriesEvent.OnMonthChanged(monthPeriod.nextMonth())) },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 20.dp)
                )
            }
            item(key = "create_category") {
                Button(onClick = viewModel::showCreateCategory, modifier = Modifier.fillMaxWidth()) {
                    Text("Criar categoria")
                }
            }
            when (val state = uiState) {
                CategoriesUiState.Loading -> item(key = "loading") {
                    Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
                is CategoriesUiState.Error -> item(key = "error") {
                    EmptyState(
                        message = state.message,
                        onActionClick = { viewModel.onEvent(CategoriesEvent.OnRetryClicked) }
                    )
                }
                is CategoriesUiState.Empty -> item(key = "empty") {
                    EmptyState(message = "Nenhuma categoria cadastrada. Crie sua primeira categoria.")
                }
                is CategoriesUiState.Success -> items(
                    items = state.categories,
                    key = { it.category.id },
                    contentType = { "category" }
                ) { summary ->
                    CategorySummaryRow(
                        categoryName = summary.category.name,
                        categoryId = summary.category.id,
                        totalAmountFormatted = remember(summary.totalAmount.cents) { MoneyFormatter.format(summary.totalAmount) },
                        categoryType = summary.category.type,
                        onClick = {
                            viewModel.onEvent(CategoriesEvent.OnCategoryClicked(summary))
                            onCategoryClick(summary, monthPeriod)
                        }
                    )
                }
            }
        }
        CenterAlignedTopAppBar(
            title = { Text("Categorias") },
            colors = financeTopAppBarColors(isAtTop),
            windowInsets = WindowInsets(0, 0, 0, 0),
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}
