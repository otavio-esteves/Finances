package br.com.otavioesteves.finances.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.otavioesteves.finances.domain.model.CategoryType
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CategoryPersistenceTest {
    @Test
    fun firstReadAlreadyContainsDefaultCategoriesAndConcurrentDuplicatesAreRejected() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java
        ).addCallback(AppDatabase.DefaultCategoriesCallback()).build()
        try {
            val dao = database.categoryDao()
            val firstRead = dao.getAllCategories().first()
            assertEquals(9, firstRead.size)
            assertEquals(CategoryType.entries.toSet(), firstRead.map { it.type }.toSet())
            val results = listOf(" Educação ", "educação").map { name ->
                async { runCatching { dao.createCategory(name, CategoryType.EXPENSE) } }
            }.awaitAll()
            assertEquals(1, results.count { it.isSuccess })
            assertEquals(1, dao.getAllCategoriesForBackup().count { it.name.equals("Educação", ignoreCase = true) })
            assertTrue(runCatching { dao.createCategory("  ", CategoryType.INCOME) }.isFailure)
            assertEquals(CategoryType.INCOME, dao.createCategory("Educação", CategoryType.INCOME).type)
        } finally { database.close() }
    }
}
