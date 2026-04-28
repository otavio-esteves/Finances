package br.com.otavioesteves.finances.domain.repository

import br.com.otavioesteves.finances.domain.model.BackupModel

interface BackupRepository {
    suspend fun createBackup(): BackupModel
    suspend fun restoreBackup(backup: BackupModel)
}
