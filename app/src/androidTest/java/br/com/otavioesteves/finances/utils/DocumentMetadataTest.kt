package br.com.otavioesteves.finances.utils

import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.content.ContextWrapper
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Looper
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import br.com.otavioesteves.finances.domain.DateProvider
import br.com.otavioesteves.finances.domain.ai.*
import br.com.otavioesteves.finances.domain.model.*
import br.com.otavioesteves.finances.domain.repository.*
import br.com.otavioesteves.finances.domain.usecase.*
import br.com.otavioesteves.finances.presentation.aimodel.AiModelViewModel
import br.com.otavioesteves.finances.presentation.importstatement.ImportStatementUiState
import br.com.otavioesteves.finances.presentation.importstatement.ImportStatementViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.InputStream
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29) // ContentResolver.wrap is only used to provide the test double.
class DocumentMetadataTest {
    private class Provider : ContentProvider() {
        @Volatile var fail = false
        val mainThreadQueries = java.util.concurrent.CopyOnWriteArrayList<Boolean>()
        val projections = java.util.concurrent.CopyOnWriteArrayList<List<String>>()
        val cursors = java.util.concurrent.CopyOnWriteArrayList<Cursor>()
        override fun onCreate() = true
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
            mainThreadQueries += (Looper.myLooper() == Looper.getMainLooper())
            projections += projection?.toList().orEmpty()
            if (fail) throw SecurityException("Consulta recusada pelo provedor")
            return MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME)).apply {
                addRow(arrayOf("arquivo.csv"))
                cursors += this
            }
        }
        override fun getType(uri: Uri) = "text/csv"
        override fun insert(uri: Uri, values: ContentValues?): Uri? = error("Unused")
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
    }

    private class Model : LocalAiEngine, ModelImporter {
        override val state = MutableStateFlow<AiEngineState>(AiEngineState.NotProvisioned)
        override val hasImportedModel = MutableStateFlow(false)
        var imports = 0
        var lastName: String? = null
        override suspend fun warmUp() = Result.success(Unit)
        override suspend fun refreshAvailability() = Unit
        override suspend fun release() = Unit
        override suspend fun importModel(displayName: String, openStream: () -> InputStream): Result<InstalledModel> {
            imports++
            lastName = displayName
            return Result.success(InstalledModel("test", displayName, ModelSource.USER_IMPORTED, false, "/test"))
        }
        override suspend fun removeImportedModel() = Result.success(Unit)
    }

    private fun statementViewModel(): ImportStatementViewModel {
        val category = Category(1, "Outros", CategoryType.EXPENSE)
        val entry = RawStatementEntry("Compra", Money(100), LocalDate.of(2026, 11, 1), TransactionType.EXPENSE)
        val dates = object : DateProvider {
            override fun getCurrentDate() = entry.date
            override fun getCurrentDateTime() = entry.date.atStartOfDay()
            override fun getCurrentMonthPeriod() = MonthPeriod(2026, 11)
        }
        val categories = object : CategoriesRepository {
            override fun getCategories() = flowOf(listOf(category))
            override fun getCategorySummaries(period: MonthPeriod) = flowOf(emptyList<CategorySummary>())
        }
        val transactions = object : TransactionsRepository {
            override fun hasTransactions() = flowOf(false)
            override fun getTransaction(id: Long) = flowOf<Transaction?>(null)
            override fun getTransactions(period: MonthPeriod) = flowOf(emptyList<Transaction>())
            override fun getMonthlyBalance(period: MonthPeriod) = flowOf(Money.Zero)
            override suspend fun addTransaction(transaction: Transaction) = Unit
            override suspend fun removeTransaction(transactionId: Long) = Unit
            override suspend fun updateTransaction(transaction: Transaction) = Unit
        }
        val imports = object : StatementImportRepository {
            override fun getImports() = flowOf(emptyList<ImportedStatement>())
            override suspend fun confirmImport(statementImport: ImportedStatement, transactions: List<Transaction>, fingerprint: String, legacyFingerprints: List<String>) = Unit
        }
        val parser = object : StatementParserRepository {
            override suspend fun parse(fileName: String, rawContent: ByteArray, kind: StatementKind, password: String?, referenceYear: Int?) = listOf(entry)
        }
        val categorizer = object : TransactionCategorizer {
            override suspend fun categorize(entries: List<RawStatementEntry>, categories: List<Category>, onProgress: (Int, Int) -> Unit) =
                Result.success(entries.map { CategorySuggestion(it, category, 1f) })
        }
        return ImportStatementViewModel(ImportStatementUseCase(parser), SynthesizeStatementUseCase(categorizer, categories),
            ConfirmStatementImportUseCase(imports, dates), categories, FindPotentialOverlapsUseCase(transactions))
    }

    @Test fun bothImportFlowsQueryOnlyTheNameOffTheMainThreadAndCloseTheirCursors() = runBlocking {
        exerciseProvider(fail = false)
    }

    @Test fun failingProviderShowsErrorsInBothFlowsWithoutOpeningFilesAndAllowsRetry() = runBlocking {
        exerciseProvider(fail = true)
    }

    private suspend fun exerciseProvider(fail: Boolean) = coroutineScope {
        withTimeout(20_000) {
            val provider = Provider().apply { this.fail = fail }
            val appContext = InstrumentationRegistry.getInstrumentation().targetContext
            provider.attachInfo(appContext, ProviderInfo().apply { authority = "test"; exported = false })
            val resolver = ContentResolver.wrap(provider)
            val context = object : ContextWrapper(appContext) {
                override fun getContentResolver() = resolver
            }
            val uri = Uri.parse("content://test/arquivo.csv")
            val store = ViewModelStore()
            val model = Model()
            val statement = withContext(Dispatchers.Main) { statementViewModel().also { store.put("statement", it) } }
            val ai = withContext(Dispatchers.Main) { AiModelViewModel(model, model).also { store.put("ai", it) } }
            val collector = launch(Dispatchers.Main) { ai.uiState.collect() }
            var opened = false
            suspend fun selectFiles() = withContext(Dispatchers.Main) {
                statement.onFileSelected({ requireNotNull(queryDisplayName(context, uri)) }) {
                    opened = true
                    byteArrayOf(1).inputStream()
                }
                ai.onModelSelected({ requireNotNull(queryDisplayName(context, uri)) }) {
                    opened = true
                    byteArrayOf(1).inputStream()
                }
            }
            try {
                selectFiles()
                if (fail) {
                    val error = statement.uiState.first { it is ImportStatementUiState.Error } as ImportStatementUiState.Error
                    assertTrue(error.message.contains("Consulta recusada"))
                    ai.uiState.first { it.errorMessage != null && !it.isImporting }
                    assertEquals(0, model.imports)
                    assertFalse(opened)
                    provider.fail = false
                    selectFiles()
                }
                val review = statement.uiState.first { it is ImportStatementUiState.ReviewingSuggestions } as ImportStatementUiState.ReviewingSuggestions
                ai.uiState.first { !it.isImporting && it.errorMessage == null && model.imports == 1 }
                assertEquals("arquivo.csv", review.fileName)
                assertEquals("arquivo.csv", model.lastName)
                assertTrue(provider.mainThreadQueries.isNotEmpty())
                assertTrue(provider.mainThreadQueries.none { it })
                assertTrue(provider.projections.all { it == listOf(OpenableColumns.DISPLAY_NAME) })
                assertEquals(2, provider.cursors.size)
                assertTrue(provider.cursors.all { it.isClosed })
            } finally {
                collector.cancel()
                withContext(Dispatchers.Main) { store.clear() }
            }
        }
    }
}
