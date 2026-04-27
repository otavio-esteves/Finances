package br.com.otavioesteves.finances.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import br.com.otavioesteves.finances.domain.model.CategoryType

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val type: CategoryType
)
