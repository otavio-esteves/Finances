package br.com.otavioesteves.finances.domain.usecase

import br.com.otavioesteves.finances.domain.model.*
import br.com.otavioesteves.finances.domain.repository.BackupRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

class BackupStreamingTest {
    private fun backup(notes: String? = null) = BackupModel(
        categories = listOf(CategoryExportModel(1, "Outros", CategoryType.EXPENSE)),
        transactions = listOf(TransactionExportModel(1, "Compra", 100, 1, "2026-11-01", TransactionType.EXPENSE, notes))
    )

    private class Repository(var data: BackupModel) : BackupRepository {
        var restored: BackupModel? = null
        override suspend fun createBackup() = data
        override suspend fun restoreBackup(backup: BackupModel) { restored = backup }
    }

    @Test fun generatedBackupAboveFiftyMegabytesCanBeRestoredFromAStream() = runBlocking {
        val note = "a".repeat(60_000)
        val template = backup(note)
        val data = template.copy(transactions = (1L..900L).map { template.transactions.single().copy(id = it) })
        val repository = Repository(data)
        val file = File.createTempFile("finances-backup-test-", ".json")
        try {
            CreateBackupUseCase(repository)(file.outputStream())
            assertTrue(file.length().toString(), file.length() > 50L * 1024 * 1024)
            RestoreBackupUseCase(repository)(file.inputStream())
            assertEquals(data, repository.restored)
        } finally { file.delete() }
    }

    @Test fun maximumEscapedFieldAndMultibyteTextRoundTrip() = runBlocking {
        val repository = Repository(backup("\u0001".repeat(MAX_PERSISTED_TEXT_BYTES)))
        val output = ByteArrayOutputStream()
        CreateBackupUseCase(repository)(output)
        RestoreBackupUseCase(repository)(output.toByteArray().inputStream())
        assertEquals(repository.data, repository.restored)
        repository.data = backup("😀".repeat(MAX_PERSISTED_TEXT_BYTES / 4))
        output.reset()
        CreateBackupUseCase(repository)(output)
        RestoreBackupUseCase(repository)(output.toByteArray().inputStream())
        assertEquals(repository.data, repository.restored)
    }

    @Test fun invalidUtf8ClosesInputAndNeverCallsRestore() {
        val repository = Repository(backup())
        val json = runBlocking { CreateBackupUseCase(repository)() }.toByteArray()
        val location = json.indexOf('C'.code.toByte())
        json[location] = 0xff.toByte()
        val input = object : ByteArrayInputStream(json) {
            var closed = false
            override fun close() { closed = true; super.close() }
        }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { RestoreBackupUseCase(repository)(input) } }
        assertTrue(input.closed)
        assertNull(repository.restored)
    }

    @Test fun oversizeFieldsAreRejectedBeforeAnyBackupBytesAreWritten() {
        val repository = Repository(backup("a".repeat(3 * 1024 * 1024)))
        val output = object : ByteArrayOutputStream() {
            var closed = false
            override fun close() { closed = true; super.close() }
        }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { CreateBackupUseCase(repository)(output) } }
        assertEquals(0, output.size())
        assertTrue(output.closed)
    }

    @Test fun destinationFailureIsPropagatedAndClosesTheStream() {
        val output = object : java.io.OutputStream() {
            var closed = false
            override fun write(value: Int) { throw IOException("Destino indisponível") }
            override fun close() { closed = true }
        }
        assertThrows(IOException::class.java) { runBlocking { CreateBackupUseCase(Repository(backup()))(output) } }
        assertTrue(output.closed)
    }
}
