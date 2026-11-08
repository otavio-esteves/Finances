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
import br.com.otavioesteves.finances.domain.model.StatementPasswordRequiredException
import br.com.otavioesteves.finances.domain.model.StatementKind
import br.com.otavioesteves.finances.domain.repository.CategoriesRepository
import br.com.otavioesteves.finances.domain.repository.StatementImportRepository
import br.com.otavioesteves.finances.domain.repository.StatementParserRepository
import br.com.otavioesteves.finances.domain.repository.TransactionsRepository
import br.com.otavioesteves.finances.domain.usecase.ConfirmStatementImportUseCase
import br.com.otavioesteves.finances.domain.usecase.ImportStatementUseCase
import br.com.otavioesteves.finances.domain.usecase.FindPotentialOverlapsUseCase
import br.com.otavioesteves.finances.domain.usecase.SynthesizeStatementUseCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
        var lastKind: StatementKind? = null
        var expectedPassword: String? = null
        val passwords = mutableListOf<String?>()

        override suspend fun parse(fileName: String, rawContent: ByteArray, kind: StatementKind, password: String?): List<RawStatementEntry> {
            lastKind = kind
            passwords += password
            if (expectedPassword != null && password != expectedPassword) throw StatementPasswordRequiredException(password != null)
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

    private class FakeStatementImportRepository : StatementImportRepository {
        var confirmGate: CompletableDeferred<Unit>? = null
        val imports = mutableListOf<ImportedStatement>()
        val transactions = mutableListOf<Transaction>()
        override fun getImports(): Flow<List<ImportedStatement>> = flowOf(imports)
        override suspend fun confirmImport(
            statementImport: ImportedStatement,
            transactions: List<Transaction>,
            fingerprint: String,
            legacyFingerprint: String?
        ) {
            confirmGate?.await()
            imports += statementImport
            this.transactions += transactions
        }
    }

    private class FakeTransactionsRepository(private val transactions: List<Transaction> = emptyList()) : TransactionsRepository {
        override fun hasTransactions(): Flow<Boolean> = flowOf(transactions.isNotEmpty())
        override fun getTransaction(id: Long): Flow<Transaction?> = flowOf(null)
        override fun getTransactions(period: MonthPeriod): Flow<List<Transaction>> = flowOf(
            transactions.filter { it.date.year == period.year && it.date.monthValue == period.month }
        )
        override fun getMonthlyBalance(period: MonthPeriod): Flow<Money> = flowOf(Money.Zero)
        override suspend fun addTransaction(transaction: Transaction) = Unit
        override suspend fun removeTransaction(transactionId: Long) = Unit
        override suspend fun updateTransaction(transaction: Transaction) = Unit
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
        parserRepository: FakeStatementParserRepository = FakeStatementParserRepository(entries, parserFailure),
        statementImportRepository: FakeStatementImportRepository = FakeStatementImportRepository(),
        existingTransactions: List<Transaction> = emptyList(),
        categorizer: TransactionCategorizer = FakeTransactionCategorizer(suggestions)
    ): ImportStatementViewModel {
        val importStatement = ImportStatementUseCase(parserRepository)
        val synthesizeStatement = SynthesizeStatementUseCase(
            categorizer,
            FakeCategoriesRepository(categories)
        )
        val confirmStatementImport = ConfirmStatementImportUseCase(
            statementImportRepository,
            fakeDateProvider
        )
        return ImportStatementViewModel(
            importStatement,
            synthesizeStatement,
            confirmStatementImport,
            FakeCategoriesRepository(categories),
            FindPotentialOverlapsUseCase(FakeTransactionsRepository(existingTransactions)),
            ioDispatcher = testDispatcher,
            cpuDispatcher = testDispatcher
        )
    }

    @Test
    fun `processing reports measured categorization progress and finishes only in review`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val entries = listOf(entry, entry.copy(description = "Segunda compra"))
        val repository = FakeStatementImportRepository()
        val states = mutableListOf<ImportStatementUiState>()
        val categorizer = object : TransactionCategorizer {
            override suspend fun categorize(entries: List<RawStatementEntry>, categories: List<Category>, onProgress: (Int, Int) -> Unit): Result<List<CategorySuggestion>> {
                onProgress(1, entries.size)
                gate.await()
                onProgress(entries.size, entries.size)
                return Result.success(entries.map { CategorySuggestion(it, mercado, 0.8f) })
            }
        }
        val vm = createViewModel(entries = entries, categorizer = categorizer, statementImportRepository = repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect { states += it } }
        vm.onFileSelected("minha-fatura.pdf", byteArrayOf(1), StatementKind.CARD_BILL)
        assertEquals(ImportProcessingStage.READING, (vm.uiState.value as ImportStatementUiState.Loading).stage)
        testDispatcher.scheduler.runCurrent()
        val processing = vm.uiState.value as ImportStatementUiState.Loading
        assertEquals("minha-fatura.pdf", processing.fileName)
        assertEquals(ImportProcessingStage.CATEGORIZING, processing.stage)
        assertEquals(0.5f, processing.progress!!, 0.0001f)
        assertEquals(1, processing.processedEntries)
        assertTrue(repository.imports.isEmpty())

        gate.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.uiState.value is ImportStatementUiState.ReviewingSuggestions)
        assertEquals(ImportProcessingStage.entries.toList(), states.filterIsInstance<ImportStatementUiState.Loading>().map { it.stage }.distinct())
        assertTrue(states.filterIsInstance<ImportStatementUiState.Loading>().none { it.progress == 1f })
        assertTrue(repository.imports.isEmpty())
    }

    @Test
    fun `cancelled or replaced categorization callbacks cannot replace newer state`() = runTest {
        val callbacks = mutableListOf<(Int, Int) -> Unit>()
        val categorizer = object : TransactionCategorizer {
            override suspend fun categorize(entries: List<RawStatementEntry>, categories: List<Category>, onProgress: (Int, Int) -> Unit): Result<List<CategorySuggestion>> {
                callbacks += onProgress
                CompletableDeferred<Unit>().await()
                return Result.success(emptyList())
            }
        }
        val vm = createViewModel(entries = listOf(entry, entry), categorizer = categorizer)
        vm.onFileSelected("primeiro.pdf", byteArrayOf(1))
        testDispatcher.scheduler.runCurrent()
        assertEquals(1, callbacks.size)
        vm.onStartOver()
        callbacks[0](1, 2)
        assertEquals(ImportStatementUiState.Idle, vm.uiState.value)

        vm.onFileSelected("novo.pdf", byteArrayOf(2))
        testDispatcher.scheduler.runCurrent()
        assertEquals(2, callbacks.size)
        callbacks[0](1, 2)
        val state = vm.uiState.value as ImportStatementUiState.Loading
        assertEquals("novo.pdf", state.fileName)
        assertEquals(0, state.processedEntries)
        callbacks[1](1, 2)
        assertEquals(1, (vm.uiState.value as ImportStatementUiState.Loading).processedEntries)
        vm.onStartOver()
        testDispatcher.scheduler.advanceUntilIdle()
    }

    @Test
    fun `failed categorization never reports completed processing`() = runTest {
        val states = mutableListOf<ImportStatementUiState>()
        val categorizer = object : TransactionCategorizer {
            override suspend fun categorize(entries: List<RawStatementEntry>, categories: List<Category>, onProgress: (Int, Int) -> Unit): Result<List<CategorySuggestion>> {
                onProgress(entries.size, entries.size)
                return Result.failure(IllegalStateException("Falha ao organizar categorias"))
            }
        }
        val vm = createViewModel(categorizer = categorizer)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect { states += it } }
        vm.onFileSelected("fatura.pdf", byteArrayOf(1))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("Falha ao organizar categorias", (vm.uiState.value as ImportStatementUiState.Error).message)
        assertTrue(states.filterIsInstance<ImportStatementUiState.Loading>().none { it.progress == 1f })
        assertTrue(states.none { it is ImportStatementUiState.ReviewingSuggestions || it is ImportStatementUiState.Success })
    }

    @Test
    fun `protected PDF asks for a password retries and never saves before review`() = runTest {
        val parser = FakeStatementParserRepository(listOf(entry)).apply { expectedPassword = "123456" }
        val repository = FakeStatementImportRepository()
        val viewModel = createViewModel(parserRepository = parser, statementImportRepository = repository)
        viewModel.onFileSelected("protegido.pdf", byteArrayOf(1))
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value is ImportStatementUiState.NeedsPassword)
        viewModel.onPasswordSubmitted("errada")
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue((viewModel.uiState.value as ImportStatementUiState.NeedsPassword).error != null)
        viewModel.onPasswordSubmitted("123456")
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value is ImportStatementUiState.ReviewingSuggestions)
        assertTrue(repository.imports.isEmpty())
        assertEquals(listOf(null, "errada", "123456"), parser.passwords)
    }

    @Test
    fun `OCR requires explicit review and editing data resets it`() = runTest {
        val scanned = entry.copy(extractedByOcr = true)
        val viewModel = createViewModel(entries = listOf(scanned), suggestions = listOf(CategorySuggestion(scanned, mercado, 0.8f)))
        viewModel.onFileSelected("scan.pdf", byteArrayOf(1))
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(!(viewModel.uiState.value as ImportStatementUiState.ReviewingSuggestions).canConfirm)
        viewModel.onOcrAcknowledged(true)
        assertTrue((viewModel.uiState.value as ImportStatementUiState.ReviewingSuggestions).canConfirm)
        assertEquals(null, viewModel.onEntryEdited(0, "Compra corrigida", "25,90", "11/01/2026"))
        testDispatcher.scheduler.advanceUntilIdle()
        val state = viewModel.uiState.value as ImportStatementUiState.ReviewingSuggestions
        assertTrue(!state.canConfirm)
        assertEquals(2590L, state.suggestions.single().entry.amount.cents)
        assertEquals(scanned, state.suggestions.single().originalEntry)
        viewModel.onOcrAcknowledged(true)
        assertTrue((viewModel.uiState.value as ImportStatementUiState.ReviewingSuggestions).canConfirm)
    }

    @Test
    fun `editing date value and description recomputes historical overlaps`() = runTest {
        val duplicate = Transaction(9, "Compra corrigida", Money(2590), mercado.id, LocalDate.of(2026, 1, 11), TransactionType.EXPENSE)
        val viewModel = createViewModel(existingTransactions = listOf(duplicate))
        viewModel.onFileSelected("extrato.pdf", byteArrayOf(1))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(null, viewModel.onEntryEdited(0, "Compra corrigida", "25,90", "11/01/2026"))
        assertTrue((viewModel.uiState.value as ImportStatementUiState.ReviewingSuggestions).isCheckingOverlaps)
        testDispatcher.scheduler.advanceUntilIdle()
        val state = viewModel.uiState.value as ImportStatementUiState.ReviewingSuggestions
        assertEquals(1, state.potentialOverlapCount)
        assertTrue(!state.canConfirm)
        viewModel.onOverlapAcknowledged(true)
        assertTrue((viewModel.uiState.value as ImportStatementUiState.ReviewingSuggestions).canConfirm)
    }

    @Test
    fun `invalid corrections keep the original data and reject impossible dates`() = runTest {
        val viewModel = createViewModel()
        viewModel.onFileSelected("extrato.pdf", byteArrayOf(1))
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.onEntryEdited(0, "Compra", "20,00", "31/02/2026") != null)
        assertTrue(viewModel.onEntryEdited(0, "Compra", "20,00", "01/01/0000") != null)
        assertTrue(viewModel.onEntryEdited(0, "Compra", "-20,00", "10/01/2026") != null)
        assertTrue(viewModel.onEntryEdited(0, " ", "20,00", "10/01/2026") != null)
        assertEquals(entry, (viewModel.uiState.value as ImportStatementUiState.ReviewingSuggestions).suggestions.single().entry)
    }

    @Test
    fun `confirming batch cannot be reset or replaced by another file`() = runTest {
        val repository = FakeStatementImportRepository().apply { confirmGate = CompletableDeferred() }
        val viewModel = createViewModel(statementImportRepository = repository)
        viewModel.onFileSelected("original.pdf", byteArrayOf(1))
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.onConfirmImport()
        viewModel.onStartOver()
        viewModel.onFileSelected("outro.pdf", byteArrayOf(2))
        testDispatcher.scheduler.runCurrent()
        val state = viewModel.uiState.value as ImportStatementUiState.ReviewingSuggestions
        assertEquals("original.pdf", state.fileName)
        assertTrue(state.isConfirming)
        repository.confirmGate!!.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value is ImportStatementUiState.Success)
        assertEquals(1, repository.imports.size)
    }

    @Test
    fun `onFileSelected passes selected card bill kind to parser`() = runTest {
        val parser = FakeStatementParserRepository(listOf(entry))
        val viewModel = createViewModel(parserRepository = parser)

        viewModel.onFileSelected("fatura.csv", ByteArray(0), StatementKind.CARD_BILL)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(StatementKind.CARD_BILL, parser.lastKind)
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
    fun `onFileSelected rejects files above size limit`() = runTest {
        val viewModel = createViewModel()
        viewModel.onFileSelected("grande.csv", ByteArray(10 * 1024 * 1024 + 1))
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value as ImportStatementUiState.Error
        assertTrue(state.message.contains("10 MB"))
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
    fun `possible overlap requires explicit acknowledgement`() = runTest {
        val existing = Transaction(1, entry.description, entry.amount, mercado.id, entry.date, entry.type)
        val viewModel = createViewModel(existingTransactions = listOf(existing))

        viewModel.onFileSelected("extrato.csv", ByteArray(0))
        testDispatcher.scheduler.advanceUntilIdle()

        val before = viewModel.uiState.value as ImportStatementUiState.ReviewingSuggestions
        assertEquals(1, before.potentialOverlapCount)
        assertTrue(!before.canConfirm)
        viewModel.onOverlapAcknowledged(true)
        assertTrue((viewModel.uiState.value as ImportStatementUiState.ReviewingSuggestions).canConfirm)
    }

    @Test
    fun `payment can be corrected to transfer during review`() = runTest {
        val transfer = Category(3L, "Transferência", CategoryType.TRANSFER)
        val viewModel = createViewModel(categories = listOf(mercado, transporte, transfer))

        viewModel.onFileSelected("extrato.csv", ByteArray(0))
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.onTypeSelected(0, TransactionType.TRANSFER)

        val suggestion = (viewModel.uiState.value as ImportStatementUiState.ReviewingSuggestions).suggestions.single()
        assertEquals(TransactionType.TRANSFER, suggestion.entry.type)
        assertEquals(TransactionType.EXPENSE, suggestion.originalType)
        assertEquals(transfer, suggestion.suggestedCategory)
    }

    @Test
    fun `correcting a date or value does not validate an uncertain category`() = runTest {
        val vm = createViewModel(suggestions = listOf(CategorySuggestion(entry, mercado, 0.4f)))
        vm.onFileSelected("a.csv", byteArrayOf(1))
        testDispatcher.scheduler.advanceUntilIdle()
        vm.onEntryEdited(0, "Compra corrigida", "20,00", "11/01/2026")
        testDispatcher.scheduler.advanceUntilIdle()
        val state = vm.uiState.value as ImportStatementUiState.ReviewingSuggestions
        assertEquals(listOf(0), state.indicesFor(ImportReviewFilter.UNCERTAIN))
        assertEquals(0.4f, state.suggestions.single().confidence, 0.0001f)
        assertTrue(!state.suggestions.single().rememberCorrection)
    }

    @Test
    fun `excluding a historical overlap saves only included rows and reinclusion resets acknowledgment`() = runTest {
        val other = entry.copy(description = "Outra compra", amount = Money(2000))
        val existing = Transaction(1, entry.description, entry.amount, mercado.id, entry.date, entry.type,
            sourceLabel = "Cartão pessoal")
        val repository = FakeStatementImportRepository()
        val vm = createViewModel(entries = listOf(entry, other), suggestions = listOf(
            CategorySuggestion(entry, mercado, 0.9f), CategorySuggestion(other, transporte, 0.9f)
        ), existingTransactions = listOf(existing), statementImportRepository = repository)
        vm.onFileSelected("a.csv", byteArrayOf(1))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf(existing), (vm.uiState.value as ImportStatementUiState.ReviewingSuggestions).overlapMatches[0])
        vm.onEntryExcluded(0, true)
        var state = vm.uiState.value as ImportStatementUiState.ReviewingSuggestions
        assertEquals(0, state.potentialOverlapCount)
        assertEquals(Money(2000), state.expenseTotal)
        assertTrue(state.canConfirm)
        vm.onEntryExcluded(0, false)
        state = vm.uiState.value as ImportStatementUiState.ReviewingSuggestions
        assertEquals(1, state.potentialOverlapCount)
        assertTrue(!state.canConfirm)
        vm.onEntryExcluded(0, true)
        vm.onConfirmImport()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf(other.description), repository.transactions.map { it.description })
        assertEquals(ImportStatementUiState.Success(1), vm.uiState.value)
    }

    @Test
    fun `bulk categorization uses stable indices after filtering and teaches only selected rows`() = runTest {
        val entries = listOf(entry, entry.copy(description = "Outra compra"), entry.copy(description = "Terceira compra"))
        val vm = createViewModel(entries = entries, suggestions = entries.mapIndexed { index, item ->
            CategorySuggestion(item, if (index == 1) mercado else null, if (index == 1) 0.9f else 0.2f)
        })
        vm.onFileSelected("a.csv", byteArrayOf(1))
        testDispatcher.scheduler.advanceUntilIdle()
        vm.onFilterSelected(ImportReviewFilter.UNCATEGORIZED)
        vm.onSelectionModeChanged(true)
        vm.onSelectVisibleEntries()
        assertEquals(setOf(0, 2), (vm.uiState.value as ImportStatementUiState.ReviewingSuggestions).selectedIndices)
        vm.onBulkCategorySelected(transporte)
        val state = vm.uiState.value as ImportStatementUiState.ReviewingSuggestions
        assertEquals(listOf(transporte, mercado, transporte), state.suggestions.map { it.suggestedCategory })
        assertEquals(listOf(true, false, true), state.suggestions.map { it.rememberCorrection })
        assertEquals(entries, state.suggestions.map { it.originalEntry })
        assertTrue(state.visibleIndices.isEmpty())
        assertTrue(state.selectedIndices.isEmpty())
        assertTrue(state.canConfirm)
    }

    @Test
    fun `bulk category cannot change mixed transaction types or excluded rows`() = runTest {
        val income = Category(3, "Salário", CategoryType.INCOME)
        val entries = listOf(entry, entry.copy(description = "Recebimento", type = TransactionType.INCOME))
        val vm = createViewModel(entries = entries, suggestions = listOf(
            CategorySuggestion(entries[0], mercado, 0.9f), CategorySuggestion(entries[1], income, 0.9f)
        ), categories = listOf(mercado, transporte, income))
        vm.onFileSelected("a.csv", byteArrayOf(1))
        testDispatcher.scheduler.advanceUntilIdle()
        vm.onSelectionModeChanged(true)
        vm.onSelectVisibleEntries()
        vm.onBulkCategorySelected(transporte)
        assertEquals(mercado, (vm.uiState.value as ImportStatementUiState.ReviewingSuggestions).suggestions[0].suggestedCategory)
        vm.onEntryExcluded(1, true)
        vm.onEntrySelectionChanged(1, true)
        vm.onBulkCategorySelected(transporte)
        val state = vm.uiState.value as ImportStatementUiState.ReviewingSuggestions
        assertEquals(listOf(transporte, income), state.suggestions.map { it.suggestedCategory })
        vm.onSelectionModeChanged(true)
        vm.onSelectVisibleEntries()
        vm.onFilterSelected(ImportReviewFilter.EXCLUDED)
        assertTrue((vm.uiState.value as ImportStatementUiState.ReviewingSuggestions).selectedIndices.isEmpty())
    }

    @Test
    fun `onConfirmImport persists transaction and import history`() = runTest {
        val statementImportRepository = FakeStatementImportRepository()
        val viewModel = createViewModel(
            statementImportRepository = statementImportRepository
        )

        viewModel.onFileSelected("extrato.csv", ByteArray(0))
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.onConfirmImport()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, statementImportRepository.transactions.size)
        assertEquals(1, statementImportRepository.imports.size)
        assertEquals(ImportStatementUiState.Success(1), viewModel.uiState.value)
    }
}
