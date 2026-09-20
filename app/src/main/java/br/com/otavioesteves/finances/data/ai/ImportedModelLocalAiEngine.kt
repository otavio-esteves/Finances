package br.com.otavioesteves.finances.data.ai

import android.content.Context
import br.com.otavioesteves.finances.domain.ai.AiError
import br.com.otavioesteves.finances.domain.ai.AiEngineState
import br.com.otavioesteves.finances.domain.ai.InstalledModel
import br.com.otavioesteves.finances.domain.ai.LocalAiEngine
import br.com.otavioesteves.finances.domain.ai.ModelImporter
import br.com.otavioesteves.finances.domain.ai.ModelSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.Locale

/**
 * [LocalAiEngine] backed by a model the user imported manually via SAF
 * (Fase 2a — docs/PLANO_MOTOR_IA_LOCAL.md, seção 2.3). [warmUp] itself is
 * still only a format/size sanity check on the bundle, not a load of the
 * LiteRT-LM runtime — the real engine lives in [LiteRtTransactionCategorizer]
 * and [LiteRtLocalAiRepository] (Fase 3/5), which read [state]/[InstalledModel.path]
 * from here and do their own loading per call.
 */
class ImportedModelLocalAiEngine(
    private val context: Context,
    private val capabilityGate: AndroidDeviceCapabilityGate = AndroidDeviceCapabilityGate(context)
) : LocalAiEngine, ModelImporter {

    private val modelsDir = File(context.filesDir, MODELS_DIR_NAME)
    private val modelFile = File(modelsDir, MODEL_FILE_NAME)
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(resolveInitialState())
    override val state: StateFlow<AiEngineState> = _state

    private fun resolveInitialState(): AiEngineState {
        capabilityGate.check()?.let { return AiEngineState.Unsupported(it) }
        val displayName = prefs.getString(KEY_DISPLAY_NAME, null)
        if (displayName == null || !isPlausibleBundle(modelFile)) return AiEngineState.NotProvisioned
        return AiEngineState.Ready(
            InstalledModel(
                id = prefs.getString(KEY_SHA256, "").orEmpty(),
                displayName = displayName,
                source = ModelSource.USER_IMPORTED,
                verified = prefs.getBoolean(KEY_VERIFIED, false),
                path = modelFile.absolutePath
            )
        )
    }

    override suspend fun importModel(displayName: String, openStream: () -> InputStream): Result<InstalledModel> {
        if (_state.value is AiEngineState.Unsupported) {
            return Result.failure(IllegalStateException("Aparelho não suporta o motor de IA local"))
        }
        return withContext(Dispatchers.IO) {
            val tempFile = File(modelsDir, "$MODEL_FILE_NAME.tmp")
            try {
                modelsDir.mkdirs()
                val digest = MessageDigest.getInstance("SHA-256")
                openStream().use { input ->
                    DigestInputStream(input, digest).use { digestStream ->
                        tempFile.outputStream().use { output -> digestStream.copyTo(output) }
                    }
                }

                if (!isPlausibleBundle(tempFile)) {
                    tempFile.delete()
                    _state.value = AiEngineState.Failed(AiError.ModelNotLoaded)
                    return@withContext Result.failure(
                        IllegalArgumentException("Arquivo selecionado não parece ser um modelo válido")
                    )
                }

                if (!tempFile.renameTo(modelFile)) {
                    tempFile.copyTo(modelFile, overwrite = true)
                    tempFile.delete()
                }

                val sha256 = digest.digest().joinToString("") { "%02x".format(Locale.ROOT, it) }
                val verified = sha256 in KNOWN_MODEL_HASHES

                prefs.edit()
                    .putString(KEY_DISPLAY_NAME, displayName)
                    .putString(KEY_SHA256, sha256)
                    .putBoolean(KEY_VERIFIED, verified)
                    .apply()

                val installedModel = InstalledModel(
                    id = sha256,
                    displayName = displayName,
                    source = ModelSource.USER_IMPORTED,
                    verified = verified,
                    path = modelFile.absolutePath
                )
                _state.value = AiEngineState.Ready(installedModel)
                Result.success(installedModel)
            } catch (e: Exception) {
                tempFile.delete()
                _state.value = AiEngineState.Failed(AiError.Unknown(e))
                Result.failure(e)
            }
        }
    }

    override suspend fun removeImportedModel(): Result<Unit> = withContext(Dispatchers.IO) {
        modelFile.delete()
        prefs.edit().clear().apply()
        _state.value = capabilityGate.check()?.let { AiEngineState.Unsupported(it) } ?: AiEngineState.NotProvisioned
        Result.success(Unit)
    }

    override suspend fun warmUp(): Result<Unit> = withContext(Dispatchers.IO) {
        if (_state.value !is AiEngineState.Ready) return@withContext Result.success(Unit)
        if (!isPlausibleBundle(modelFile)) {
            _state.value = AiEngineState.Failed(AiError.ModelNotLoaded)
            return@withContext Result.failure(IllegalStateException("Bundle do modelo ausente ou corrompido"))
        }
        Result.success(Unit)
    }

    override suspend fun release() = Unit

    private fun isPlausibleBundle(file: File): Boolean =
        file.exists() && file.length() >= MIN_BUNDLE_SIZE_BYTES

    private companion object {
        const val PREFS_NAME = "local_ai_model"
        const val MODELS_DIR_NAME = "models"
        const val MODEL_FILE_NAME = "current.litertlm"
        const val KEY_DISPLAY_NAME = "display_name"
        const val KEY_SHA256 = "sha256"
        const val KEY_VERIFIED = "verified"
        const val MIN_BUNDLE_SIZE_BYTES = 10L * 1024 * 1024

        /** Bundles verified during Fase 4's device measurements. Empty until then. */
        val KNOWN_MODEL_HASHES: Set<String> = emptySet()
    }
}
