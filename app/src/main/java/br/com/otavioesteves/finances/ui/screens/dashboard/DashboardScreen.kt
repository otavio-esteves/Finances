package br.com.otavioesteves.finances.ui.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.testTag
import br.com.otavioesteves.finances.ui.components.PrimaryActionButton
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
    onHistoryClick: (MonthPeriod) -> Unit,
    onTransactionClick: (Transaction) -> Unit = {},
    onCategoryClick: (CategorySummary, MonthPeriod) -> Unit = { _, _ -> },
    onImportStatementClick: () -> Unit = {},
    hazeState: HazeState? = null,
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val loadedState by viewModel.uiState.collectAsStateWithLifecycle()
    val monthPeriod by viewModel.selectedMonthPeriod.collectAsStateWithLifecycle()
    val uiState = if (loadedState.monthPeriod == monthPeriod) loadedState else DashboardUiState(monthPeriod, Money.Zero)

    DashboardContent(
        state = uiState,
        onHistoryClick = { onHistoryClick(uiState.monthPeriod) },
        onTransactionClick = onTransactionClick,
        onCategoryClick = onCategoryClick,
        onImportStatementClick = onImportStatementClick,
        onPreviousMonthClick = { viewModel.onMonthSelected(uiState.monthPeriod.previousMonth()) },
        onNextMonthClick = { viewModel.onMonthSelected(uiState.monthPeriod.nextMonth()) },
        hazeState = hazeState,
        modifier = modifier
    )
}

@Composable
internal fun DashboardContent(
    state: DashboardUiState,
    onHistoryClick: () -> Unit,
    onPreviousMonthClick: () -> Unit,
    onNextMonthClick: () -> Unit,
    onTransactionClick: (Transaction) -> Unit = {},
    onCategoryClick: (CategorySummary, MonthPeriod) -> Unit = { _, _ -> },
    onImportStatementClick: () -> Unit = {},
    hazeState: HazeState? = null,
    modifier: Modifier = Modifier
) {
    val firstUse = state.hasAnyTransactions == false && state.recentTransactions.isEmpty()
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .testTag("dashboard_list")
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            MonthPeriodSelector(
                monthPeriod = state.monthPeriod,
                onPreviousClick = onPreviousMonthClick,
                onNextClick = onNextMonthClick,
                hazeState = hazeState
            )

            if (firstUse) ImportInvitation(firstUse = true, onImportClick = onImportStatementClick)

            BalanceHero(balance = state.monthlyBalance, income = state.monthlyIncome, expenses = state.monthlyExpenses)
            if (state.comparisons.isNotEmpty()) FinanceCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Em relação ao mês anterior", style = MaterialTheme.typography.titleMedium)
                    Text("Comparação dos lançamentos registrados nos dois meses. O mês atual pode estar incompleto.",
                        style = MaterialTheme.typography.bodySmall)
                    state.comparisons.forEach { change ->
                        val direction = if (change.difference.isPositive()) "a mais" else "a menos"
                        val amount = MoneyFormatter.format(Money.fromCents(kotlin.math.abs(change.difference.cents)))
                        Text("${change.category.name}: $amount $direction", style = MaterialTheme.typography.bodyLarge)
                        Text("Anterior: ${MoneyFormatter.format(change.previous)} · Selecionado: ${MoneyFormatter.format(change.current)}",
                            style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { onCategoryClick(CategorySummary(change.category, change.current), state.monthPeriod) }) {
                            Text("Conferir lançamentos de ${change.category.name}")
                        }
                    }
                }
            }

            if (state.recentTransactions.isNotEmpty()) FinanceCard(modifier = Modifier.fillMaxWidth()) {
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

            if (!firstUse) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                }
                if (state.recentTransactions.isEmpty()) {
                    if (state.hasAnyTransactions != null) ImportInvitation(firstUse = false, onImportClick = onImportStatementClick)
                } else {
                    FinanceCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                            state.recentTransactions.forEachIndexed { index, item ->
                                TransactionListRow(
                                    transaction = item.transaction,
                                    categoryName = item.category?.name,
                                    modifier = Modifier.clickable { onTransactionClick(item.transaction) }
                                )
                                if (index < state.recentTransactions.lastIndex) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                                }
                            }
                        }
                    }
                }
                if (state.recentTransactions.isNotEmpty()) PrimaryActionButton(
                    text = "Ver transações",
                    onClick = onHistoryClick,
                    modifier = Modifier.testTag("dashboard_view_transactions")
                )
            }
            Spacer(modifier = Modifier.height(128.dp))
        }
    }
}

@Composable
private fun BalanceHero(balance: Money, income: Money, expenses: Money) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(14.dp, RoundedCornerShape(28.dp), spotColor = colors.primary.copy(alpha = 0.22f))
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(
                androidx.compose.ui.graphics.lerp(colors.primary, Color.Black, 0.18f), colors.primary
            )))
            .border(1.dp, colors.onPrimary.copy(alpha = 0.32f), RoundedCornerShape(28.dp))
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text = "VISÃO DO MÊS",
                style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.4.sp),
                color = colors.onPrimary.copy(alpha = 0.85f)
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Resultado registrado no mês",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onPrimary.copy(alpha = 0.85f)
            )
            Text(
                text = MoneyFormatter.format(balance),
                style = MaterialTheme.typography.headlineMedium,
                color = colors.onPrimary,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Receitas", style = MaterialTheme.typography.labelMedium, color = colors.onPrimary.copy(alpha = 0.85f))
                    Text(MoneyFormatter.format(income), style = MaterialTheme.typography.titleMedium, color = colors.onPrimary)
                }
                Column(Modifier.weight(1f)) {
                    Text("Despesas", style = MaterialTheme.typography.labelMedium, color = colors.onPrimary.copy(alpha = 0.85f))
                    Text(MoneyFormatter.format(expenses), style = MaterialTheme.typography.titleMedium, color = colors.onPrimary)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("Receitas menos despesas dos lançamentos registrados. O período pode estar incompleto.",
                style = MaterialTheme.typography.bodySmall, color = colors.onPrimary.copy(alpha = 0.85f))
        }
    }
}

@Composable
private fun ImportInvitation(firstUse: Boolean, onImportClick: () -> Unit) {
    FinanceCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (firstUse) "Comece pelo seu primeiro arquivo" else "Nenhum lançamento neste mês",
                style = MaterialTheme.typography.titleLarge)
            Text("Selecione uma fatura ou extrato em CSV, OFX ou PDF. O app sugere categorias e você revisa tudo antes de salvar.",
                style = MaterialTheme.typography.bodyMedium)
            PrimaryActionButton(
                text = if (firstUse) "Importar primeira fatura ou extrato" else "Importar fatura ou extrato",
                onClick = onImportClick,
                modifier = Modifier.testTag("dashboard_import_statement")
            )
            Text("O processamento acontece no seu aparelho.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
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
