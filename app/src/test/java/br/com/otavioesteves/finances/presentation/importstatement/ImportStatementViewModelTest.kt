package br.com.otavioesteves.finances.presentation.importstatement

import br.com.otavioesteves.finances.domain.DateProvider
import br.com.otavioesteves.finances.domain.ai.TransactionCategorizer
import br.com.otavioesteves.finances.domain.model.Category
import br.com.otavioesteves.finances.domain.model.CategorySuggestion
import br.com.otavioesteves.finances.domain.model.CategorySummary
import br.com.otavioesteves.finances.domain.model.CategoryType
import br.com.otavioesteves.finances.domain.model.ImportedStatement
import br.com.otavioesteves.finances.domain.model.Money
import br.com.otavioesteves.finances.domain.model.MonthPeriod
import br.com.otavioesteves.finances.domain.model.RawStatementEntry
import br.com.otavioesteves.finances.domain.model.Transaction
import br.com.otavioesteves.finances.domain.model.TransactionType
import br.com.otavioesteves.finances.domain.repository.CategoriesRepository
import br.com.otavioesteves.finances.domain.repository.StatementImportRepository
import br.com.otavioesteves.finances.domain.repository.StatementParserRepository
import br.com.otavioesteves.finances.domain.repository.TransactionsRepository
import br.com.otavioesteves.finances.domain.usecase.AddTransactionUseCase
import br.com.otavioesteves.finances.domain.usecase.ConfirmStatementImportUseCase
import br.com.otavioesteves.finances.domain.usecase.ImportStatementUseCase
import br.com.otavioesteves.finances.domain.usecase.SynthesizeStatementUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class ImportStatementViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val mercado = Category(id = 1L, name = "Mercado", type = CategoryType.EXPENSE)
    private val transporte = Category(id = 2L, name = "Transporte", type = CategoryType.EXPENSE)

    private val fakeDateProvider = object : DateProvider {
        override fun getCurrentMonthPeriod(): MonthPeriod = MonthPeriod(2026, 1)
        override fun getCurrentDate(): LocalDate = LocalDate.of(2026, 1, 15)
        override fun getCurrentDateTime(): LocalDateTime = LocalDateTime.of(2026, 1, 15, 10, 0)
    }

    private val entry = RawStatementEntry(
        description = "Supermercado ABC",
        amount = Money.fromCents(10_000),
        date = LocalDate.of(2026, 1, 10),
        type = TransactionType.EXPENSE
    )

    private class FakeStatementParserRepository(
        private val entries: List<RawStatementEntry>,
        private val failure: Exception? = null
    ) : StatementParserRepository {
        override suspend fun parse(fileName: String, rawContent: ByteArray): List<RawStatementEntry> {
            failure?.let { throw it }
            return entries
        }
    }

    private class FakeTransactionCategorizer(
        private val suggestions: List<CategorySuggestion>
    ) : TransactionCategorizer {
        override suspend fun categorize(
            entries: List<RawStatementEntry>,
            categories: List<Category>,
            onProgress: (done: Int, total: Int) -> Unit
        ): Result<List<CategorySuggestion>> = Result.success(suggestions)
    }

    private class FakeCategoriesRepository(
        private val categories: List<Category>
    ) : CategoriesRepository {
        override fun getCategories(): Flow<List<Category>> = flowOf(categories)
        override fun getCategorySummaries(period: MonthPeriod): Flow<List<CategorySummary>> = flowOf(emptyList())
    }

    private class FakeTransactionsRepository : TransactionsRepository {
        val added = mutableListOf<Transaction>()
        override fun getTransactions(period: MonthPeriod): Flow<List<Transaction>> = flowOf(emptyList())
        override fun getMonthlyBalance(period: MonthPeriod): Flow<Money> = flowOf(Money.Zero)
        override suspend fun addTransaction(transaction: Transaction) {
            added += transaction
        }
        override suspend fun removeTransaction(transactionId: Long) = Unit
        override suspend fun updateTransaction(transaction: Transaction) = Unit
    }

    private class FakeStatementImportRepository : StatementImportRepository {
        val imports = mutableListOf<ImportedStatement>()
        override fun getImports(): Flow<List<ImportedStatement>> = flowOf(imports)
        override suspend fun addImport(statementImport: ImportedStatement) {
            imports += statementImport
        }
    }

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(
        entries: List<RawStatementEntry> = listOf(entry),
        suggestions: List<CategorySuggestion> = listOf(
            CategorySuggestion(entry = entry, suggestedCategory = mercado, confidence = 0.8f)
        ),
        categories: List<Category> = listOf(mercado, transporte),
        parserFailure: Exception? = null,
        transactionsRepository: FakeTransactionsRepository = FakeTransactionsRepository(),
        statementImportRepository: FakeStatementImportRepository = FakeStatementImportRepository()
    ): ImportStatementViewModel {
        val importStatement = ImportStatementUseCase(FakeStatementParserRepository(entries, parserFailure))
        val synthesizeStatement = SynthesizeStatementUseCase(
            FakeTransactionCategorizer(suggestions),
            FakeCategoriesRepository(categories)
        )
        val confirmStatementImport = ConfirmStatementImportUseCase(
            AddTransactionUseCase(transactionsRepository),
            statementImportRepository,
            fakeDateProvider
        )
        return ImportStatementViewModel(
            importStatement,
            synthesizeStatement,
            confirmStatementImport,
            FakeCategoriesRepository(categories)
        )
    }

    @Test
    fun `onFileSelected parses and synthesizes suggestions`() = runTest {
        val viewModel = createViewModel()
        viewModel.onFileSelected("extrato.csv", ByteArray(0))
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is ImportStatementUiState.ReviewingSuggestions)
        state as ImportStatementUiState.ReviewingSuggestions
        assertEquals("extrato.csv", state.fileName)
        assertEquals(1, state.suggestions.size)
        assertEquals(mercado, state.suggestions.first().suggestedCategory)
        assertTrue(state.canConfirm)
    }

    @Test
    fun `onFileSelected with no entries reports error`() = runTest {
        val viewModel = createViewModel(entries = emptyList())
        viewModel.onFileSelected("vazio.csv", ByteArray(0))
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value is ImportStatementUiState.Error)
    }

    @Test
    fun `onFileSelected with parser failure reports error message`() = runTest {
        val viewModel = createViewModel(parserFailure = IllegalArgumentException("CSV inválido"))
        viewModel.onFileSelected("ruim.csv", ByteArray(0))
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is ImportStatementUiState.Error)
        assertEquals("CSV inválido", (state as ImportStatementUiState.Error).message)
    }

    @Test
    fun `canConfirm is false while a suggestion has no category`() = runTest {
        val viewModel = createViewModel(
            suggestions = listOf(CategorySuggestion(entry = entry, suggestedCategory = null, confidence = 0f))
        )
        viewModel.onFileSelected("extrato.csv", ByteArray(0))
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value as ImportStatementUiState.ReviewingSuggestions
        assertTrue(!state.canConfirm)
    }

    @Test
    fun `onCategorySelected fills in a missing category and enables confirm`() = runTest {
        val viewModel = createViewModel(
            suggestions = listOf(CategorySuggestion(entry = entry, suggestedCategory = null, confidence = 0f))
        )
        viewModel.onFileSelected("extrato.csv", ByteArray(0))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onCategorySelected(0, transporte)

        val state = viewModel.uiState.value as ImportStatementUiState.ReviewingSuggestions
        assertEquals(transporte, state.suggestions.first().suggestedCategory)
        assertTrue(state.canConfirm)
    }

    @Test
    fun `onConfirmImport persists transaction and import history`() = runTest {
        val transactionsRepository = FakeTransactionsRepository()
        val statementImportRepository = FakeStatementImportRepository()
        val viewModel = createViewModel(
            transactionsRepository = transactionsRepository,
            statementImportRepository = statementImportRepository
        )

        viewModel.onFileSelected("extrato.csv", ByteArray(0))
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.onConfirmImport()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, transactionsRepository.added.size)
        assertEquals(1, statementImportRepository.imports.size)
        assertEquals(ImportStatementUiState.Success(1), viewModel.uiState.value)
    }
}
