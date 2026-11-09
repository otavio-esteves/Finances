package br.com.otavioesteves.finances.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Internal magnitude budget, updated in the same transaction as every write. */
@Entity(tableName = "transaction_total")
data class TransactionTotalEntity(@PrimaryKey val id: Int = 1, val amountCents: Long)
