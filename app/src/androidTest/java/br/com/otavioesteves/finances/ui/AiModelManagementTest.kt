package br.com.otavioesteves.finances.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import br.com.otavioesteves.finances.domain.ai.*
import br.com.otavioesteves.finances.presentation.aimodel.AiModelViewModel
import br.com.otavioesteves.finances.ui.screens.aimodel.AiModelScreen
import br.com.otavioesteves.finances.ui.theme.FinancesTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.InputStream

@RunWith(AndroidJUnit4::class)
class AiModelManagementTest {
    @get:Rule val rule = createComposeRule()
    private val store = ViewModelStore()
    @After fun clear() { rule.runOnIdle { store.clear() } }

    private class Engine : LocalAiEngine, ModelImporter {
        override val state = MutableStateFlow<AiEngineState>(AiEngineState.Unsupported(UnsupportedReason.NO_STORAGE))
        override val hasImportedModel = MutableStateFlow(false)
        var available = false
        var refreshes = 0
        var removals = 0
        override suspend fun refreshAvailability() {
            refreshes++
            if (available) state.value = AiEngineState.NotProvisioned
        }
        override suspend fun warmUp() = Result.success(Unit)
        override suspend fun release() {}
        override suspend fun importModel(displayName: String, openStream: () -> InputStream): Result<InstalledModel> = error("Unexpected import")
        override suspend fun removeImportedModel(): Result<Unit> {
            removals++
            hasImportedModel.value = false
            return Result.success(Unit)
        }
    }

    @Test fun removalRemainsAccessibleForEveryUnsupportedReason() {
        val engine = Engine().apply { hasImportedModel.value = true }
        val vm = AiModelViewModel(engine, engine).also { store.put("model", it) }
        rule.setContent { FinancesTheme { AiModelScreen({}, viewModel = vm) } }
        for ((index, reason) in UnsupportedReason.entries.withIndex()) {
            rule.runOnIdle {
                engine.state.value = AiEngineState.Unsupported(reason)
                engine.hasImportedModel.value = true
            }
            rule.onNodeWithText("Remover modelo").performScrollTo().assertIsEnabled().performClick()
            rule.waitUntil(5_000) { engine.removals == index + 1 }
            rule.onNodeWithText("Remover modelo").assertDoesNotExist()
        }
    }

    @Test fun returningFromStorageSettingsReenablesImportWithoutRecreatingViewModel() {
        val engine = Engine()
        val vm = AiModelViewModel(engine, engine).also { store.put("model", it) }
        val owner = object : LifecycleOwner {
            val registry = LifecycleRegistry(this)
            override val lifecycle: Lifecycle get() = registry
        }
        rule.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        rule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                FinancesTheme { AiModelScreen({}, viewModel = vm) }
            }
        }
        rule.waitUntil(5_000) { engine.refreshes == 1 }
        rule.onNodeWithText("Selecionar arquivo do modelo (.litertlm)").assertDoesNotExist()
        rule.runOnIdle {
            owner.registry.currentState = Lifecycle.State.STARTED
            engine.available = true
            owner.registry.currentState = Lifecycle.State.RESUMED
        }
        rule.onNodeWithText("Selecionar arquivo do modelo (.litertlm)").performScrollTo().assertIsEnabled()
        assertEquals(2, engine.refreshes)
    }
}
