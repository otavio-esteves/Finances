package br.com.otavioesteves.finances.domain.model

data class CategorySummary(
    val category: Category,
    val totalAmount: Money
)
