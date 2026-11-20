package br.com.otavioesteves.finances.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import br.com.otavioesteves.finances.data.backup.PasswordBackupEncryption
import br.com.otavioesteves.finances.domain.model.*
import br.com.otavioesteves.finances.domain.repository.BackupRepository
import br.com.otavioesteves.finances.domain.usecase.*
import br.com.otavioesteves.finances.presentation.settings.SettingsViewModel
import br.com.otavioesteves.finances.ui.screens.settings.SettingsScreen
import br.com.otavioesteves.finances.ui.theme.FinancesTheme
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream

@RunWith(AndroidJUnit4::class)
class BetaBackupFlowTest {
    @get:Rule val rule = createComposeRule()
    private val store = ViewModelStore()
    private class Repository : BackupRepository {
        @Volatile var restores = 0
        var data = BackupModel(categories = emptyList(), transactions = emptyList())
        override suspend fun createBackup() = data
        override suspend fun restoreBackup(backup: BackupModel) { data = backup; restores++ }
    }
    @After fun clear() { rule.runOnIdle { store.clear() } }

    @Test fun wrongPasswordAllowsRetryAndCancelDoesNotOpenTheDocument() {
        val repository = Repository()
        val encryption = PasswordBackupEncryption()
        val output = ByteArrayOutputStream()
        runBlocking { CreateBackupUseCase(repository, encryption)(output, "senha correta sintética".toCharArray()) }
        val vm = SettingsViewModel(CreateBackupUseCase(repository, encryption), RestoreBackupUseCase(repository, encryption))
        store.put("settings", vm)
        rule.setContent { FinancesTheme { SettingsScreen(onAiModelClick = {}, viewModel = vm) } }
        var opens = 0
        rule.runOnIdle { vm.onRestoreRequest { opens++; output.toByteArray().inputStream() } }
        rule.onNodeWithText("Cancelar").performClick()
        rule.runOnIdle { assertEquals(0, opens); assertEquals(0, repository.restores) }
        rule.runOnIdle { vm.onRestoreRequest { opens++; output.toByteArray().inputStream() } }
        val label = "Senha do backup (JSON legado: deixe vazio)"
        rule.onNodeWithText(label).performTextReplacement("senha errada sintética")
        rule.onNodeWithText("Confirmar Restauração").performClick()
        rule.waitUntil(60_000) { !vm.uiState.value.isLoading && vm.uiState.value.showRestoreConfirmation }
        rule.runOnIdle { assertEquals(0, repository.restores); assertEquals(1, opens) }
        rule.onNodeWithText(label).performTextReplacement("senha correta sintética")
        rule.onNodeWithText("Confirmar Restauração").performClick()
        rule.waitUntil(60_000) { !vm.uiState.value.isLoading && repository.restores == 1 }
        rule.runOnIdle { assertEquals(2, opens); assertNull(vm.uiState.value.error) }
    }
}
