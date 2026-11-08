package br.com.otavioesteves.finances.presentation.addtransaction

import br.com.otavioesteves.finances.domain.DateProvider
import br.com.otavioesteves.finances.domain.model.Category
import br.com.otavioesteves.finances.domain.model.CategorySummary
import br.com.otavioesteves.finances.domain.model.CategoryType
import br.com.otavioesteves.finances.domain.model.Money
import br.com.otavioesteves.finances.domain.model.MonthPeriod
import br.com.otavioesteves.finances.domain.model.Transaction
import br.com.otavioesteves.finances.domain.model.TransactionOrigin
import br.com.otavioesteves.finances.domain.model.TransactionType
import br.com.otavioesteves.finances.domain.repository.CategoriesRepository
import br.com.otavioesteves.finances.domain.repository.TransactionsRepository
import br.com.otavioesteves.finances.domain.usecase.AddTransactionUseCase
import br.com.otavioesteves.finances.domain.usecase.DeleteTransactionUseCase
import br.com.otavioesteves.finances.domain.usecase.GetTransactionUseCase
import br.com.otavioesteves.finances.domain.usecase.UpdateTransactionUseCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class AddTransactionViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    
    private val fakeDate = LocalDate.of(2025, 12, 15)
    private val fakeDateProvider = object : DateProvider {
        override fun getCurrentDate(): LocalDate = fakeDate
        override fun getCurrentMonthPeriod(): MonthPeriod = MonthPeriod(2025, 12)
        override fun getCurrentDateTime(): java.time.LocalDateTime = fakeDate.atStartOfDay()
    }

    private class FakeTransactionsRepository(
        var transactions: List<Transaction> = emptyList()
    ) : TransactionsRepository {
        override fun hasTransactions(): kotlinx.coroutines.flow.Flow<Boolean> = kotlinx.coroutines.flow.flowOf(false)
        var deletedId: Long? = null
        var shouldFailDelete = false
        var shouldFailSave = false
        var saveValidationError: String? = null
        var saveGate: CompletableDeferred<Unit>? = null
        var insertCalls = 0
        var updateCalls = 0

        override fun getTransaction(id: Long): Flow<Transaction?> = flowOf(transactions.find { it.id == id })
        override fun getTransactions(period: MonthPeriod): Flow<List<Transaction>> = flowOf(transactions.filter {
            it.date.year == period.year && it.date.monthValue == period.month
        })
        override fun getMonthlyBalance(period: MonthPeriod): Flow<Money> = flowOf(Money.Zero)
        override suspend fun addTransaction(transaction: Transaction) {
            insertCalls++
            saveGate?.await()
            if (shouldFailSave) error("Save failed")
            saveValidationError?.let { throw IllegalArgumentException(it) }
            transactions = transactions + transaction
        }
        override suspend fun removeTransaction(transactionId: Long) {
            if (shouldFailDelete) throw Exception("Error")
            deletedId = transactionId
            transactions = transactions.filter { it.id != transactionId }
        }
        override suspend fun updateTransaction(transaction: Transaction) {
            updateCalls++
            saveGate?.await()
            if (shouldFailSave) error("Save failed")
            saveValidationError?.let { throw IllegalArgumentException(it) }
            transactions = transactions.map { if (it.id == transaction.id) transaction else it }
        }
    }

    private class FakeCategoriesRepository : CategoriesRepository {
        var creationFailure: String? = null
        val categories = MutableStateFlow(listOf(Category(1, "Alimentação", CategoryType.EXPENSE)))
        override suspend fun createCategory(name: String, type: CategoryType): Category {
            creationFailure?.let { error(it) }
            return Category(2, name, type).also { categories.value = categories.value + it }
        }
        override fun getCategories(): Flow<List<Category>> = categories
        override fun getCategorySummaries(period: MonthPeriod): Flow<List<CategorySummary>> = flowOf(emptyList())
    }

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `aggregate validation error explains the limit and preserves the form for retry`() = runTest {
        val message = br.com.otavioesteves.finances.domain.model.AMOUNT_TOTAL_LIMIT_MESSAGE
        val repository = FakeTransactionsRepository().apply { saveValidationError = message }
        val viewModel = createViewModel(repository)
        testDispatcher.scheduler.advanceUntilIdle()
        fillNewTransaction(viewModel)
        viewModel.saveTransaction()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(message, viewModel.uiState.value.error)
        assertEquals("Compra", viewModel.uiState.value.description)
        assertFalse(viewModel.uiState.value.isSaving)
        assertFalse(viewModel.uiState.value.isSaveSuccessful)
        repository.saveValidationError = null
        viewModel.saveTransaction()
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isSaveSuccessful)
        assertEquals(1, repository.transactions.size)
    }

    @Test
    fun `loadTransaction with valid ID updates UI state`() = runTest {
        val transaction = createFakeTransaction(1)
        val transRepo = FakeTransactionsRepository(listOf(transaction))
        
        val viewModel = createViewModel(transRepo, transactionId = 1)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Test", viewModel.uiState.value.description)
        assertTrue(viewModel.isEditMode)
    }

    @Test
    fun `editing an older imported transaction preserves its origin`() = runTest {
        val original = createFakeTransaction(7).copy(
            date = LocalDate.of(2025, 8, 3),
            origin = TransactionOrigin.IMPORTED,
            iconKey = "market"
        )
        val repository = FakeTransactionsRepository(listOf(original))
        val viewModel = createViewModel(repository, transactionId = 7)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(original.date, viewModel.uiState.value.date)
        viewModel.onDescriptionChange("Corrigida")
        viewModel.onIconChange("food")
        viewModel.saveTransaction()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Corrigida", repository.transactions.single().description)
        assertEquals(TransactionOrigin.IMPORTED, repository.transactions.single().origin)
        assertEquals("food", repository.transactions.single().iconKey)
        assertTrue(viewModel.uiState.value.isSaveSuccessful)
    }

    @Test
    fun `changing transaction type clears incompatible category`() = runTest {
        val viewModel = createViewModel(FakeTransactionsRepository())
        viewModel.onCategoryChange(Category(1, "Alimentação", CategoryType.EXPENSE))
        viewModel.onTypeChange(TransactionType.INCOME)

        assertEquals(null, viewModel.uiState.value.selectedCategory)
        assertFalse(viewModel.isEditMode)
    }

    @Test
    fun `onDeleteRequest shows confirmation`() = runTest {
        val viewModel = createViewModel(FakeTransactionsRepository())
        viewModel.onDeleteRequest()
        assertTrue(viewModel.uiState.value.showDeleteConfirmation)
    }

    @Test
    fun `onDeleteCancel hides confirmation`() = runTest {
        val viewModel = createViewModel(FakeTransactionsRepository())
        viewModel.onDeleteRequest()
        viewModel.onDeleteCancel()
        assertFalse(viewModel.uiState.value.showDeleteConfirmation)
    }

    @Test
    fun `onDeleteConfirm calls delete use case and emits success`() = runTest {
        val repository = FakeTransactionsRepository(listOf(createFakeTransaction(10)))
        val viewModel = createViewModel(repository, transactionId = 10)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.onDeleteRequest()
        viewModel.onDeleteConfirm()
        testDispatcher.scheduler.advanceUntilIdle()
        
        assertEquals(10L, repository.deletedId)
        assertTrue(viewModel.uiState.value.isSaveSuccessful)
    }

    @Test
    fun `onDeleteConfirm failure emits error`() = runTest {
        val repository = FakeTransactionsRepository(listOf(createFakeTransaction(1))).apply { shouldFailDelete = true }
        val viewModel = createViewModel(repository, transactionId = 1)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.onDeleteRequest()
        viewModel.onDeleteConfirm()
        testDispatcher.scheduler.advanceUntilIdle()
        
        assertNotNull(viewModel.uiState.value.error)
        assertEquals("Erro ao excluir transação", viewModel.uiState.value.error)
    }

    @Test
    fun `repeated saves before during and after insert create only one transaction`() = runTest {
        val repository = FakeTransactionsRepository().apply { saveGate = CompletableDeferred() }
        val viewModel = createViewModel(repository)
        testDispatcher.scheduler.advanceUntilIdle()
        fillNewTransaction(viewModel)

        viewModel.saveTransaction()
        viewModel.saveTransaction()
        assertTrue(viewModel.uiState.value.isSaving)
        testDispatcher.scheduler.runCurrent()
        viewModel.saveTransaction()
        viewModel.onDeleteConfirm()
        assertEquals(1, repository.insertCalls)
        assertTrue(repository.transactions.isEmpty())
        assertEquals(null, repository.deletedId)

        repository.saveGate!!.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.saveTransaction()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, repository.transactions.size)
        assertEquals(1, repository.insertCalls)
        assertTrue(viewModel.uiState.value.isSaveSuccessful)
        assertFalse(viewModel.uiState.value.isSaving)
    }

    @Test
    fun `repeated saves during edit perform only one update`() = runTest {
        val repository = FakeTransactionsRepository(listOf(createFakeTransaction(7))).apply {
            saveGate = CompletableDeferred()
        }
        val viewModel = createViewModel(repository, transactionId = 7)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.onDescriptionChange("Corrigida")

        viewModel.saveTransaction()
        viewModel.saveTransaction()
        testDispatcher.scheduler.runCurrent()
        viewModel.saveTransaction()
        assertEquals(1, repository.updateCalls)
        repository.saveGate!!.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Corrigida", repository.transactions.single().description)
        assertEquals(1, repository.updateCalls)
        assertFalse(viewModel.uiState.value.isSaving)
    }

    @Test
    fun `failed save releases guard and allows retry`() = runTest {
        val repository = FakeTransactionsRepository().apply { shouldFailSave = true }
        val viewModel = createViewModel(repository)
        testDispatcher.scheduler.advanceUntilIdle()
        fillNewTransaction(viewModel)
        viewModel.saveTransaction()
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isSaving)
        assertFalse(viewModel.uiState.value.isSaveSuccessful)
        assertNotNull(viewModel.uiState.value.error)

        repository.shouldFailSave = false
        viewModel.saveTransaction()
        assertEquals(null, viewModel.uiState.value.error)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, repository.transactions.size)
        assertTrue(viewModel.uiState.value.isSaveSuccessful)
    }

    @Test
    fun `invalid form does not claim save or insert anything`() = runTest {
        val repository = FakeTransactionsRepository()
        val viewModel = createViewModel(repository)
        viewModel.saveTransaction()
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isSaving)
        assertEquals(0, repository.insertCalls)
    }

    @Test
    fun `failed edit allows retry and preserves the original transaction`() = runTest {
        val original = createFakeTransaction(7)
        val repository = FakeTransactionsRepository(listOf(original)).apply { shouldFailSave = true }
        val viewModel = createViewModel(repository, transactionId = 7)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.onDescriptionChange("Corrigida")
        viewModel.saveTransaction()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(original, repository.transactions.single())
        assertFalse(viewModel.uiState.value.isSaving)
        assertFalse(viewModel.uiState.value.isSaveSuccessful)

        repository.shouldFailSave = false
        viewModel.saveTransaction()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("Corrigida", repository.transactions.single().description)
        assertEquals(2, repository.updateCalls)
        assertTrue(viewModel.uiState.value.isSaveSuccessful)
    }

    private fun fillNewTransaction(viewModel: AddTransactionViewModel) {
        viewModel.onDescriptionChange("Compra")
        viewModel.onAmountChange("100,00")
        viewModel.onCategoryChange(Category(1, "Alimentação", CategoryType.EXPENSE))
    }

    @Test
    fun `editing a zero imported transaction preserves its value and metadata`() = runTest {
        val original = createFakeTransaction(7).copy(
            amount = Money.Zero,
            origin = TransactionOrigin.IMPORTED,
            date = LocalDate.of(2025, 8, 3)
        )
        val repository = FakeTransactionsRepository(listOf(original))
        val viewModel = createViewModel(repository, transactionId = original.id)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.onDescriptionChange("Descrição corrigida")
        viewModel.onNotesChange("Conferido")
        viewModel.onIconChange("market")

        viewModel.saveTransaction()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(original.copy(description = "Descrição corrigida", notes = "Conferido", iconKey = "market"),
            repository.transactions.single())
        assertTrue(viewModel.uiState.value.isSaveSuccessful)
    }

    @Test
    fun `manual creation still rejects zero and editing rejects negative values`() = runTest {
        val repository = FakeTransactionsRepository(listOf(createFakeTransaction(7)))
        val creating = createViewModel(repository)
        val editing = createViewModel(repository, transactionId = 7)
        testDispatcher.scheduler.advanceUntilIdle()
        fillNewTransaction(creating)
        creating.onAmountChange("0,00")
        creating.saveTransaction()
        editing.onAmountChange("-1,00")
        editing.saveTransaction()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, repository.insertCalls)
        assertEquals(0, repository.updateCalls)
        assertNotNull(creating.uiState.value.amountError)
        assertNotNull(editing.uiState.value.amountError)
    }

    @Test
    fun `editing shows a format error for empty or invalid amounts`() = runTest {
        val repository = FakeTransactionsRepository(listOf(createFakeTransaction(7)))
        val viewModel = createViewModel(repository, transactionId = 7)
        testDispatcher.scheduler.advanceUntilIdle()
        listOf("", "abc", "1,2,3").forEach { invalid ->
            viewModel.onAmountChange(invalid)
            viewModel.saveTransaction()
            assertEquals("Informe um valor válido", viewModel.uiState.value.amountError)
        }
        assertEquals(0, repository.updateCalls)
    }

    @Test
    fun `late categories update the form without discarding edited fields`() = runTest {
        val categories = MutableStateFlow<List<Category>>(emptyList())
        val categoryRepository = object : CategoriesRepository {
            override fun getCategories(): Flow<List<Category>> = categories
            override fun getCategorySummaries(period: MonthPeriod): Flow<List<CategorySummary>> = flowOf(emptyList())
        }
        val original = createFakeTransaction(7)
        val viewModel = createViewModel(FakeTransactionsRepository(listOf(original)), 7, categoryRepository)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(emptyList<Category>(), viewModel.uiState.value.categories)
        viewModel.onDescriptionChange("Descrição digitada")

        val category = Category(1, "Alimentação", CategoryType.EXPENSE)
        categories.value = listOf(category)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(category, viewModel.uiState.value.selectedCategory)
        assertEquals("Descrição digitada", viewModel.uiState.value.description)

        val custom = Category(2, "Educação", CategoryType.EXPENSE)
        categories.value = listOf(category, custom)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf(category, custom), viewModel.uiState.value.categories)
        assertEquals("Descrição digitada", viewModel.uiState.value.description)
    }

    @Test
    fun `creating a category selects it with the current type and preserves the form`() = runTest {
        val repository = FakeCategoriesRepository()
        val viewModel = createViewModel(FakeTransactionsRepository(), categoriesRepository = repository)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.onDescriptionChange("Salário de novembro")
        viewModel.onAmountChange("2000,00")
        viewModel.onIconChange("income")
        viewModel.onTypeChange(TransactionType.INCOME)
        viewModel.showCreateCategory()
        viewModel.createCategory("  Trabalho   extra  ")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(Category(2, "Trabalho extra", CategoryType.INCOME), state.selectedCategory)
        assertTrue(state.categories.contains(state.selectedCategory))
        assertEquals("Salário de novembro", state.description)
        assertEquals("2000,00", state.amount)
        assertEquals("income", state.iconKey)
        assertFalse(state.showCategoryCreation)
        assertFalse(state.isCreatingCategory)
    }

    @Test
    fun `category creation failure keeps the form and allows retry`() = runTest {
        val repository = FakeCategoriesRepository().apply { creationFailure = "Categoria já existe" }
        val viewModel = createViewModel(FakeTransactionsRepository(), categoriesRepository = repository)
        testDispatcher.scheduler.advanceUntilIdle()
        fillNewTransaction(viewModel)
        val originalCategory = viewModel.uiState.value.selectedCategory
        viewModel.showCreateCategory()
        viewModel.createCategory("Duplicada")
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("Categoria já existe", viewModel.uiState.value.categoryCreationError)
        assertTrue(viewModel.uiState.value.showCategoryCreation)
        assertFalse(viewModel.uiState.value.isCreatingCategory)
        assertEquals(originalCategory, viewModel.uiState.value.selectedCategory)
        assertEquals("Compra", viewModel.uiState.value.description)

        repository.creationFailure = null
        viewModel.createCategory("Nova")
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("Nova", viewModel.uiState.value.selectedCategory?.name)
        assertFalse(viewModel.uiState.value.showCategoryCreation)
    }

    private fun createViewModel(
        repository: FakeTransactionsRepository,
        transactionId: Long? = null,
        categoriesRepository: CategoriesRepository = FakeCategoriesRepository()
    ): AddTransactionViewModel {
        return AddTransactionViewModel(
            addTransactionUseCase = AddTransactionUseCase(repository),
            updateTransactionUseCase = UpdateTransactionUseCase(repository),
            getTransactionUseCase = GetTransactionUseCase(repository),
            deleteTransactionUseCase = DeleteTransactionUseCase(repository),
            categoriesRepository = categoriesRepository,
            dateProvider = fakeDateProvider,
            transactionId = transactionId
        )
    }

    private fun createFakeTransaction(id: Long) = Transaction(
        id = id,
        description = "Test",
        amount = Money.fromCents(10000),
        categoryId = 1,
        date = fakeDate,
        type = TransactionType.EXPENSE
    )
}
