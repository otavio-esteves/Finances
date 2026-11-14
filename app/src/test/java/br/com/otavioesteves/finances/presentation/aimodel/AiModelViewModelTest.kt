package br.com.otavioesteves.finances.presentation.aimodel

import br.com.otavioesteves.finances.domain.ai.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.InputStream

@OptIn(ExperimentalCoroutinesApi::class)
class AiModelViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private class Engine : LocalAiEngine, ModelImporter {
        override val state = MutableStateFlow<AiEngineState>(AiEngineState.Unsupported(UnsupportedReason.NO_STORAGE))
        override val hasImportedModel = MutableStateFlow(true)
        var nextState: AiEngineState = state.value
        var refreshes = 0
        var warmUps = 0
        var removals = 0
        var imports = 0
        var removalGate: CompletableDeferred<Unit>? = null
        var removalFails = false
        override suspend fun refreshAvailability() { refreshes++; state.value = nextState }
        override suspend fun warmUp(): Result<Unit> { warmUps++; return Result.success(Unit) }
        override suspend fun release() {}
        override suspend fun importModel(displayName: String, openStream: () -> InputStream): Result<InstalledModel> {
            imports++
            error("Unexpected import")
        }
        override suspend fun removeImportedModel(): Result<Unit> {
            removals++
            removalGate?.await()
            if (removalFails) throw java.io.IOException("Falha de disco")
            hasImportedModel.value = false
            state.value = AiEngineState.NotProvisioned
            return Result.success(Unit)
        }
    }

    @Test fun returningToScreenRefreshesCapacityAndWarmsOnlyAnAvailableModel() = runTest(dispatcher) {
        val engine = Engine()
        val vm = AiModelViewModel(engine, engine)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect() }
        vm.onResume()
        advanceUntilIdle()
        assertEquals(1, engine.refreshes)
        assertEquals(0, engine.warmUps)
        engine.nextState = AiEngineState.Ready(InstalledModel("sha", "Modelo", ModelSource.USER_IMPORTED, false, "/model"))
        vm.onResume()
        advanceUntilIdle()
        assertEquals(2, engine.refreshes)
        assertEquals(1, engine.warmUps)
        assertTrue(vm.uiState.value.engineState is AiEngineState.Ready)
    }

    @Test fun unsupportedModelCanBeRemovedOnceAndCannotBeReplacedDuringRemoval() = runTest(dispatcher) {
        val engine = Engine().apply { removalGate = CompletableDeferred() }
        val vm = AiModelViewModel(engine, engine)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect() }
        assertTrue(vm.uiState.value.hasImportedModel)
        vm.onRemoveModel()
        vm.onRemoveModel()
        vm.onModelSelected("Another") { error("Must not open a file during removal") }
        vm.onResume()
        runCurrent()
        assertTrue(vm.uiState.value.isRemoving)
        assertEquals(1, engine.removals)
        assertEquals(0, engine.imports)
        assertEquals(0, engine.refreshes)
        engine.removalGate!!.complete(Unit)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isRemoving)
        assertFalse(vm.uiState.value.hasImportedModel)
    }

    @Test fun removalFailureShowsAnErrorAndAllowsRetry() = runTest(dispatcher) {
        val engine = Engine().apply { removalFails = true }
        val vm = AiModelViewModel(engine, engine)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect() }
        vm.onRemoveModel()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isRemoving)
        assertTrue(vm.uiState.value.hasImportedModel)
        assertEquals("Falha de disco", vm.uiState.value.errorMessage)
        engine.removalFails = false
        vm.onRemoveModel()
        advanceUntilIdle()
        assertEquals(2, engine.removals)
        assertFalse(vm.uiState.value.hasImportedModel)
        assertNull(vm.uiState.value.errorMessage)
    }
}
