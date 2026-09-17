package br.com.otavioesteves.finances.domain.model

import java.time.LocalDate

data class RawStatementEntry(
    val description: String,
    val amount: Money,
    val date: LocalDate,
    val type: TransactionType
) {
    init {
        require(description.isNotBlank()) { "description must not be blank" }
        require(!amount.isNegative()) { "amount must not be negative" }
    }
}
