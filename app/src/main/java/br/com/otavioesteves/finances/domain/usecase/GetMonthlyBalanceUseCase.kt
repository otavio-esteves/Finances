package br.com.otavioesteves.finances.domain.usecase

import br.com.otavioesteves.finances.domain.model.MonthPeriod
import br.com.otavioesteves.finances.domain.model.Money
import br.com.otavioesteves.finances.domain.repository.TransactionsRepository
import kotlinx.coroutines.flow.Flow

class GetMonthlyBalanceUseCase(
    private val repository: TransactionsRepository
) {
    operator fun invoke(period: MonthPeriod): Flow<Money> {
        return repository.getMonthlyBalance(period)
    }
}
