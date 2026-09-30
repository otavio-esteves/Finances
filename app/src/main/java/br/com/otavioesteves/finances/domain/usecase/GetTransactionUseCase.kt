package br.com.otavioesteves.finances.domain.usecase

import br.com.otavioesteves.finances.domain.model.Transaction
import br.com.otavioesteves.finances.domain.repository.TransactionsRepository
import kotlinx.coroutines.flow.Flow

class GetTransactionUseCase(
    private val repository: TransactionsRepository
) {
    operator fun invoke(id: Long): Flow<Transaction?> = repository.getTransaction(id)
}
