package br.com.otavioesteves.finances.domain.model

data class ExpenseComparison(val category: Category, val current: Money, val previous: Money) {
    val difference: Money get() = current - previous
}

/** Compare recorded expenses only. Missing history is not evidence of zero spending. */
fun compareExpenses(current: List<CategorySummary>, previous: List<CategorySummary>): List<ExpenseComparison> {
    val currentById = current.associateBy { it.category.id }
    return previous.filter { it.category.type == CategoryType.EXPENSE && it.totalAmount.isPositive() }
        .map { ExpenseComparison(it.category, currentById[it.category.id]?.totalAmount ?: Money.Zero, it.totalAmount) }
        .filter { !it.difference.isZero() }
        .sortedByDescending { kotlin.math.abs(it.difference.cents) }
        .take(3)
}
