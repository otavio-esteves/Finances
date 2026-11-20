package br.com.otavioesteves.finances.data.backup

import br.com.otavioesteves.finances.domain.model.*
import br.com.otavioesteves.finances.domain.repository.*
import br.com.otavioesteves.finances.domain.usecase.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream

class ProtectedBackupTest {
    private val password get() = "frase longa café 🔑 123".toCharArray()
    private fun data() = BackupModel(categories = listOf(CategoryExportModel(1, "Outros", CategoryType.EXPENSE)),
        transactions = (1L..50).map { TransactionExportModel(it, "Compra parcela $it/50", 1234L + it, 1,
            "2026-11-01", TransactionType.EXPENSE, "sintético ".repeat(350)) })
    private class Repository(var data: BackupModel) : BackupRepository {
        var restores = 0
        override suspend fun createBackup() = data
        override suspend fun restoreBackup(backup: BackupModel) { data = backup; restores++ }
    }
    private suspend fun encode(repository: Repository): ByteArray {
        val output = ByteArrayOutputStream()
        CreateBackupUseCase(repository, PasswordBackupEncryption())(output, password)
        return output.toByteArray()
    }

    @Test fun freshInstancesRestorePortableMultisegmentBackupAndClearPasswords() = runBlocking {
        val source = Repository(data())
        val bytes = encode(source)
        assertTrue(bytes.size > PasswordBackupEncryption.SEGMENT_BYTES * 2)
        assertFalse(bytes.toString(Charsets.ISO_8859_1).contains("Compra parcela"))
        assertFalse(bytes.contentEquals(encode(source))) // New salt and nonce every export.
        val target = Repository(BackupModel(categories = emptyList(), transactions = emptyList()))
        val secret = password
        RestoreBackupUseCase(target, PasswordBackupEncryption())(bytes.inputStream(), secret)
        assertTrue(secret.all { it == '\u0000' })
        assertEquals(source.data, target.data)
        assertEquals(1, target.restores)
        val exportPassword = password
        CreateBackupUseCase(source, PasswordBackupEncryption())(ByteArrayOutputStream(), exportPassword)
        assertTrue(exportPassword.all { it == '\u0000' })
    }

    @Test fun wrongPasswordTamperingTruncationTrailingBytesAndVersionNeverMutateRepository() = runBlocking {
        val repository = Repository(data())
        val original = repository.data
        val valid = encode(repository)
        val variants = listOf(valid to "senha incorreta".toCharArray(),
            valid.copyOf().also { it[14] = (it[14].toInt() xor 1).toByte() } to password,
            valid.copyOf().also { it[valid.lastIndex] = (it.last().toInt() xor 1).toByte() } to password,
            valid.copyOf(valid.size - 1) to password,
            valid.copyOf(valid.size - 70_000) to password,
            (valid + byteArrayOf(1, 2, 3)) to password,
            valid.copyOf().also { it[8] = 99 } to password,
            valid.copyOf().also { it[10] = 127 } to password)
        for ((bytes, secret) in variants) {
            assertTrue(runCatching { RestoreBackupUseCase(repository, PasswordBackupEncryption())(bytes.inputStream(), secret) }
                .exceptionOrNull() is BackupProtectionException)
            assertTrue(secret.all { it == '\u0000' })
            assertEquals(0, repository.restores)
            assertEquals(original, repository.data)
        }
        val missing = runCatching { RestoreBackupUseCase(repository, PasswordBackupEncryption())(valid.inputStream()) }.exceptionOrNull()
        assertEquals(BackupProtectionException.Reason.PASSWORD_REQUIRED, (missing as BackupProtectionException).reason)
        assertEquals(0, repository.restores)
    }

    @Test fun authenticatedButInvalidJsonAndCancellationCannotReachRestore() = runBlocking {
        val repository = Repository(data())
        val protection = PasswordBackupEncryption()
        val output = ByteArrayOutputStream()
        protection.encrypt(output, password) {}.use { it.write("{\"version\":5,\"categories\":[],\"transactions\":[".toByteArray()) }
        assertTrue(runCatching { RestoreBackupUseCase(repository, protection)(output.toByteArray().inputStream(), password) }.isFailure)
        val valid = encode(repository)
        val interrupted = object : java.io.ByteArrayInputStream(valid) {
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (pos > 70_000) throw CancellationException("cancel")
                return super.read(b, off, minOf(len, 4096))
            }
        }
        assertTrue(runCatching { RestoreBackupUseCase(repository, protection)(interrupted, password) }.exceptionOrNull() is CancellationException)
        assertEquals(0, repository.restores)
    }

    @Test fun legacyJsonVersionsStillRestoreAndWeakExportPasswordIsRejected() = runBlocking {
        val repository = Repository(data())
        for (version in 1..5) {
            val json = """{"version":$version,"categories":[],"transactions":[]}"""
            RestoreBackupUseCase(repository, PasswordBackupEncryption())(json.byteInputStream())
            assertEquals(version, repository.data.version)
        }
        assertEquals(5, repository.restores)
        val short = "123".toCharArray()
        assertTrue(runCatching { CreateBackupUseCase(repository, PasswordBackupEncryption())(ByteArrayOutputStream(), short) }
            .exceptionOrNull() is BackupProtectionException)
        assertTrue(short.all { it == '\u0000' })
    }

    @Test fun api24FallbackMatchesJcaAndPublishedPbkdf2Sha256Vector() {
        val encryption = PasswordBackupEncryption()
        val salt = "salt".toByteArray()
        val expected = "120fb6cffcf8b32c43e7225256c4f837a86548c92ccc35480805987cb70be17b"
        val result = encryption.derive("password".toCharArray(), salt, 1, forceFallback = true)
        assertEquals(expected, result.joinToString("") { "%02x".format(it) })
        assertArrayEquals(encryption.derive(password, salt, 1000), encryption.derive(password, salt, 1000, true))
    }
}
