package br.com.otavioesteves.finances.ui.screens.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import br.com.otavioesteves.finances.presentation.AppViewModelProvider
import br.com.otavioesteves.finances.presentation.dashboard.DashboardUiState
import br.com.otavioesteves.finances.presentation.dashboard.DashboardViewModel
import br.com.otavioesteves.finances.ui.components.AmountText
import br.com.otavioesteves.finances.ui.components.CategoryUsageChart
import br.com.otavioesteves.finances.ui.components.FinanceCard
import br.com.otavioesteves.finances.ui.components.PrimaryActionButton
import br.com.otavioesteves.finances.ui.components.SectionTitle
import br.com.otavioesteves.finances.ui.theme.FinancesTheme
import br.com.otavioesteves.finances.utils.MoneyFormatter
import br.com.otavioesteves.finances.utils.formatMonthPeriod

@Composable
fun DashboardScreen(
    onCategoriesClick: () -> Unit,
    onAddTransactionClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onImportStatementClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    DashboardContent(
        state = uiState,
        onCategoriesClick = onCategoriesClick,
        onAddTransactionClick = onAddTransactionClick,
        onHistoryClick = onHistoryClick,
        onSettingsClick = onSettingsClick,
        onImportStatementClick = onImportStatementClick,
        modifier = modifier
    )
}

@Composable
private fun DashboardContent(
    state: DashboardUiState,
    onCategoriesClick: () -> Unit,
    onAddTransactionClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onImportStatementClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Column {
            Text(
                text = "Dashboard",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = formatMonthPeriod(state.monthPeriod),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        FinanceCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Saldo do mês",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                AmountText(
                    amount = MoneyFormatter.format(state.monthlyBalance),
                    style = MaterialTheme.typography.headlineMedium,
                    isPositive = state.monthlyBalance.cents >= 0
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            FinanceCard(
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Receitas",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    AmountText(
                        amount = MoneyFormatter.format(state.totalIncome),
                        style = MaterialTheme.typography.titleLarge,
                        isPositive = true
                    )
                }
            }
            FinanceCard(
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Despesas",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    AmountText(
                        amount = MoneyFormatter.format(state.totalExpenses),
                        style = MaterialTheme.typography.titleLarge,
                        isPositive = false
                    )
                }
            }
        }

        Column {
            SectionTitle(title = "Gastos por Categoria")
            FinanceCard(modifier = Modifier.fillMaxWidth()) {
                CategoryUsageChart(
                    summaries = state.categorySummaries,
                    modifier = Modifier.padding(20.dp)
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle(title = "Ações")
            PrimaryActionButton(
                text = "Adicionar Transação",
                onClick = onAddTransactionClick
            )
            PrimaryActionButton(
                text = "Importar Extrato",
                onClick = onImportStatementClick,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
            PrimaryActionButton(
                text = "Ver Histórico",
                onClick = onHistoryClick,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
            PrimaryActionButton(
                text = "Ver Categorias",
                onClick = onCategoriesClick,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurface
            )
            PrimaryActionButton(
                text = "Configurações",
                onClick = onSettingsClick,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DashboardScreenPreview() {
    FinancesTheme {
        DashboardContent(
            state = DashboardUiState(
                monthPeriod = MonthPeriod.now(),
                monthlyBalance = Money.fromCents(511_560),
                totalIncome = Money.fromCents(650_000),
                totalExpenses = Money.fromCents(138_440),
                categorySummaries = listOf(
                    CategorySummary(
                        category = Category(id = 1, name = "Mercado", type = CategoryType.EXPENSE),
                        totalAmount = Money.fromCents(58_000)
                    ),
                    CategorySummary(
                        category = Category(id = 2, name = "Transporte", type = CategoryType.EXPENSE),
                        totalAmount = Money.fromCents(32_000)
                    ),
                    CategorySummary(
                        category = Category(id = 3, name = "Lazer", type = CategoryType.EXPENSE),
                        totalAmount = Money.fromCents(28_440)
                    ),
                    CategorySummary(
                        category = Category(id = 4, name = "Salário", type = CategoryType.INCOME),
                        totalAmount = Money.fromCents(650_000)
                    )
                )
            ),
            onCategoriesClick = {},
            onAddTransactionClick = {},
            onHistoryClick = {},
            onSettingsClick = {},
            onImportStatementClick = {}
        )
    }
}
