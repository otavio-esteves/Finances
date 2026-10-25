package br.com.otavioesteves.finances.data.local.entity

/** Room query projection; this is not a persisted table and does not change the schema. */
data class TransactionMonthAggregateEntity(
    val month: Int,
    val categoryId: Long,
    val expenseCents: Long,
    val expenseCount: Int,
    val trackedNoteCount: Int
)
