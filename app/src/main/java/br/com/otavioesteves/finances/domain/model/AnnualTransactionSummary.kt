package br.com.otavioesteves.finances.domain.model

/** Compact chart data: at most twelve totals per category, without transaction descriptions. */
data class AnnualTransactionSummary(
    val year: Int,
    val monthlyExpenses: List<Long> = List(12) { 0L },
    val categoryMonthlyExpenses: Map<Long, List<Long>> = emptyMap(),
    val trackedNoteCount: Int = 0
)

/** Default implementation for repositories without a database aggregation query. */
fun summarizeAnnualTransactions(
    transactions: List<Transaction>,
    year: Int,
    trackedNote: String?
): AnnualTransactionSummary {
    val monthly = LongArray(12)
    val byCategory = mutableMapOf<Long, LongArray>()
    var noteCount = 0
    transactions.forEach { transaction ->
        if (transaction.date.year != year) return@forEach
        if (trackedNote != null && transaction.notes == trackedNote) noteCount++
        if (transaction.type != TransactionType.EXPENSE) return@forEach
        val month = transaction.date.monthValue - 1
        monthly[month] = Math.addExact(monthly[month], transaction.amount.cents)
        val category = byCategory.getOrPut(transaction.categoryId) { LongArray(12) }
        category[month] = Math.addExact(category[month], transaction.amount.cents)
    }
    return AnnualTransactionSummary(year, monthly.toList(), byCategory.mapValues { it.value.toList() }, noteCount)
}
