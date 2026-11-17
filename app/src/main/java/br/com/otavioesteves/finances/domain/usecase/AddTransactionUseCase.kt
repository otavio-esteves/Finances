package br.com.otavioesteves.finances.domain.usecase

import br.com.otavioesteves.finances.domain.model.Transaction
import br.com.otavioesteves.finances.domain.repository.TransactionsRepository

class AddTransactionUseCase(
    private val repository: TransactionsRepository
) {
    val dataRevision: Long get() = repository.dataRevision

    suspend fun getDataRevision(): Long = repository.getDataRevision()

    suspend operator fun invoke(transaction: Transaction) {
        repository.addTransaction(transaction)
    }

    suspend operator fun invoke(transaction: Transaction, dataRevision: Long) {
        repository.addTransactionIfCurrent(transaction, dataRevision)
    }
}
