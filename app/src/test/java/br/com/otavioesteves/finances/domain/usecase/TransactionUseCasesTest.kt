package br.com.otavioesteves.finances.domain.usecase

import br.com.otavioesteves.finances.domain.model.MonthPeriod
import br.com.otavioesteves.finances.domain.model.Money
import br.com.otavioesteves.finances.domain.model.Transaction
import br.com.otavioesteves.finances.domain.model.TransactionType
import br.com.otavioesteves.finances.domain.repository.TransactionsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class TransactionUseCasesTest {
    @Test
    fun getTransactionsByMonth_returnsRepositoryFlow() = runBlocking {
        val january = MonthPeriod(year = 2026, month = 1)
        val transactions = listOf(
            transaction(
                id = 1L,
                amountInCents = 100_000,
                type = TransactionType.INCOME,
                date = LocalDate.of(2026, 1, 5)
            ),
            transaction(
                id = 2L,
                amountInCents = 5_000,
                type = TransactionType.EXPENSE,
                date = LocalDate.of(2026, 1, 8)
            )
        )
        val repository = FakeTransactionsRepository(transactions)

        val result = GetTransactionsByMonthUseCase(repository)(january).first()

        assertEquals(transactions, result)
    }

    @Test
    fun addTransaction_delegatesToRepository() = runBlocking {
        val transaction = transaction(
            id = 10L,
            amountInCents = 9_999,
            type = TransactionType.EXPENSE,
            date = LocalDate.of(2026, 1, 10)
        )
        val repository = FakeTransactionsRepository()

        AddTransactionUseCase(repository)(transaction)

        assertEquals(listOf(transaction), repository.allTransactions())
    }

    @Test
    fun deleteTransaction_delegatesToRepository() = runBlocking {
        val repository = FakeTransactionsRepository(
            listOf(
                transaction(1L, 10_000, TransactionType.EXPENSE, LocalDate.of(2026, 1, 1)),
                transaction(2L, 20_000, TransactionType.EXPENSE, LocalDate.of(2026, 1, 2))
            )
        )

        DeleteTransactionUseCase(repository)(1L)

        assertEquals(listOf(2L), repository.allTransactions().map(Transaction::id))
    }

    @Test
    fun getMonthlyBalance_calculatesIncomeMinusExpenseAndIgnoresTransfer() = runBlocking {
        val repository = FakeTransactionsRepository(
            listOf(
                transaction(1L, 500_000, TransactionType.INCOME, LocalDate.of(2026, 1, 5)),
                transaction(2L, 125_000, TransactionType.EXPENSE, LocalDate.of(2026, 1, 8)),
                transaction(3L, 20_000, TransactionType.TRANSFER, LocalDate.of(2026, 1, 10))
            )
        )

        val balance = GetMonthlyBalanceUseCase(repository)(MonthPeriod(year = 2026, month = 1)).first()

        assertEquals(375_000L, balance.cents)
    }

    private fun transaction(
        id: Long,
        amountInCents: Long,
        type: TransactionType,
        date: LocalDate
    ): Transaction {
        return Transaction(
            id = id,
            description = "Transaction $id",
            amount = Money.fromCents(amountInCents),
            categoryId = 1L,
            date = date,
            type = type
        )
    }

    private class FakeTransactionsRepository(
        initialTransactions: List<Transaction> = emptyList()
    ) : TransactionsRepository {
        private val transactions = MutableStateFlow(initialTransactions)

        override fun getTransactions(period: MonthPeriod): Flow<List<Transaction>> {
            return transactions.map { currentTransactions ->
                currentTransactions.filter { transaction ->
                    transaction.date.year == period.year && transaction.date.monthValue == period.month
                }
            }
        }

        override suspend fun addTransaction(transaction: Transaction) {
            transactions.value = transactions.value + transaction
        }

        override suspend fun removeTransaction(transactionId: Long) {
            transactions.value = transactions.value.filterNot { it.id == transactionId }
        }

        override suspend fun updateTransaction(transaction: Transaction) {
            transactions.value = transactions.value.map { current ->
                if (current.id == transaction.id) transaction else current
            }
        }

        fun allTransactions(): List<Transaction> = transactions.value
    }
}
