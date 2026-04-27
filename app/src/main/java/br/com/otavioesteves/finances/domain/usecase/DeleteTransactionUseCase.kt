package br.com.otavioesteves.finances.domain.usecase

import br.com.otavioesteves.finances.domain.repository.TransactionsRepository

class DeleteTransactionUseCase(
    private val repository: TransactionsRepository
) {
    suspend operator fun invoke(transactionId: Long) {
        repository.removeTransaction(transactionId)
    }
}
