package br.com.otavioesteves.finances.domain.ai

import java.io.InputStream
import kotlinx.coroutines.flow.StateFlow

/**
 * Provisions a user-supplied model bundle onto the device (Fase 2a — import
 * manual via SAF). See docs/PLANO_MOTOR_IA_LOCAL.md, "Importação do modelo".
 */
interface ModelImporter {
    /** File presence is independent of the device's ability to run inference. */
    val hasImportedModel: StateFlow<Boolean>
    suspend fun refreshAvailability()
    suspend fun importModel(displayName: String, openStream: () -> InputStream): Result<InstalledModel>
    suspend fun removeImportedModel(): Result<Unit>
}
