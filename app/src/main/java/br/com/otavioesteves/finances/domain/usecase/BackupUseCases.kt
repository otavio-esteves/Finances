package br.com.otavioesteves.finances.domain.usecase

import br.com.otavioesteves.finances.domain.model.BackupModel
import br.com.otavioesteves.finances.domain.model.BackupInputStream
import br.com.otavioesteves.finances.domain.model.requirePersistableText
import br.com.otavioesteves.finances.domain.repository.BackupRepository
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import java.io.InputStream
import java.io.OutputStream

private val backupJson = Json { prettyPrint = true; encodeDefaults = true }

@OptIn(ExperimentalSerializationApi::class)
class CreateBackupUseCase(private val repository: BackupRepository) {
    suspend operator fun invoke(): String {
        val backup = repository.createBackup().also { it.requirePersistableText() }
        return backupJson.encodeToString(backup)
    }

    /** The document path never constructs a JSON String or a second full-size byte array. */
    suspend operator fun invoke(output: OutputStream) = output.use { destination ->
        val context = currentCoroutineContext()
        val backup = repository.createBackup().also { it.requirePersistableText() }
        val cancellableOutput = object : OutputStream() {
            override fun write(value: Int) { context.ensureActive(); destination.write(value) }
            override fun write(buffer: ByteArray, offset: Int, length: Int) {
                context.ensureActive()
                destination.write(buffer, offset, length)
            }
            override fun flush() = destination.flush()
        }
        backupJson.encodeToStream(backup, cancellableOutput)
        context.ensureActive()
        destination.flush()
    }
}

@OptIn(ExperimentalSerializationApi::class)
class RestoreBackupUseCase(private val repository: BackupRepository) {
    suspend operator fun invoke(json: String) {
        invoke(json.byteInputStream(Charsets.UTF_8))
    }

    suspend operator fun invoke(input: InputStream) {
        val context = currentCoroutineContext()
        val backup = BackupInputStream(input) { context.ensureActive() }.use {
            backupJson.decodeFromStream<BackupModel>(it)
        }
        context.ensureActive()
        if (backup.categories.isEmpty() && backup.transactions.isNotEmpty()) {
            throw IllegalArgumentException("Backup inválido: transações sem categorias")
        }
        require(backup.version in 1..5) { "Versão de backup não suportada: ${backup.version}" }
        backup.requirePersistableText()
        repository.restoreBackup(backup)
    }
}
