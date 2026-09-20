package br.com.otavioesteves.finances.ui.screens.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.otavioesteves.finances.domain.model.Category
import br.com.otavioesteves.finances.domain.model.CategorySummary
import br.com.otavioesteves.finances.domain.model.CategoryType
import br.com.otavioesteves.finances.domain.model.MonthPeriod
import br.com.otavioesteves.finances.domain.model.Money
import br.com.otavioesteves.finances.domain.model.Transaction
import br.com.otavioesteves.finances.domain.model.TransactionType
import br.com.otavioesteves.finances.presentation.AppViewModelProvider
import br.com.otavioesteves.finances.presentation.dashboard.DashboardUiState
import br.com.otavioesteves.finances.presentation.dashboard.DashboardViewModel
import br.com.otavioesteves.finances.presentation.transactions.TransactionItem
import br.com.otavioesteves.finances.ui.components.CategoryUsageChart
import br.com.otavioesteves.finances.ui.components.EmptyState
import br.com.otavioesteves.finances.ui.components.FinanceCard
import br.com.otavioesteves.finances.ui.components.MonthPeriodSelector
import br.com.otavioesteves.finances.ui.components.TransactionListRow
import br.com.otavioesteves.finances.ui.theme.AppAccent
import br.com.otavioesteves.finances.ui.theme.AppAccentForeground
import br.com.otavioesteves.finances.ui.theme.FinancesTheme
import br.com.otavioesteves.finances.utils.MoneyFormatter
import java.time.LocalDate

@Composable
fun DashboardScreen(
    onAddTransactionClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onImportStatementClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    DashboardContent(
        state = uiState,
        onAddTransactionClick = onAddTransactionClick,
        onHistoryClick = onHistoryClick,
        onImportStatementClick = onImportStatementClick,
        onPreviousMonthClick = { viewModel.onMonthSelected(uiState.monthPeriod.previousMonth()) },
        onNextMonthClick = { viewModel.onMonthSelected(uiState.monthPeriod.nextMonth()) },
        modifier = modifier
    )
}

@Composable
private fun DashboardContent(
    state: DashboardUiState,
    onAddTransactionClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onImportStatementClick: () -> Unit,
    onPreviousMonthClick: () -> Unit,
    onNextMonthClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddTransactionClick,
                containerColor = AppAccent,
                contentColor = AppAccentForeground
            ) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = "Adicionar transação")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                MonthPeriodSelector(
                    monthPeriod = state.monthPeriod,
                    onPreviousClick = onPreviousMonthClick,
                    onNextClick = onNextMonthClick,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onImportStatementClick) {
                    Icon(
                        imageVector = Icons.Filled.UploadFile,
                        contentDescription = "Importar extrato",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = "Saldo do mês: ${MoneyFormatter.format(state.monthlyBalance)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            FinanceCard(modifier = Modifier.fillMaxWidth()) {
                CategoryUsageChart(
                    summaries = state.categorySummaries,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                )
            }

            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Transações",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    TextButton(onClick = onHistoryClick) {
                        Text(
                            text = "Ver todas",
                            style = MaterialTheme.typography.labelLarge,
                            color = AppAccent
                        )
                    }
                }

                if (state.recentTransactions.isEmpty()) {
                    EmptyState(message = "Nenhuma transação neste mês ainda.")
                } else {
                    Column {
                        state.recentTransactions.forEach { item ->
                            TransactionListRow(
                                transaction = item.transaction,
                                categoryName = item.category?.name
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DashboardScreenPreview() {
    FinancesTheme {
        val mercado = Category(id = 1, name = "Mercado", type = CategoryType.EXPENSE)
        val transporte = Category(id = 2, name = "Transporte", type = CategoryType.EXPENSE)
        val lazer = Category(id = 3, name = "Lazer", type = CategoryType.EXPENSE)
        val salario = Category(id = 4, name = "Salário", type = CategoryType.INCOME)

        DashboardContent(
            state = DashboardUiState(
                monthPeriod = MonthPeriod.now(),
                monthlyBalance = Money.fromCents(511_560),
                categorySummaries = listOf(
                    CategorySummary(category = mercado, totalAmount = Money.fromCents(58_000)),
                    CategorySummary(category = transporte, totalAmount = Money.fromCents(32_000)),
                    CategorySummary(category = lazer, totalAmount = Money.fromCents(28_440)),
                    CategorySummary(category = salario, totalAmount = Money.fromCents(650_000))
                ),
                recentTransactions = listOf(
                    TransactionItem(
                        transaction = Transaction(
                            id = 1,
                            description = "Supermercado Extra",
                            amount = Money.fromCents(12_345),
                            categoryId = 1,
                            date = LocalDate.now(),
                            type = TransactionType.EXPENSE
                        ),
                        category = mercado
                    ),
                    TransactionItem(
                        transaction = Transaction(
                            id = 2,
                            description = "Salário",
                            amount = Money.fromCents(500_000),
                            categoryId = 4,
                            date = LocalDate.now().minusDays(9),
                            type = TransactionType.INCOME
                        ),
                        category = salario
                    )
                )
            ),
            onAddTransactionClick = {},
            onHistoryClick = {},
            onImportStatementClick = {},
            onPreviousMonthClick = {},
            onNextMonthClick = {}
        )
    }
}
