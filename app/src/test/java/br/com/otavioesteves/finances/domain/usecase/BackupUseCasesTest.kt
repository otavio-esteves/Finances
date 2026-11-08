package br.com.otavioesteves.finances.domain.usecase

import br.com.otavioesteves.finances.domain.model.*
import br.com.otavioesteves.finances.domain.repository.BackupRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class BackupUseCasesTest {

    private class FakeBackupRepository : BackupRepository {
        var savedBackup: BackupModel? = null
        override suspend fun createBackup(): BackupModel {
            return BackupModel(
                categories = listOf(CategoryExportModel(1, "Food", CategoryType.EXPENSE)),
                transactions = listOf(TransactionExportModel(1, "Pizza", 5000, 1, "2026-01-01", TransactionType.EXPENSE, null))
            )
        }
        override suspend fun restoreBackup(backup: BackupModel) {
            savedBackup = backup
        }
    }

    @Test
    fun `restoreBackupUseCase validates and calls repository`() = runBlocking {
        val repo = FakeBackupRepository()
        val restoreUseCase = RestoreBackupUseCase(repo)
        
        val json = """
            {
                "version": 1,
                "categories": [{"id": 1, "name": "Food", "type": "EXPENSE"}],
                "transactions": [{"id": 1, "description": "Pizza", "amountCents": 5000, "categoryId": 1, "date": "2026-01-01", "type": "EXPENSE", "notes": null}]
            }
        """.trimIndent()

        restoreUseCase(json)
        
        assertEquals(1, repo.savedBackup?.categories?.size)
        assertEquals("Pizza", repo.savedBackup?.transactions?.first()?.description)
        assertEquals(TransactionOrigin.MANUAL, repo.savedBackup?.transactions?.first()?.origin)
        assertEquals(emptyList<ChatMessageExportModel>(), repo.savedBackup?.chatMessages)
    }

    @Test
    fun `createBackupUseCase serializes version five with original categorization keys`() = runBlocking {
        val repo = FakeBackupRepository()
        val json = CreateBackupUseCase(repo)()
        assertEquals(true, json.contains("\"version\": 5"))
        assertEquals(true, json.contains("\"categoryMemoryInputType\": null"))
    }

    @Test
    fun `restoreBackupUseCase accepts every supported version and rejects future versions`() = runBlocking<Unit> {
        val repo = FakeBackupRepository()
        val restore = RestoreBackupUseCase(repo)
        for (version in 1..5) {
            restore("""{"version":$version,"categories":[],"transactions":[]}""")
            assertEquals(version, repo.savedBackup?.version)
        }
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            runBlocking { restore("""{"version":6,"categories":[],"transactions":[]}""") }
        }
    }

    @Test(expected = Exception::class)
    fun `restoreBackupUseCase throws exception for invalid JSON`() = runBlocking {
        val repo = FakeBackupRepository()
        val restoreUseCase = RestoreBackupUseCase(repo)
        restoreUseCase("invalid json")
    }
}
