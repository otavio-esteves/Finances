package br.com.otavioesteves.finances.data.repository

import br.com.otavioesteves.finances.domain.model.Category
import br.com.otavioesteves.finances.domain.model.CategoryType
import br.com.otavioesteves.finances.domain.model.Money
import br.com.otavioesteves.finances.domain.model.Transaction
import br.com.otavioesteves.finances.domain.model.TransactionType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate

internal class InMemoryFinanceStore(
    categories: List<Category>,
    transactions: List<Transaction>
) {
    private val categoriesState = MutableStateFlow(categories.toList())
    private val transactionsState = MutableStateFlow(transactions.toList())

    val categories: StateFlow<List<Category>> = categoriesState
    val transactions: StateFlow<List<Transaction>> = transactionsState

    fun addTransaction(transaction: Transaction) {
        transactionsState.value = transactionsState.value + transaction
    }

    fun removeTransaction(transactionId: Long) {
        transactionsState.value = transactionsState.value.filterNot { it.id == transactionId }
    }

    fun updateTransaction(transaction: Transaction) {
        transactionsState.value = transactionsState.value.map { current ->
            if (current.id == transaction.id) transaction else current
        }
    }

    companion object {
        fun createDefault(): InMemoryFinanceStore {
            return InMemoryFinanceStore(
                categories = defaultCategories(),
                transactions = defaultTransactions()
            )
        }

        private fun defaultCategories(): List<Category> = listOf(
            Category(id = 1L, name = "Mercado", type = CategoryType.EXPENSE),
            Category(id = 2L, name = "Água", type = CategoryType.EXPENSE),
            Category(id = 3L, name = "Energia", type = CategoryType.EXPENSE),
            Category(id = 4L, name = "Farmácia", type = CategoryType.EXPENSE),
            Category(id = 5L, name = "Transporte", type = CategoryType.EXPENSE),
            Category(id = 6L, name = "Moradia", type = CategoryType.EXPENSE),
            Category(id = 7L, name = "Salário", type = CategoryType.INCOME),
            Category(id = 8L, name = "Outros", type = CategoryType.EXPENSE)
        )

        private fun defaultTransactions(): List<Transaction> = listOf(
            Transaction(
                id = 1L,
                description = "Salário janeiro",
                amount = Money.fromCents(6_500_00),
                categoryId = 7L,
                date = LocalDate.of(2026, 1, 5),
                type = TransactionType.INCOME,
                notes = "Pagamento mensal"
            ),
            Transaction(
                id = 2L,
                description = "Compra do mês",
                amount = Money.fromCents(42_390),
                categoryId = 1L,
                date = LocalDate.of(2026, 1, 6),
                type = TransactionType.EXPENSE
            ),
            Transaction(
                id = 3L,
                description = "Supermercado extra",
                amount = Money.fromCents(18_750),
                categoryId = 1L,
                date = LocalDate.of(2026, 1, 20),
                type = TransactionType.EXPENSE
            ),
            Transaction(
                id = 4L,
                description = "Conta de água",
                amount = Money.fromCents(8_940),
                categoryId = 2L,
                date = LocalDate.of(2026, 1, 10),
                type = TransactionType.EXPENSE
            ),
            Transaction(
                id = 5L,
                description = "Conta de energia",
                amount = Money.fromCents(15_830),
                categoryId = 3L,
                date = LocalDate.of(2026, 1, 12),
                type = TransactionType.EXPENSE
            ),
            Transaction(
                id = 6L,
                description = "Farmácia",
                amount = Money.fromCents(6_290),
                categoryId = 4L,
                date = LocalDate.of(2026, 1, 18),
                type = TransactionType.EXPENSE
            ),
            Transaction(
                id = 7L,
                description = "Combustível",
                amount = Money.fromCents(12_000),
                categoryId = 5L,
                date = LocalDate.of(2026, 1, 9),
                type = TransactionType.EXPENSE
            ),
            Transaction(
                id = 8L,
                description = "Aluguel",
                amount = Money.fromCents(120_000),
                categoryId = 6L,
                date = LocalDate.of(2026, 1, 8),
                type = TransactionType.EXPENSE
            ),
            Transaction(
                id = 9L,
                description = "Manutenção doméstica",
                amount = Money.fromCents(9_990),
                categoryId = 8L,
                date = LocalDate.of(2026, 1, 25),
                type = TransactionType.EXPENSE
            ),
            Transaction(
                id = 10L,
                description = "Padaria",
                amount = Money.fromCents(3_250),
                categoryId = 1L,
                date = LocalDate.of(2026, 1, 27),
                type = TransactionType.EXPENSE
            )
        )
    }
}
