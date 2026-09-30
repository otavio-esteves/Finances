package br.com.otavioesteves.finances.domain.usecase

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
import br.com.otavioesteves.finances.domain.model.TransactionOrigin
import br.com.otavioesteves.finances.domain.model.TransactionType
import br.com.otavioesteves.finances.domain.repository.CategoriesRepository
import br.com.otavioesteves.finances.domain.repository.StatementImportRepository
import br.com.otavioesteves.finances.domain.repository.StatementParserRepository
import br.com.otavioesteves.finances.domain.repository.TransactionsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class StatementImportUseCasesTest {

    @Test
    fun importStatement_delegatesToParserRepository() = runBlocking {
        val entries = listOf(
            RawStatementEntry(
                description = "Mercado",
                amount = Money.fromCents(5_000),
                date = LocalDate.of(2026, 1, 10),
                type = TransactionType.EXPENSE
            )
        )
        val repository = FakeStatementParserRepository(entries)
        val bytes = "any content".toByteArray()

        val result = ImportStatementUseCase(repository)("extrato.csv", bytes)

        assertEquals(entries, result)
        assertEquals("extrato.csv", repository.lastFileName)
        assertSame(bytes, repository.lastRawContent)
    }

    @Test
    fun synthesizeStatement_suggestsCategoriesUsingKnownCategories() = runBlocking {
        val entry = RawStatementEntry(
            description = "Mercado",
            amount = Money.fromCents(5_000),
            date = LocalDate.of(2026, 1, 10),
            type = TransactionType.EXPENSE
        )
        val category = Category(id = 1L, name = "Mercado", type = CategoryType.EXPENSE)
        val suggestion = CategorySuggestion(entry = entry, suggestedCategory = category, confidence = 1f)
        val categoriesRepository = FakeCategoriesRepository(listOf(category))
        val transactionCategorizer = FakeTransactionCategorizer(listOf(suggestion))

        val result = SynthesizeStatementUseCase(transactionCategorizer, categoriesRepository)(listOf(entry))

        assertEquals(listOf(suggestion), result)
        assertEquals(listOf(category), transactionCategorizer.lastCategories)
    }

    @Test
    fun confirmStatementImport_persistsTransactionsWithImportedOriginAndRecordsImport() = runBlocking {
        val category = Category(id = 1L, name = "Mercado", type = CategoryType.EXPENSE)
        val entry = RawStatementEntry(
            description = "Mercado",
            amount = Money.fromCents(5_000),
            date = LocalDate.of(2026, 1, 10),
            type = TransactionType.EXPENSE
        )
        val suggestion = CategorySuggestion(entry = entry, suggestedCategory = category, confidence = 1f)
        val statementImportRepository = FakeStatementImportRepository()
        val importedAt = LocalDateTime.of(2026, 1, 15, 10, 0)
        val useCase = ConfirmStatementImportUseCase(
            statementImportRepository,
            FakeDateProvider(importedAt)
        )

        val result = useCase("extrato.csv", listOf(suggestion))

        assertEquals(1, statementImportRepository.addedTransactions.size)
        val persisted = statementImportRepository.addedTransactions.single()
        assertEquals(TransactionOrigin.IMPORTED, persisted.origin)
        assertEquals(entry.description, persisted.description)
        assertEquals(category.id, persisted.categoryId)
        assertEquals(1, statementImportRepository.addedImports.size)
        assertEquals(
            ImportedStatement(id = 0, fileName = "extrato.csv", importedAt = importedAt, transactionCount = 1),
            result
        )
        assertEquals(result, statementImportRepository.addedImports.single())
        assertEquals(64, statementImportRepository.fingerprints.single().length)
    }

    @Test
    fun confirmStatementImport_throwsWhenSuggestionHasNoCategory() = runBlocking {
        val entry = RawStatementEntry(
            description = "Mercado",
            amount = Money.fromCents(5_000),
            date = LocalDate.of(2026, 1, 10),
            type = TransactionType.EXPENSE
        )
        val suggestion = CategorySuggestion(entry = entry, suggestedCategory = null, confidence = 0f)
        val useCase = ConfirmStatementImportUseCase(
            FakeStatementImportRepository(),
            FakeDateProvider(LocalDateTime.of(2026, 1, 15, 10, 0))
        )

        val threw = runCatching { useCase("extrato.csv", listOf(suggestion)) }.isFailure
        assertTrue(threw)
    }

    @Test
    fun confirmStatementImport_rejectsCategoryWithDifferentTransactionType() = runBlocking {
        val entry = RawStatementEntry("Mercado", Money.fromCents(5_000), LocalDate.of(2026, 1, 10), TransactionType.EXPENSE)
        val suggestion = CategorySuggestion(entry, Category(1, "Salário", CategoryType.INCOME), 1f)
        val repository = FakeStatementImportRepository()
        val useCase = ConfirmStatementImportUseCase(repository, FakeDateProvider(LocalDateTime.of(2026, 1, 15, 10, 0)))

        assertTrue(runCatching { useCase("extrato.csv", listOf(suggestion)) }.isFailure)
        assertTrue(repository.addedTransactions.isEmpty())
    }

    @Test
    fun confirmStatementImport_fingerprintIgnoresCategoryCorrection() = runBlocking {
        val entry = RawStatementEntry(
            description = "Mercado",
            amount = Money.fromCents(5_000),
            date = LocalDate.of(2026, 1, 10),
            type = TransactionType.EXPENSE
        )
        val repository = FakeStatementImportRepository()
        val useCase = ConfirmStatementImportUseCase(
            repository,
            FakeDateProvider(LocalDateTime.of(2026, 1, 15, 10, 0))
        )

        useCase("primeiro.csv", listOf(CategorySuggestion(entry, Category(1, "Mercado", CategoryType.EXPENSE), 0.8f)))
        useCase("renomeado.csv", listOf(CategorySuggestion(entry, Category(2, "Alimentação", CategoryType.EXPENSE), 1f)))

        assertEquals(repository.fingerprints[0], repository.fingerprints[1])
    }

    private class FakeStatementParserRepository(
        private val entries: List<RawStatementEntry>
    ) : StatementParserRepository {
        var lastFileName: String? = null
        var lastRawContent: ByteArray? = null

        override suspend fun parse(fileName: String, rawContent: ByteArray): List<RawStatementEntry> {
            lastFileName = fileName
            lastRawContent = rawContent
            return entries
        }
    }

    private class FakeCategoriesRepository(
        private val categories: List<Category>
    ) : CategoriesRepository {
        override fun getCategories(): Flow<List<Category>> = flowOf(categories)
        override fun getCategorySummaries(period: MonthPeriod): Flow<List<CategorySummary>> =
            flowOf(emptyList())
    }

    private class FakeTransactionCategorizer(
        private val suggestions: List<CategorySuggestion>
    ) : TransactionCategorizer {
        var lastCategories: List<Category>? = null

        override suspend fun categorize(
            entries: List<RawStatementEntry>,
            categories: List<Category>,
            onProgress: (done: Int, total: Int) -> Unit
        ): Result<List<CategorySuggestion>> {
            lastCategories = categories
            return Result.success(suggestions)
        }
    }

    private class FakeTransactionsRepository : TransactionsRepository {
        override fun getTransaction(id: Long): Flow<Transaction?> = flowOf(null)
        val addedTransactions = mutableListOf<Transaction>()

        override fun getTransactions(period: MonthPeriod): Flow<List<Transaction>> = flowOf(emptyList())
        override fun getMonthlyBalance(period: MonthPeriod): Flow<Money> = flowOf(Money.Zero)
        override suspend fun addTransaction(transaction: Transaction) {
            addedTransactions += transaction
        }
        override suspend fun removeTransaction(transactionId: Long) = Unit
        override suspend fun updateTransaction(transaction: Transaction) = Unit
    }

    private class FakeStatementImportRepository : StatementImportRepository {
        val addedImports = mutableListOf<ImportedStatement>()
        val addedTransactions = mutableListOf<Transaction>()
        val fingerprints = mutableListOf<String>()

        override fun getImports(): Flow<List<ImportedStatement>> = flowOf(addedImports)
        override suspend fun confirmImport(
            statementImport: ImportedStatement,
            transactions: List<Transaction>,
            fingerprint: String
        ) {
            addedImports += statementImport
            addedTransactions += transactions
            fingerprints += fingerprint
        }
    }

    private class FakeDateProvider(private val dateTime: LocalDateTime) : DateProvider {
        override fun getCurrentMonthPeriod(): MonthPeriod = MonthPeriod(year = dateTime.year, month = dateTime.monthValue)
        override fun getCurrentDate(): LocalDate = dateTime.toLocalDate()
        override fun getCurrentDateTime(): LocalDateTime = dateTime
    }
}
