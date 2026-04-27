package br.com.otavioesteves.finances.data.local.mapper

import br.com.otavioesteves.finances.data.local.entity.CategoryEntity
import br.com.otavioesteves.finances.data.local.entity.TransactionEntity
import br.com.otavioesteves.finances.domain.model.Category
import br.com.otavioesteves.finances.domain.model.Money
import br.com.otavioesteves.finances.domain.model.Transaction

fun CategoryEntity.toDomain(): Category {
    return Category(
        id = this.id,
        name = this.name,
        type = this.type
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
        notes = this.notes
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
        notes = this.notes
    )
}
