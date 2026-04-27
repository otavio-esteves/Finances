package br.com.otavioesteves.finances.domain.usecase

import br.com.otavioesteves.finances.domain.model.MonthPeriod
import br.com.otavioesteves.finances.domain.model.Money
import br.com.otavioesteves.finances.domain.model.Transaction
import br.com.otavioesteves.finances.domain.model.TransactionType
import br.com.otavioesteves.finances.domain.repository.TransactionsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class GetMonthlyBalanceUseCase(
    private val repository: TransactionsRepository
) {
    operator fun invoke(period: MonthPeriod): Flow<Money> {
        return repository.getTransactions(period).map { transactions ->
            transactions
                .map { transaction -> transaction.toBalanceContribution() }
                .fold(Money.Zero, Money::plus)
        }
    }

    private fun Transaction.toBalanceContribution(): Money {
        return when (type) {
            TransactionType.INCOME -> amount
            TransactionType.EXPENSE -> -amount
            TransactionType.TRANSFER -> Money.Zero
        }
    }
}
