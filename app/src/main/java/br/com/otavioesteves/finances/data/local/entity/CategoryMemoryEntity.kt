package br.com.otavioesteves.finances.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import br.com.otavioesteves.finances.domain.model.TransactionType

@Entity(tableName = "category_memories", primaryKeys = ["descriptionKey", "inputType"],
    foreignKeys = [ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"],
        childColumns = ["categoryId"], onDelete = ForeignKey.CASCADE)], indices = [Index("categoryId")])
data class CategoryMemoryEntity(val descriptionKey: String, val inputType: TransactionType, val categoryId: Long)
