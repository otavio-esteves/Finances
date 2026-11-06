package br.com.otavioesteves.finances.data.repository

import br.com.otavioesteves.finances.data.local.dao.CategoryMemoryDao
import br.com.otavioesteves.finances.domain.model.CategoryMemory
import br.com.otavioesteves.finances.domain.repository.CategoryMemoryRepository

class RoomCategoryMemoryRepository(private val dao: CategoryMemoryDao) : CategoryMemoryRepository {
    override suspend fun getMemories() = dao.getAll().map { CategoryMemory(it.descriptionKey, it.inputType, it.categoryId) }
    override suspend fun clear() = dao.clear()
}
