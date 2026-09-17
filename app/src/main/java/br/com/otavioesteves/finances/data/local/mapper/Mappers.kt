package br.com.otavioesteves.finances.data.local.mapper

import br.com.otavioesteves.finances.data.local.entity.CategoryEntity
import br.com.otavioesteves.finances.data.local.entity.CategorySummaryEntity
import br.com.otavioesteves.finances.data.local.entity.ChatMessageEntity
import br.com.otavioesteves.finances.data.local.entity.StatementImportEntity
import br.com.otavioesteves.finances.data.local.entity.TransactionEntity
import br.com.otavioesteves.finances.domain.model.Category
import br.com.otavioesteves.finances.domain.model.CategorySummary
import br.com.otavioesteves.finances.domain.model.ChatMessage
import br.com.otavioesteves.finances.domain.model.ImportedStatement
import br.com.otavioesteves.finances.domain.model.Money
import br.com.otavioesteves.finances.domain.model.Transaction

fun CategoryEntity.toDomain(): Category {
    return Category(
        id = this.id,
        name = this.name,
        type = this.type
    )
}

fun CategorySummaryEntity.toDomain(): CategorySummary {
    return CategorySummary(
        category = Category(id = id, name = name, type = type),
        totalAmount = Money.fromCents(totalAmountCents)
    )
}

fun Category.toEntity(): CategoryEntity {
    return CategoryEntity(
        id = this.id,
        name = this.name,
        type = this.type
    )
}

fun TransactionEntity.toDomain(): Transaction {
    return Transaction(
        id = this.id,
        description = this.description,
        amount = Money.fromCents(this.amountCents),
        categoryId = this.categoryId,
        date = this.date,
        type = this.type,
        notes = this.notes,
        origin = this.origin
    )
}

fun Transaction.toEntity(): TransactionEntity {
    return TransactionEntity(
        id = this.id,
        description = this.description,
        amountCents = this.amount.cents,
        categoryId = this.categoryId,
        date = this.date,
        type = this.type,
        notes = this.notes,
        origin = this.origin
    )
}

fun StatementImportEntity.toDomain(): ImportedStatement {
    return ImportedStatement(
        id = this.id,
        fileName = this.fileName,
        importedAt = this.importedAt,
        transactionCount = this.transactionCount
    )
}

fun ImportedStatement.toEntity(): StatementImportEntity {
    return StatementImportEntity(
        id = this.id,
        fileName = this.fileName,
        importedAt = this.importedAt,
        transactionCount = this.transactionCount
    )
}

fun ChatMessageEntity.toDomain(): ChatMessage {
    return ChatMessage(
        id = this.id,
        role = this.role,
        content = this.content,
        createdAt = this.createdAt
    )
}

fun ChatMessage.toEntity(): ChatMessageEntity {
    return ChatMessageEntity(
        id = this.id,
        role = this.role,
        content = this.content,
        createdAt = this.createdAt
    )
}
