package br.com.otavioesteves.finances.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import br.com.otavioesteves.finances.domain.DateProvider
import br.com.otavioesteves.finances.domain.model.Category
import br.com.otavioesteves.finances.domain.model.CategorySummary
import br.com.otavioesteves.finances.domain.model.CategoryType
import br.com.otavioesteves.finances.domain.model.Money
import br.com.otavioesteves.finances.domain.model.MonthPeriod
import br.com.otavioesteves.finances.domain.model.Transaction
import br.com.otavioesteves.finances.domain.model.TransactionType
import br.com.otavioesteves.finances.domain.repository.CategoriesRepository
import br.com.otavioesteves.finances.domain.repository.TransactionsRepository
import br.com.otavioesteves.finances.domain.usecase.DeleteTransactionUseCase
import br.com.otavioesteves.finances.domain.usecase.GetTransactionsByMonthUseCase
import br.com.otavioesteves.finances.presentation.transactions.TransactionsViewModel
import br.com.otavioesteves.finances.ui.components.TransactionListRow
import br.com.otavioesteves.finances.ui.screens.transactions.TransactionsScreen
import br.com.otavioesteves.finances.ui.theme.FinancesTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalDateTime

@RunWith(AndroidJUnit4::class)
class TransactionCardDesignTest {
    @get:Rule val composeRule = createComposeRule()
    private val store = ViewModelStore()
    private val period = MonthPeriod(2026, 2)
    private val transaction = Transaction(1, "Compra teste", Money(145_000), 1, LocalDate.of(2026, 2, 10), TransactionType.EXPENSE)

    @After
    fun clearViewModel() { composeRule.runOnIdle { store.clear() } }

    @Test
    fun longFinancialDataKeepsItsOwnColumnOnNarrowScreenWithLargeFont() {
        val description = "Compra com descrição muito longa para testar leitura em uma tela pequena"
        val category = "Categoria de alimentação e compras para a casa"
        val amount = "R$ 92.233.720.368.547.758,07"
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.6f)) {
                FinancesTheme {
                    Box(Modifier.width(320.dp)) {
                        TransactionListRow(
                            transaction = transaction.copy(description = description), categoryName = category,
                            formattedAmount = amount, formattedDate = "10/02/2026", uniformHeight = true,
                            modifier = Modifier.testTag("financial_card").padding(horizontal = 14.dp),
                            trailing = {
                                IconButton(onClick = {}) { Icon(Icons.Default.MoreVert, contentDescription = "Ações do teste") }
                            }
                        )
                    }
                }
            }
        }
        composeRule.onNodeWithText(amount, useUnmergedTree = true).assertIsDisplayed()
        val title = composeRule.onNodeWithText(description, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val details = composeRule.onNodeWithText(category, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val date = composeRule.onNodeWithText("10/02/2026", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val value = composeRule.onNodeWithText(amount, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val action = composeRule.onNodeWithContentDescription("Ações do teste").fetchSemanticsNode().boundsInRoot
        val card = composeRule.onNodeWithTag("financial_card").fetchSemanticsNode().boundsInRoot
        assertEquals(title.left, value.left, 1f)
        assertTrue(value.left < action.left)
        assertTrue(details.top >= title.bottom)
        assertTrue(value.top >= details.bottom)
        assertTrue(date.left >= details.right)
        assertTrue(value.top >= date.bottom)
        assertTrue(value.bottom <= card.bottom)
        assertTrue(value.right <= card.right)
    }

    @Test
    fun secondaryMenuKeepsEditAvailableAndRequiresConfirmationBeforeDeletion() {
        val repository = object : TransactionsRepository {
        override fun hasTransactions(): kotlinx.coroutines.flow.Flow<Boolean> = kotlinx.coroutines.flow.flowOf(false)
            val rows = MutableStateFlow(listOf(transaction))
            @Volatile var deletedId: Long? = null
            override fun getTransaction(id: Long): Flow<Transaction?> = flowOf(transaction)
            override fun getTransactions(period: MonthPeriod): Flow<List<Transaction>> = rows
            override fun getMonthlyBalance(period: MonthPeriod): Flow<Money> = flowOf(Money.Zero)
            override suspend fun addTransaction(transaction: Transaction) = Unit
            override suspend fun updateTransaction(transaction: Transaction) = Unit
            override suspend fun removeTransaction(transactionId: Long) { deletedId = transactionId; rows.value = emptyList() }
        }
        val categories = object : CategoriesRepository {
            override fun getCategories(): Flow<List<Category>> = flowOf(listOf(Category(1, "Alimentação", CategoryType.EXPENSE)))
            override fun getCategorySummaries(period: MonthPeriod): Flow<List<CategorySummary>> = flowOf(emptyList())
        }
        val dates = object : DateProvider {
            override fun getCurrentMonthPeriod() = period
            override fun getCurrentDate(): LocalDate = period.toYearMonth().atDay(1)
            override fun getCurrentDateTime(): LocalDateTime = getCurrentDate().atStartOfDay()
        }
        val viewModel = TransactionsViewModel(GetTransactionsByMonthUseCase(repository), DeleteTransactionUseCase(repository), categories, dates)
        store.put("transactions", viewModel)
        var editedId: Long? = null
        composeRule.setContent {
            FinancesTheme { TransactionsScreen(onBackClick = {}, onTransactionClick = { editedId = it.id }, viewModel = viewModel) }
        }
        composeRule.waitUntil(5_000) { viewModel.uiState.value.transactions.isNotEmpty() }
        composeRule.onNodeWithContentDescription("Mais ações para Compra teste").performClick()
        composeRule.onNodeWithText("Editar transação").performClick()
        composeRule.runOnIdle { assertEquals(1L, editedId); assertNull(repository.deletedId) }
        composeRule.onNodeWithContentDescription("Mais ações para Compra teste").performClick()
        composeRule.onNodeWithText("Excluir transação").performClick()
        composeRule.onNodeWithText("Tem certeza que deseja excluir esta transação? Esta ação não pode ser desfeita.").assertIsDisplayed()
        composeRule.runOnIdle { assertNull(repository.deletedId) }
        composeRule.onNodeWithText("Cancelar").performClick()
        composeRule.onNodeWithText("Excluir").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Mais ações para Compra teste").performClick()
        composeRule.onNodeWithText("Excluir transação").performClick()
        composeRule.onNodeWithText("Excluir").performClick()
        composeRule.waitUntil(5_000) { repository.deletedId == 1L }
    }
}
