package br.com.otavioesteves.finances.domain.model

import java.time.LocalDate

data class Transaction(
    val id: Long,
    val description: String,
    val amount: Money,
    val categoryId: Long,
    val date: LocalDate,
    val type: TransactionType,
    val notes: String? = null,
    val origin: TransactionOrigin = TransactionOrigin.MANUAL,
    val iconKey: String? = null,
    val importId: Long? = null,
    val sourceLabel: String? = null,
    val categoryMemoryDescriptionKey: String? = null,
    val categoryMemoryInputType: TransactionType? = null
) {
    init {
        require(id >= 0) { "id must be positive or zero" }
        require(!amount.isNegative()) { "amount must not be negative" }
        require(description.isNotBlank()) { "description must not be blank" }
        require(categoryId >= 0) { "categoryId must be positive or zero" }
        requireSupportedTransactionDate(date)
        require((categoryMemoryDescriptionKey == null) == (categoryMemoryInputType == null)) { "Chave de memória incompleta." }
        require(categoryMemoryDescriptionKey == null || categoryMemoryDescriptionKey.isNotBlank()) { "Chave de memória inválida." }
    }
}
