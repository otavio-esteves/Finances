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
    val notes: String?,
    val origin: TransactionOrigin = TransactionOrigin.MANUAL
)

@Serializable
data class StatementImportExportModel(
    val id: Long,
    val fileName: String,
    val importedAt: String,
    val transactionCount: Int,
    val fingerprint: String?
)

@Serializable
data class ChatMessageExportModel(
    val id: Long,
    val role: ChatRole,
    val content: String,
    val createdAt: String
)

@Serializable
data class BackupModel(
    val version: Int = 2,
    val categories: List<CategoryExportModel>,
    val transactions: List<TransactionExportModel>,
    val statementImports: List<StatementImportExportModel> = emptyList(),
    val chatMessages: List<ChatMessageExportModel> = emptyList()
)
