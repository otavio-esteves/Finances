package br.com.otavioesteves.finances.ui.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import br.com.otavioesteves.finances.ui.theme.FinancesThemeTokens
import br.com.otavioesteves.finances.ui.theme.FinancesTheme
import br.com.otavioesteves.finances.utils.MoneyFormatter
import java.time.LocalDate
import dev.chrisbanes.haze.HazeState

@Composable
fun DashboardScreen(
    onHistoryClick: () -> Unit,
    hazeState: HazeState? = null,
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    DashboardContent(
        state = uiState,
        onHistoryClick = onHistoryClick,
        onPreviousMonthClick = { viewModel.onMonthSelected(uiState.monthPeriod.previousMonth()) },
        onNextMonthClick = { viewModel.onMonthSelected(uiState.monthPeriod.nextMonth()) },
        hazeState = hazeState,
        modifier = modifier
    )
}

@Composable
private fun DashboardContent(
    state: DashboardUiState,
    onHistoryClick: () -> Unit,
    onPreviousMonthClick: () -> Unit,
    onNextMonthClick: () -> Unit,
    hazeState: HazeState? = null,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            BalanceHero(balance = state.monthlyBalance)

            FinanceCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Gastos por categoria",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    CategoryUsageChart(
                        summaries = state.categorySummaries,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Transações",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    TextButton(onClick = onHistoryClick) {
                        Text(
                            text = "Ver todas",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                if (state.recentTransactions.isEmpty()) {
                    FinanceCard(modifier = Modifier.fillMaxWidth()) {
                        EmptyState(message = "Nenhuma transação neste mês ainda.")
                    }
                } else {
                    FinanceCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                            state.recentTransactions.forEachIndexed { index, item ->
                                TransactionListRow(
                                    transaction = item.transaction,
                                    categoryName = item.category?.name
                                )
                                if (index < state.recentTransactions.lastIndex) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(104.dp))
        }
        MonthPeriodSelector(
            monthPeriod = state.monthPeriod,
            onPreviousClick = onPreviousMonthClick,
            onNextClick = onNextMonthClick,
            hazeState = hazeState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(horizontal = 20.dp, vertical = 16.dp)
        )
    }
}

@Composable
private fun BalanceHero(balance: Money) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(FinancesThemeTokens.colors.heroSurface)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text = "VISÃO DO MÊS",
                style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.4.sp),
                color = FinancesThemeTokens.colors.heroLabel
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Saldo do mês",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
            )
            Text(
                text = MoneyFormatter.format(balance),
                style = MaterialTheme.typography.headlineMedium,
                color = if (balance.cents >= 0) FinancesThemeTokens.colors.income else FinancesThemeTokens.colors.expense
            )
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
            onHistoryClick = {},
            onPreviousMonthClick = {},
            onNextMonthClick = {}
        )
    }
}
