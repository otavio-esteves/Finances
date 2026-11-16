package br.com.otavioesteves.finances.presentation.settings

import br.com.otavioesteves.finances.domain.model.BackupModel
import br.com.otavioesteves.finances.domain.repository.BackupRepository
import br.com.otavioesteves.finances.domain.repository.StatementImportRepository
import br.com.otavioesteves.finances.domain.model.ImportedStatement
import br.com.otavioesteves.finances.domain.model.Transaction
import br.com.otavioesteves.finances.domain.model.StaleStatementImportException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import br.com.otavioesteves.finances.domain.usecase.CreateBackupUseCase
import br.com.otavioesteves.finances.domain.usecase.RestoreBackupUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private class Repository : BackupRepository {
        var restores = 0
        override suspend fun createBackup() = BackupModel(categories = emptyList(), transactions = emptyList())
        override suspend fun restoreBackup(backup: BackupModel) { restores++ }
    }
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }
    private class Imports : StatementImportRepository {
        val requests = mutableListOf<ImportedStatement>()
        var gate: CompletableDeferred<Unit>? = null
        var stale = false
        override fun getImports() = flowOf(emptyList<ImportedStatement>())
        override suspend fun undoImport(statementImport: ImportedStatement) {
            requests += statementImport
            gate?.await()
            if (stale) throw StaleStatementImportException()
        }
        override suspend fun confirmImport(statementImport: ImportedStatement, transactions: List<Transaction>, fingerprint: String, legacyFingerprints: List<String>) = Unit
    }
    private fun viewModel(repository: Repository, imports: Imports? = null) = SettingsViewModel(
        CreateBackupUseCase(repository), RestoreBackupUseCase(repository), dispatcher, importsRepository = imports
    )

    @Test fun historyFailureIsReportedWithoutPreventingBackupRecovery() = runTest(dispatcher) {
        val imports = object : StatementImportRepository {
            override fun getImports() = kotlinx.coroutines.flow.flow<List<ImportedStatement>> {
                throw IllegalArgumentException("fileName must not be blank")
            }
            override suspend fun confirmImport(statementImport: ImportedStatement, transactions: List<Transaction>,
                fingerprint: String, legacyFingerprints: List<String>) = Unit
        }
        val repository = Repository()
        val vm = SettingsViewModel(CreateBackupUseCase(repository), RestoreBackupUseCase(repository), dispatcher,
            importsRepository = imports)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.imports.collect {} }
        runCurrent()
        assertEquals("Não foi possível carregar o histórico de importações.", vm.uiState.value.error)
        assertTrue(vm.imports.value.isEmpty())
        vm.onRestoreRequest { "{\"version\":5,\"categories\":[],\"transactions\":[]}".byteInputStream() }
        vm.onRestoreConfirm()
        runCurrent()
        assertEquals(1, repository.restores)
        assertNull(vm.uiState.value.error)
        assertNotNull(vm.uiState.value.successMessage)
    }

    @Test fun undoKeepsTheDisplayedRevisionRejectsConcurrentActionsAndAllowsFreshRetry() = runTest(dispatcher) {
        val imports = Imports().apply { gate = CompletableDeferred(); stale = true }
        val vm = viewModel(Repository(), imports)
        val old = ImportedStatement(1, "fatura.csv", java.time.LocalDateTime.of(2026, 11, 1, 9, 0), 1, canUndo = true, dataRevision = 4)
        vm.requestUndo(old)
        vm.confirmUndo()
        vm.confirmUndo()
        vm.requestUndo(old)
        vm.onRestoreRequest { error("Restore cannot start during undo") }
        runCurrent()
        assertEquals(listOf(old), imports.requests)
        assertFalse(vm.uiState.value.showRestoreConfirmation)
        assertNull(vm.uiState.value.pendingUndo)
        imports.gate!!.complete(Unit)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isLoading)
        assertNotNull(vm.uiState.value.error)
        assertNull(vm.uiState.value.successMessage)
        imports.stale = false
        val fresh = old.copy(dataRevision = 5)
        vm.requestUndo(fresh)
        vm.confirmUndo()
        advanceUntilIdle()
        assertEquals(listOf(old, fresh), imports.requests)
        assertNull(vm.uiState.value.error)
        assertNotNull(vm.uiState.value.successMessage)
    }

    @Test fun requestingRestoreDiscardsPendingUndoAndPreventsAnotherUndoConfirmation() = runTest(dispatcher) {
        val imports = Imports()
        val vm = viewModel(Repository(), imports)
        val item = ImportedStatement(1, "fatura.csv", java.time.LocalDateTime.of(2026, 11, 1, 9, 0), 1, canUndo = true, dataRevision = 0)
        vm.requestUndo(item)
        vm.onRestoreRequest { ByteArrayInputStream("{\"version\":5,\"categories\":[],\"transactions\":[]}".toByteArray()) }
        assertNull(vm.uiState.value.pendingUndo)
        vm.requestUndo(item)
        vm.confirmUndo()
        vm.onRestoreConfirm()
        advanceUntilIdle()
        assertTrue(imports.requests.isEmpty())
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test fun restoreWaitsForConfirmationAndCancelNeverOpensTheFile() = runTest(dispatcher) {
        val repository = Repository()
        val vm = viewModel(repository)
        var opened = false
        vm.onRestoreRequest { opened = true; ByteArrayInputStream(byteArrayOf()) }
        assertTrue(vm.uiState.value.showRestoreConfirmation)
        assertFalse(opened)
        vm.onRestoreCancel()
        vm.onRestoreConfirm()
        advanceUntilIdle()
        assertFalse(opened)
        assertEquals(0, repository.restores)
    }

    @Test fun doubleConfirmationRestoresOnceAndClosesTheInput() = runTest(dispatcher) {
        val repository = Repository()
        val vm = viewModel(repository)
        val input = object : ByteArrayInputStream("{\"version\":5,\"categories\":[],\"transactions\":[]}".toByteArray()) {
            var closed = false
            override fun close() { closed = true; super.close() }
        }
        vm.onRestoreRequest { input }
        vm.onRestoreConfirm()
        vm.onRestoreConfirm()
        advanceUntilIdle()
        assertEquals(1, repository.restores)
        assertTrue(input.closed)
        assertFalse(vm.uiState.value.isLoading)
        assertNotNull(vm.uiState.value.successMessage)
    }

    @Test fun streamFailureDoesNotReportBackupSuccessAndAllowsRetry() = runTest(dispatcher) {
        val vm = viewModel(Repository())
        vm.createBackup { throw java.io.IOException("Destino indisponível") }
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isLoading)
        assertNull(vm.uiState.value.successMessage)
        assertTrue(vm.uiState.value.error.orEmpty().contains("Destino indisponível"))
        val output = ByteArrayOutputStream()
        vm.createBackup { output }
        vm.createBackup { error("A second tap must not open another file") }
        advanceUntilIdle()
        assertNull(vm.uiState.value.error)
        assertNotNull(vm.uiState.value.successMessage)
        assertTrue(output.size() > 0)
    }
}
