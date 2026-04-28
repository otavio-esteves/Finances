package br.com.otavioesteves.finances.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class CategoryExportModel(
    val id: Long,
    val name: String,
    val type: CategoryType
)

@Serializable
data class TransactionExportModel(
    val id: Long,
    val description: String,
    val amountCents: Long,
    val categoryId: Long,
    val date: String,
    val type: TransactionType,
    val notes: String?
)

@Serializable
data class BackupModel(
    val version: Int = 1,
    val categories: List<CategoryExportModel>,
    val transactions: List<TransactionExportModel>
)
