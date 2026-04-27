package br.com.otavioesteves.finances.domain.model

import java.time.LocalDate

data class Transaction(
    val id: Long,
    val description: String,
    val amount: Money,
    val categoryId: Long,
    val date: LocalDate,
    val type: TransactionType,
    val notes: String? = null
) {
    init {
        require(id >= 0) { "id must be positive or zero" }
        require(description.isNotBlank()) { "description must not be blank" }
        require(categoryId >= 0) { "categoryId must be positive or zero" }
    }
}
