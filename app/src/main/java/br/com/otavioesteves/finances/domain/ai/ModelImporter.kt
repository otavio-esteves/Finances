package br.com.otavioesteves.finances.domain.ai

import java.io.InputStream

/**
 * Provisions a user-supplied model bundle onto the device (Fase 2a — import
 * manual via SAF). See docs/PLANO_MOTOR_IA_LOCAL.md, seção 2.3 e 4.
 */
interface ModelImporter {
    suspend fun importModel(displayName: String, openStream: () -> InputStream): Result<InstalledModel>
    suspend fun removeImportedModel(): Result<Unit>
}
