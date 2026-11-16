package br.com.otavioesteves.finances.domain.usecase

import br.com.otavioesteves.finances.domain.repository.TransactionsRepository
import br.com.otavioesteves.finances.domain.model.Transaction
import br.com.otavioesteves.finances.domain.model.TransactionEditSnapshot
import br.com.otavioesteves.finances.domain.model.StaleTransactionEditException

class DeleteTransactionUseCase(
    private val repository: TransactionsRepository
) {
    suspend fun forConfirmation(transaction: Transaction): TransactionEditSnapshot {
        val snapshot = repository.getTransactionForEditing(transaction.id) ?: throw StaleTransactionEditException()
        // The visible list may itself be stale when the user taps Delete.
        if (snapshot.transaction != transaction) throw StaleTransactionEditException()
        return snapshot
    }

    suspend operator fun invoke(transactionId: Long) {
        repository.removeTransaction(transactionId)
    }
    suspend operator fun invoke(transactionId: Long, dataRevision: Long) {
        repository.removeTransactionIfCurrent(transactionId, dataRevision)
    }
}
