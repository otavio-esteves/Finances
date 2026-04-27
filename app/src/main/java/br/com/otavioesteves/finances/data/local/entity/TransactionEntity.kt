package br.com.otavioesteves.finances.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import br.com.otavioesteves.finances.domain.model.TransactionType
import java.time.LocalDate

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("categoryId")]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val description: String,
    val amountCents: Long,
    val categoryId: Long,
    val date: LocalDate,
    val type: TransactionType,
    val notes: String?
)
