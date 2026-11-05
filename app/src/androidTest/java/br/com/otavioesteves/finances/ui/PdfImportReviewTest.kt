package br.com.otavioesteves.finances.ui

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import br.com.otavioesteves.finances.domain.DateProvider
import br.com.otavioesteves.finances.domain.ai.TransactionCategorizer
import br.com.otavioesteves.finances.domain.model.*
import br.com.otavioesteves.finances.domain.repository.*
import br.com.otavioesteves.finances.domain.usecase.*
import br.com.otavioesteves.finances.presentation.importstatement.ImportStatementUiState
import br.com.otavioesteves.finances.presentation.importstatement.ImportStatementViewModel
import br.com.otavioesteves.finances.ui.screens.importstatement.ImportStatementScreen
import br.com.otavioesteves.finances.ui.theme.FinancesTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class PdfImportReviewTest {
    @get:Rule val rule = createComposeRule()
    private val store = ViewModelStore()
    @After fun clear() { rule.runOnIdle { store.clear() } }

    @Test
    fun processingShowsMeasuredProgressAndCanBeCancelled() {
        val gate = CompletableDeferred<Unit>()
        val (vm, saved) = viewModel(scanned = false, processingGate = gate)
        rule.setContent { FinancesTheme { ImportStatementScreen(onBackClick = {}, viewModel = vm) } }
        rule.runOnIdle { vm.onFileSelected("fatura-em-processamento.pdf", byteArrayOf(1)) }
        rule.waitUntil(10_000) { (vm.uiState.value as? ImportStatementUiState.Loading)?.progress == 0.5f }
        rule.onNodeWithTag("import_progress_measured").assertIsDisplayed()
        rule.onNodeWithText("Processando…").assertIsDisplayed()
        rule.onNodeWithText("1 de 2 lançamentos · 50% desta etapa").assertDoesNotExist()
        rule.onNodeWithText("fatura-em-processamento.pdf").assertDoesNotExist()
        rule.onNodeWithContentDescription("Voltar").performClick()
        rule.runOnIdle { gate.complete(Unit) }
        rule.waitForIdle()
        rule.runOnIdle {
            assertEquals(ImportStatementUiState.Idle, vm.uiState.value)
            assertTrue(saved.isEmpty())
        }
    }

    @Test
    fun ocrImportNeedsAcknowledgmentAndAllowsCorrectionsBeforeSaving() {
        val (viewModel, saved) = viewModel(scanned = true)
        rule.setContent { FinancesTheme { ImportStatementScreen(onBackClick = {}, viewModel = viewModel) } }
        rule.runOnIdle { viewModel.onFileSelected("digitalizado.pdf", byteArrayOf(1)) }
        waitForReview(viewModel)
        rule.onNodeWithText("Confirmar Importação").assertIsNotEnabled()
        rule.onNode(isToggleable()).performClick()
        rule.onNodeWithText("Confirmar Importação").assertIsEnabled()
        rule.onNodeWithText("Corrigir dados").performClick()
        rule.onNodeWithText("Valor sem sinal").performTextReplacement("25,90")
        rule.onNodeWithText("Data (dd/MM/aaaa)").performTextReplacement("11/01/2026")
        rule.onNodeWithText("Aplicar").performClick()
        rule.waitUntil(10_000) { (viewModel.uiState.value as? ImportStatementUiState.ReviewingSuggestions)?.isCheckingOverlaps == false }
        rule.onNodeWithText("Confirmar Importação").assertIsNotEnabled()
        rule.onNode(isToggleable()).performClick()
        rule.onNodeWithText("Confirmar Importação").performClick()
        rule.waitUntil(10_000) { viewModel.uiState.value is ImportStatementUiState.Success }
        assertEquals(2590L, saved.single().amount.cents)
        assertEquals(LocalDate.of(2026, 1, 11), saved.single().date)
    }

    @Test
    fun protectedPdfRequestsPasswordAndWrongPasswordCanBeRetried() {
        val (viewModel, saved) = viewModel(scanned = false, protected = true)
        rule.setContent { FinancesTheme { ImportStatementScreen(onBackClick = {}, viewModel = viewModel) } }
        rule.runOnIdle { viewModel.onFileSelected("protegido.pdf", byteArrayOf(1)) }
        rule.waitUntil(10_000) { viewModel.uiState.value is ImportStatementUiState.NeedsPassword }
        rule.onNodeWithText("Senha do PDF").performTextReplacement("errada")
        rule.onNodeWithText("Abrir PDF").performClick()
        rule.waitUntil(10_000) { (viewModel.uiState.value as? ImportStatementUiState.NeedsPassword)?.error != null }
        rule.onNodeWithText("Senha do PDF").performTextReplacement("123456")
        rule.onNodeWithText("Abrir PDF").performClick()
        waitForReview(viewModel)
        assertTrue(saved.isEmpty())
        assertFalse(viewModel.uiState.value.toString().contains("123456"))
    }

    private fun waitForReview(vm: ImportStatementViewModel) {
        rule.waitUntil(10_000) { vm.uiState.value is ImportStatementUiState.ReviewingSuggestions }
    }
    private fun viewModel(scanned: Boolean, protected: Boolean = false,
        processingGate: CompletableDeferred<Unit>? = null): Pair<ImportStatementViewModel, MutableList<Transaction>> {
        val date = LocalDate.of(2026, 1, 10)
        val entry = RawStatementEntry("Mercado", Money(1000), date, TransactionType.EXPENSE, extractedByOcr = scanned)
        val category = Category(1, "Mercado", CategoryType.EXPENSE)
        val categories = object : CategoriesRepository {
            override fun getCategories() = flowOf(listOf(category))
            override fun getCategorySummaries(period: MonthPeriod) = flowOf(emptyList<CategorySummary>())
        }
        val parser = object : StatementParserRepository {
            override suspend fun parse(fileName: String, rawContent: ByteArray, kind: StatementKind, password: String?): List<RawStatementEntry> {
                if (protected && password != "123456") throw StatementPasswordRequiredException(password != null)
                return if (processingGate == null) listOf(entry) else listOf(entry, entry.copy(description = "Outra compra"))
            }
        }
        val categorizer = object : TransactionCategorizer {
            override suspend fun categorize(entries: List<RawStatementEntry>, categories: List<Category>, onProgress: (Int, Int) -> Unit): Result<List<CategorySuggestion>> {
                if (processingGate != null) {
                    onProgress(1, entries.size)
                    processingGate.await()
                }
                return Result.success(entries.map { CategorySuggestion(it, category, 1f) })
            }
        }
        val saved = mutableListOf<Transaction>()
        val imports = object : StatementImportRepository {
            override fun getImports() = flowOf(emptyList<ImportedStatement>())
            override suspend fun confirmImport(statementImport: ImportedStatement, transactions: List<Transaction>, fingerprint: String, legacyFingerprint: String?) { saved += transactions }
        }
        val transactions = object : TransactionsRepository {
            override fun getTransaction(id: Long) = flowOf<Transaction?>(null)
            override fun getTransactions(period: MonthPeriod) = flowOf(emptyList<Transaction>())
            override fun getMonthlyBalance(period: MonthPeriod) = flowOf(Money.Zero)
            override suspend fun addTransaction(transaction: Transaction) = Unit
            override suspend fun removeTransaction(transactionId: Long) = Unit
            override suspend fun updateTransaction(transaction: Transaction) = Unit
        }
        val dates = object : DateProvider {
            override fun getCurrentMonthPeriod() = MonthPeriod(2026, 1)
            override fun getCurrentDate() = date
            override fun getCurrentDateTime() = date.atStartOfDay()
        }
        return ImportStatementViewModel(ImportStatementUseCase(parser), SynthesizeStatementUseCase(categorizer, categories),
            ConfirmStatementImportUseCase(imports, dates), categories, FindPotentialOverlapsUseCase(transactions)).also { store.put("import", it) } to saved
    }
}
