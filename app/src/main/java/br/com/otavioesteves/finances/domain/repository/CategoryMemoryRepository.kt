package br.com.otavioesteves.finances.domain.repository

import br.com.otavioesteves.finances.domain.model.CategoryMemory

interface CategoryMemoryRepository {
    suspend fun getMemories(): List<CategoryMemory>
    suspend fun clear()
}
