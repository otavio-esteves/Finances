package br.com.otavioesteves.finances.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import br.com.otavioesteves.finances.domain.model.*
import br.com.otavioesteves.finances.domain.repository.*
import br.com.otavioesteves.finances.domain.usecase.*
import br.com.otavioesteves.finances.presentation.settings.SettingsViewModel
import br.com.otavioesteves.finances.ui.screens.settings.SettingsScreen
import br.com.otavioesteves.finances.ui.theme.FinancesTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime

@RunWith(AndroidJUnit4::class)
class ImportSettingsTest {
    @get:Rule val rule = createComposeRule()
    private val store = ViewModelStore()
    @After fun clear() { rule.runOnIdle { store.clear() } }

    @Test fun historyRequiresConfirmationAndRefreshesAfterUndo() {
        val imports = MutableStateFlow(listOf(ImportedStatement(1, "fatura-teste.csv", LocalDateTime.of(2026, 11, 1, 9, 0), 2, canUndo = true)))
        var undone = false
        val repository = object : StatementImportRepository {
            override fun getImports() = imports
            override suspend fun undoImport(importId: Long) { assertEquals(1L, importId); undone = true; imports.value = emptyList() }
            override suspend fun confirmImport(statementImport: ImportedStatement, transactions: List<Transaction>, fingerprint: String, legacyFingerprints: List<String>) {}
        }
        val vm = viewModel(repository)
        rule.setContent { FinancesTheme { SettingsScreen(onAiModelClick = {}, viewModel = vm) } }
        rule.onNodeWithText("Lembretes de faturas").assertIsDisplayed()
        rule.onNodeWithText("Histórico de importações").performScrollTo().performClick()
        rule.onNodeWithText("fatura-teste.csv").assertIsDisplayed()
        rule.onNodeWithText("Desfazer importação").performClick()
        assertFalse(undone)
        rule.onNodeWithText("Desfazer").performClick()
        rule.waitUntil(10_000) { undone }
        rule.onNodeWithText("Nenhuma importação salva.").assertIsDisplayed()
    }

    private fun viewModel(repository: StatementImportRepository): SettingsViewModel {
        val backups = object : BackupRepository {
            override suspend fun createBackup() = BackupModel(categories = emptyList(), transactions = emptyList())
            override suspend fun restoreBackup(backup: BackupModel) {}
        }
        return SettingsViewModel(CreateBackupUseCase(backups), RestoreBackupUseCase(backups), importsRepository = repository)
            .also { store.put("settings", it) }
    }
}
