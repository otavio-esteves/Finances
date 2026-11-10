package br.com.otavioesteves.finances.presentation.settings

import br.com.otavioesteves.finances.domain.model.BackupModel
import br.com.otavioesteves.finances.domain.repository.BackupRepository
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
    private fun viewModel(repository: Repository) = SettingsViewModel(
        CreateBackupUseCase(repository), RestoreBackupUseCase(repository), dispatcher
    )

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
