package br.com.otavioesteves.finances.domain.model

/** Transactions and their category catalog captured from the same database state. */
data class TransactionExportSnapshot(
    val transactions: List<Transaction>,
    val categories: List<Category>
)
