package br.com.otavioesteves.finances.domain.usecase

import br.com.otavioesteves.finances.domain.model.Transaction
import br.com.otavioesteves.finances.domain.repository.TransactionsRepository

class UpdateTransactionUseCase(
    private val repository: TransactionsRepository
) {
    suspend operator fun invoke(transaction: Transaction) {
        repository.updateTransaction(transaction)
    }
    suspend operator fun invoke(transaction: Transaction, dataRevision: Long) {
        repository.updateTransactionIfCurrent(transaction, dataRevision)
    }
}
