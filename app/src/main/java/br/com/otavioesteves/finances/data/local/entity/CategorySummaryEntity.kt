package br.com.otavioesteves.finances.data.local.entity

import br.com.otavioesteves.finances.domain.model.CategoryType

data class CategorySummaryEntity(
    val id: Long,
    val name: String,
    val type: CategoryType,
    val totalAmountCents: Long
)
