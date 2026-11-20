package br.com.otavioesteves.finances.domain.usecase

import br.com.otavioesteves.finances.domain.model.BackupModel
import br.com.otavioesteves.finances.domain.model.BackupInputStream
import br.com.otavioesteves.finances.domain.model.requirePersistableText
import br.com.otavioesteves.finances.domain.repository.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import java.io.InputStream
import java.io.OutputStream
import java.io.PushbackInputStream

private val backupJson = Json { prettyPrint = true; encodeDefaults = true }

@OptIn(ExperimentalSerializationApi::class)
class CreateBackupUseCase(private val repository: BackupRepository, private val encryption: BackupEncryption? = null) {
    suspend operator fun invoke(): String {
        val backup = repository.createBackup().also { it.requirePersistableText() }
        return backupJson.encodeToString(backup)
    }

    /** Both formats stream JSON directly; neither allocates a second full JSON/ciphertext buffer. */
    suspend operator fun invoke(output: OutputStream, password: CharArray? = null) {
        try {
            output.use { raw ->
                val context = currentCoroutineContext()
                val destination = if (password == null) raw else requireNotNull(encryption).encrypt(raw, password) { context.ensureActive() }
                destination.use { stream ->
                    val backup = repository.createBackup().also { it.requirePersistableText() }
                    val cancellableOutput = object : OutputStream() {
                        override fun write(value: Int) { context.ensureActive(); stream.write(value) }
                        override fun write(buffer: ByteArray, offset: Int, length: Int) {
                            context.ensureActive(); stream.write(buffer, offset, length)
                        }
                        override fun flush() = stream.flush()
                    }
                    backupJson.encodeToStream(backup, cancellableOutput)
                    context.ensureActive()
                    stream.flush()
                }
                context.ensureActive()
            }
        } finally { password?.fill('\u0000') }
    }
}

@OptIn(ExperimentalSerializationApi::class)
class RestoreBackupUseCase(private val repository: BackupRepository, private val encryption: BackupEncryption? = null) {
    suspend operator fun invoke(json: String) { invoke(json.byteInputStream(Charsets.UTF_8)) }

    suspend operator fun invoke(input: InputStream, password: CharArray? = null, onValidated: () -> Unit = {}) {
        val context = currentCoroutineContext()
        var protected = false
        val backup: BackupModel
        try {
            backup = PushbackInputStream(input, 8).use { source ->
                val prefix = ByteArray(8)
                var count = 0
                while (count < prefix.size) {
                    context.ensureActive()
                    val value = source.read()
                    if (value < 0) break
                    prefix[count++] = value.toByte()
                }
                source.unread(prefix, 0, count)
                protected = count == prefix.size && prefix.contentEquals(ProtectedBackupFormat.magic)
                val decoded = if (!protected) source else {
                    if (password == null || password.isEmpty()) throw BackupProtectionException(BackupProtectionException.Reason.PASSWORD_REQUIRED)
                    val protection = encryption ?: throw BackupProtectionException(BackupProtectionException.Reason.UNSUPPORTED)
                    protection.decrypt(source, password) { context.ensureActive() }
                }
                BackupInputStream(decoded) { context.ensureActive() }.use { checked ->
                    backupJson.decodeFromStream<BackupModel>(checked).also {
                        // Authentication of the final Tink segment and EOF must finish before repository mutation.
                        val buffer = ByteArray(8192)
                        while (checked.read(buffer) >= 0) context.ensureActive()
                    }
                }
            }
        } catch (e: CancellationException) { throw e }
        catch (e: BackupProtectionException) { throw e }
        catch (e: Exception) {
            if (protected) throw BackupProtectionException(BackupProtectionException.Reason.INVALID)
            throw e
        } finally { password?.fill('\u0000') }
        context.ensureActive()
        if (backup.categories.isEmpty() && backup.transactions.isNotEmpty())
            throw IllegalArgumentException("Backup inválido: transações sem categorias")
        require(backup.version in 1..5) { "Versão de backup não suportada" }
        backup.requirePersistableText()
        onValidated()
        context.ensureActive()
        repository.restoreBackup(backup)
    }
}
