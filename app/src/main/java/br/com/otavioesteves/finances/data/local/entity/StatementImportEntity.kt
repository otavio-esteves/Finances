package br.com.otavioesteves.finances.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Entity(tableName = "statement_imports")
data class StatementImportEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val fileName: String,
    val importedAt: LocalDateTime,
    val transactionCount: Int
)
