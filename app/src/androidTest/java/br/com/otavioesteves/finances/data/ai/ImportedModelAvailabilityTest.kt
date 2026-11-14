package br.com.otavioesteves.finances.data.ai

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.otavioesteves.finances.domain.ai.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.RandomAccessFile
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ImportedModelAvailabilityTest {
    private fun withStorage(test: suspend (Context, File) -> Unit) = runBlocking {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val prefix = "model-availability-${UUID.randomUUID()}"
        val directory = File(base.cacheDir, prefix).apply { mkdirs() }
        val context = object : ContextWrapper(base) {
            override fun getFilesDir() = directory
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                base.getSharedPreferences("$prefix-$name", mode)
        }
        try { test(context, File(directory, "models/current.litertlm")) }
        finally {
            directory.deleteRecursively()
            base.deleteSharedPreferences("$prefix-local_ai_model")
        }
    }

    private fun seedModel(context: Context, file: File) {
        file.parentFile!!.mkdirs()
        // File plausibility only; these tests never initialize a native engine.
        RandomAccessFile(file, "rw").use { it.setLength(10L * 1024 * 1024) }
        context.getSharedPreferences("local_ai_model", Context.MODE_PRIVATE).edit()
            .putString("display_name", "Modelo instalado").putString("sha256", "existing-hash").commit()
    }

    @Test fun installedModelSurvivesCapacityLossAndRecoversWithoutRecreatingTheEngine() = withStorage { context, file ->
        seedModel(context, file)
        var reason: UnsupportedReason? = UnsupportedReason.NO_STORAGE
        val engine = ImportedModelLocalAiEngine(context, { reason }, LiteRtEngineSession())
        assertEquals(AiEngineState.Unsupported(UnsupportedReason.NO_STORAGE), engine.state.value)
        assertTrue(engine.hasImportedModel.value)
        reason = null
        engine.refreshAvailability()
        val ready = engine.state.value as AiEngineState.Ready
        assertEquals("existing-hash", ready.model.id)
        assertEquals(file.absolutePath, ready.model.path)
        reason = UnsupportedReason.NO_STORAGE
        engine.refreshAvailability()
        assertEquals(AiEngineState.Unsupported(reason!!), engine.state.value)
        assertTrue(file.isFile)
    }

    @Test fun installedFileCanBeRemovedUnderEveryUnsupportedReason() = withStorage { context, file ->
        for (reason in UnsupportedReason.entries) {
            seedModel(context, file)
            val engine = ImportedModelLocalAiEngine(context, { reason }, LiteRtEngineSession())
            assertTrue(engine.hasImportedModel.value)
            engine.removeImportedModel().getOrThrow()
            assertFalse(file.exists())
            assertFalse(engine.hasImportedModel.value)
            assertEquals(AiEngineState.Unsupported(reason), engine.state.value)
            assertTrue(context.getSharedPreferences("local_ai_model", Context.MODE_PRIVATE).all.isEmpty())
        }
    }

    @Test fun importRechecksCapacityAfterRecoveryAndBeforeReadingTheProvider() = withStorage { context, _ ->
        var reason: UnsupportedReason? = UnsupportedReason.NO_STORAGE
        val engine = ImportedModelLocalAiEngine(context, { reason }, LiteRtEngineSession())
        var opened = false
        assertTrue(engine.importModel("blocked") { opened = true; byteArrayOf().inputStream() }.isFailure)
        assertFalse(opened)
        reason = null
        // Deliberately invalid small bundle reaches staging but never the native runtime.
        assertTrue(engine.importModel("candidate") { opened = true; byteArrayOf(1).inputStream() }.isFailure)
        assertTrue(opened)
        assertTrue(engine.state.value is AiEngineState.Failed)
        assertFalse(engine.hasImportedModel.value)
        engine.refreshAvailability()
        assertEquals(AiEngineState.NotProvisioned, engine.state.value)
    }

    @Test fun removingTheInstalledFileRechecksTheSpaceItFreed() = withStorage { context, file ->
        seedModel(context, file)
        val engine = ImportedModelLocalAiEngine(context,
            { if (file.exists()) UnsupportedReason.NO_STORAGE else null }, LiteRtEngineSession())
        assertTrue(engine.state.value is AiEngineState.Unsupported)
        engine.removeImportedModel().getOrThrow()
        assertEquals(AiEngineState.NotProvisioned, engine.state.value)
        assertFalse(engine.hasImportedModel.value)
    }
}
