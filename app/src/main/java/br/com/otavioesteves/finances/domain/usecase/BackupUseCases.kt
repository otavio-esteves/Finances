package br.com.otavioesteves.finances.domain.usecase

import br.com.otavioesteves.finances.domain.model.BackupModel
import br.com.otavioesteves.finances.domain.repository.BackupRepository
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class CreateBackupUseCase(private val repository: BackupRepository) {
    suspend operator fun invoke(): String {
        val backup = repository.createBackup()
        return Json { prettyPrint = true }.encodeToString(backup)
    }
}

class RestoreBackupUseCase(private val repository: BackupRepository) {
    suspend operator fun invoke(json: String) {
        val backup = Json.decodeFromString<BackupModel>(json)
        // Validação básica
        if (backup.categories.isEmpty() && backup.transactions.isNotEmpty()) {
            throw IllegalArgumentException("Backup inválido: transações sem categorias")
        }
        repository.restoreBackup(backup)
    }
}
