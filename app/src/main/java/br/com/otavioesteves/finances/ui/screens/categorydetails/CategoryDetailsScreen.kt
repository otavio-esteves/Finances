package br.com.otavioesteves.finances.ui.screens.categorydetails

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.lerp
import br.com.otavioesteves.finances.ui.theme.categoryAccent
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.otavioesteves.finances.domain.model.CategoryType
import br.com.otavioesteves.finances.domain.model.Transaction
import br.com.otavioesteves.finances.presentation.AppViewModelProvider
import br.com.otavioesteves.finances.presentation.categories.CategoryDetailsViewModel
import br.com.otavioesteves.finances.ui.components.AmountText
import br.com.otavioesteves.finances.ui.components.EmptyState
import br.com.otavioesteves.finances.ui.components.FinanceCard
import br.com.otavioesteves.finances.ui.components.FinanceGlassIconButton
import br.com.otavioesteves.finances.ui.components.TransactionListRow
import br.com.otavioesteves.finances.ui.components.financeTopAppBarColors
import br.com.otavioesteves.finances.utils.MoneyFormatter
import br.com.otavioesteves.finances.utils.formatMonthPeriod

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryDetailsScreen(
    categoryId: Long,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    onTransactionClick: (Transaction) -> Unit = {},
    viewModel: CategoryDetailsViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val accent = categoryAccent(categoryId)
    val listState = rememberLazyListState()
    val isAtTop by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0 } }
    Scaffold(
        modifier = modifier.testTag("category_details_$categoryId"),
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(state.category?.name ?: "Categoria", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                colors = financeTopAppBarColors(isAtTop),
                navigationIcon = {
                    FinanceGlassIconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(
                start = 20.dp, end = 20.dp,
                top = padding.calculateTopPadding() + 16.dp,
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp
            )
        ) {
            item {
                Text("${formatMonthPeriod(state.period)} ${state.period.year}",
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            when {
                state.isLoading -> item {
                    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
                state.error != null -> item {
                    EmptyState(requireNotNull(state.error), onActionClick = onBackClick, actionLabel = "Voltar às categorias")
                }
                state.category == null -> item { EmptyState("Categoria não encontrada.") }
                else -> {
                    val category = requireNotNull(state.category)
                    item {
                        FinanceCard(Modifier.fillMaxWidth(),
                            containerColor = lerp(MaterialTheme.colorScheme.surface, accent, 0.07f),
                            borderColor = accent.copy(alpha = 0.28f)) {
                            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(category.name, style = MaterialTheme.typography.titleLarge)
                                Text(when (category.type) {
                                    CategoryType.EXPENSE -> "Despesas do mês"
                                    CategoryType.INCOME -> "Receitas do mês"
                                    CategoryType.TRANSFER -> "Transferências do mês"
                                }, style = MaterialTheme.typography.titleMedium)
                                AmountText(MoneyFormatter.format(state.total), isPositive = when (category.type) {
                                    CategoryType.EXPENSE -> false
                                    CategoryType.INCOME -> true
                                    CategoryType.TRANSFER -> null
                                }, modifier = Modifier.testTag("category_details_total"))
                                Text("${state.transactions.size} lançamento(s)", style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (category.type == CategoryType.TRANSFER) Text("Transferências não alteram o saldo.",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    if (state.transactions.isEmpty()) item { EmptyState("Nenhum lançamento nesta categoria e neste mês.") }
                    items(state.transactions, key = { it.id }, contentType = { "transaction" }) { transaction ->
                        FinanceCard(Modifier.fillMaxWidth().clickable { onTransactionClick(transaction) }) {
                            TransactionListRow(transaction, category.name, uniformHeight = true,
                                modifier = Modifier.padding(horizontal = 16.dp))
                        }
                    }
                }
            }
        }
    }
}
