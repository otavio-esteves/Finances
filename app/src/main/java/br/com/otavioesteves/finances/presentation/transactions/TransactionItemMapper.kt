package br.com.otavioesteves.finances.presentation.transactions

import br.com.otavioesteves.finances.domain.model.Category
import br.com.otavioesteves.finances.domain.model.Transaction
import br.com.otavioesteves.finances.utils.DateFormatter
import br.com.otavioesteves.finances.utils.MoneyFormatter

/** Keeps only the current month's rows and formats only inserted or changed transactions. */
internal class TransactionItemMapper {
    private var previousItems = emptyMap<Long, TransactionItem>()

    fun map(transactions: List<Transaction>, categories: List<Category>): List<TransactionItem> {
        val categoryMap = categories.associateBy { it.id }
        val items = transactions.map { transaction ->
            val category = categoryMap[transaction.categoryId]
            previousItems[transaction.id]?.takeIf {
                it.transaction == transaction && it.category == category
            } ?: TransactionItem(
                transaction = transaction,
                category = category,
                formattedAmount = MoneyFormatter.format(transaction.amount),
                formattedDate = DateFormatter.format(transaction.date)
            )
        }
        previousItems = items.associateBy { it.transaction.id }
        return items
    }
}
