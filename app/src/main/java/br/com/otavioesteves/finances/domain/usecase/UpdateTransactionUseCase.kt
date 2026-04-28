package br.com.otavioesteves.finances.domain.usecase

import br.com.otavioesteves.finances.domain.model.Transaction
import br.com.otavioesteves.finances.domain.repository.TransactionsRepository

class UpdateTransactionUseCase(
    private val repository: TransactionsRepository
) {
    suspend operator fun invoke(transaction: Transaction) {
        repository.updateTransaction(transaction)
    }
}
