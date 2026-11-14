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
import br.com.otavioesteves.finances.domain.model.StatementKind
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
    fun decodedOfxDescriptionsRetainBothLegacyFingerprintVersionsAndPartialOverlapCandidates() = runBlocking {
        val category = Category(1, "Outros", CategoryType.EXPENSE)
        val oldRaw = RawStatementEntry("A &amp; B", Money(1000), LocalDate.of(2026, 11, 1), TransactionType.EXPENSE)
        val decoded = oldRaw.copy(description = "A & B", legacyOfxDescription = oldRaw.description)
        val repo = FakeStatementImportRepository()
        val confirm = ConfirmStatementImportUseCase(repo, FakeDateProvider(LocalDateTime.of(2026, 11, 1, 9, 0)))
        confirm("old.ofx", listOf(CategorySuggestion(oldRaw, category, 1f)))
        confirm("new.ofx", listOf(CategorySuggestion(decoded, category, 1f)))
        org.junit.Assert.assertNotEquals(repo.fingerprints[0], repo.fingerprints[1])
        assertTrue(repo.fingerprints[0] in repo.compatibilityFingerprints[1])
        assertTrue(repo.compatibilityFingerprints[0].single() in repo.compatibilityFingerprints[1])
        val stored = Transaction(1, oldRaw.description, oldRaw.amount, 1, oldRaw.date, oldRaw.type)
        val matches = FindPotentialOverlapsUseCase(FakeTransactionsRepository(listOf(stored, stored.copy(id = 2, description = decoded.description))))
            .findMatches(listOf(decoded))
        assertEquals(setOf(1L, 2L), matches.getValue(0).map { it.id }.toSet())
    }

    @Test
    fun csvAndOfxCardPaymentsAreTransfersInBothFinancialDirections() = runBlocking {
        val categories = listOf(Category(1, "Outros", CategoryType.EXPENSE), Category(2, "Outros", CategoryType.INCOME),
            Category(3, "Transferência", CategoryType.TRANSFER))
        val date = FakeDateProvider(LocalDateTime.of(2026, 11, 1, 9, 0))
        val synthesize = SynthesizeStatementUseCase(
            br.com.otavioesteves.finances.data.ai.RuleBasedLocalAiRepository(date), FakeCategoriesRepository(categories))
        val parser = br.com.otavioesteves.finances.data.statement.CsvOfxStatementParser()
        for (description in listOf("Pagamento da fatura", "PAGAMENTO DE FATURA", "Pagamento do cartão", "Pagamento\u00a0da\u00a0fatura")) {
            for (kind in StatementKind.entries) {
                val entries = parser.parse("a.csv", "Data;Descrição;Valor\n01/11/2026;$description;-100.00".toByteArray(), kind)
                val result = synthesize(entries).single()
                assertEquals(TransactionType.TRANSFER, result.entry.type)
                assertEquals(categories[2], result.suggestedCategory)
                assertEquals(entries.single().type, result.originalType)
            }
        }
        val ofx = "<OFX><BANKTRANLIST><STMTTRN><DTPOSTED>20261101<TRNAMT>100.00<MEMO>Pagamento do cart&#227;o</STMTTRN></BANKTRANLIST></OFX>"
        assertEquals(TransactionType.TRANSFER, synthesize(parser.parse("a.ofx", ofx.toByteArray())).single().entry.type)
    }

    @Test
    fun paymentRecognitionPreservesRefundsAndExplicitRememberedChoices() = runBlocking {
        val income = Category(1, "Outros", CategoryType.INCOME)
        val transfer = Category(2, "Transferência", CategoryType.TRANSFER)
        for ((description, source) in listOf("Estorno pagamento da fatura" to br.com.otavioesteves.finances.domain.model.SuggestionSource.RULE,
            "Estornos do pagamento da fatura" to br.com.otavioesteves.finances.domain.model.SuggestionSource.RULE,
            "Pagamento da fatura cancelado" to br.com.otavioesteves.finances.domain.model.SuggestionSource.RULE,
            "Pagamento da fatura" to br.com.otavioesteves.finances.domain.model.SuggestionSource.MEMORY)) {
            val raw = RawStatementEntry(description, Money(1000), LocalDate.of(2026, 11, 1), TransactionType.INCOME)
            val expected = CategorySuggestion(raw, income, 1f, source)
            val result = SynthesizeStatementUseCase(FakeTransactionCategorizer(listOf(expected)), FakeCategoriesRepository(listOf(income, transfer)))(listOf(raw))
            assertEquals(listOf(expected), result)
        }
    }

    @Test
    fun excludedRowsStillIdentifyTheFullSourceBatch() = runBlocking {
        val category = Category(1, "Mercado", CategoryType.EXPENSE)
        val first = CategorySuggestion(RawStatementEntry("Mercado", Money(100), LocalDate.of(2026, 1, 1), TransactionType.EXPENSE), category, 1f)
        val second = CategorySuggestion(first.entry.copy(description = "Outra compra"), category, 1f)
        val source = listOf(first, second)
        val repository = FakeStatementImportRepository()
        val useCase = ConfirmStatementImportUseCase(repository, FakeDateProvider(LocalDateTime.of(2026, 1, 15, 10, 0)))
        useCase("a.csv", source)
        useCase("a.csv", listOf(second), fingerprintSuggestions = source)
        assertEquals(repository.fingerprints[0], repository.fingerprints[1])
        assertEquals(1, repository.addedImports.last().transactionCount)
        assertEquals(3, repository.addedTransactions.size)
    }

    @Test
    fun overlapMatchesPreserveAllCandidatesAndOriginalEntryIndicesAcrossMonths() = runBlocking {
        val entry = RawStatementEntry(" Mercado  ABC ", Money(100), LocalDate.of(2026, 1, 10), TransactionType.EXPENSE)
        val existing = Transaction(1, "mercado abc", Money(100), 1, entry.date, TransactionType.EXPENSE)
        val otherMonth = existing.copy(id = 2, date = LocalDate.of(2025, 12, 10))
        val anotherCandidate = existing.copy(id = 3, sourceLabel = "Outro cartão")
        val find = FindPotentialOverlapsUseCase(FakeTransactionsRepository(listOf(existing, otherMonth, anotherCandidate)))
        val entries = listOf(entry.copy(description = "Nova compra"), entry, entry.copy(date = otherMonth.date), entry)
        val matches = find.findMatches(entries)
        assertEquals(setOf(1, 2, 3), matches.keys)
        assertEquals(listOf(existing, anotherCandidate), matches[1])
        assertEquals(listOf(otherMonth), matches[2])
        assertEquals(matches[1], matches[3])
        assertEquals(3, find(entries))
    }

    @Test
    fun fingerprintIgnoresOrderButPreservesRepeatedPurchases() = runBlocking {
        val category = Category(1, "Outros", CategoryType.EXPENSE)
        val first = CategorySuggestion(
            RawStatementEntry("Mercado", Money.fromCents(1000), LocalDate.of(2026, 1, 10), TransactionType.EXPENSE), category, 1f
        )
        val second = first.copy(entry = first.entry.copy(description = "Farmácia"))
        val repository = FakeStatementImportRepository()
        val useCase = ConfirmStatementImportUseCase(repository, FakeDateProvider(LocalDateTime.of(2026, 1, 15, 10, 0)))
        useCase("a.csv", listOf(first, second, first))
        useCase("b.csv", listOf(first, first, second))
        useCase("c.csv", listOf(first, second))
        assertEquals(repository.fingerprints[0], repository.fingerprints[1])
        org.junit.Assert.assertNotEquals(repository.fingerprints[0], repository.fingerprints[2])
    }

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

        val result = ImportStatementUseCase(repository)("fatura.csv", bytes, StatementKind.CARD_BILL)

        assertEquals(entries, result)
        assertEquals("fatura.csv", repository.lastFileName)
        assertSame(bytes, repository.lastRawContent)
        assertEquals(StatementKind.CARD_BILL, repository.lastKind)
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
    fun synthesizeStatement_suggestsCardPaymentAsTransferForReview() = runBlocking {
        val entry = RawStatementEntry(
            "Pagamento da fatura", Money.fromCents(50_000), LocalDate.of(2026, 1, 10), TransactionType.EXPENSE
        )
        val expense = Category(1, "Outros", CategoryType.EXPENSE)
        val transfer = Category(2, "Transferência", CategoryType.TRANSFER)
        val suggestion = CategorySuggestion(entry, expense, 0.8f)

        val result = SynthesizeStatementUseCase(
            FakeTransactionCategorizer(listOf(suggestion)),
            FakeCategoriesRepository(listOf(expense, transfer))
        )(listOf(entry)).single()

        assertEquals(TransactionType.TRANSFER, result.entry.type)
        assertEquals(TransactionType.EXPENSE, result.originalType)
        assertEquals(transfer, result.suggestedCategory)
    }

    @Test
    fun synthesizeStatement_assignsTransferCategoryToPaymentsRecognizedByPdfParser() = runBlocking {
        val entry = RawStatementEntry(
            "Pagamento em 10 JAN", Money.fromCents(50_000), LocalDate.of(2026, 1, 10), TransactionType.TRANSFER
        )
        val transfer = Category(2, "Transferência", CategoryType.TRANSFER)
        val result = SynthesizeStatementUseCase(
            FakeTransactionCategorizer(listOf(CategorySuggestion(entry, null, 0f))),
            FakeCategoriesRepository(listOf(transfer))
        )(listOf(entry)).single()
        assertEquals(transfer, result.suggestedCategory)
        assertEquals(TransactionType.TRANSFER, result.originalType)
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
            ImportedStatement(id = 0, fileName = "extrato.csv", importedAt = importedAt, transactionCount = 1, canUndo = true),
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

    @Test
    fun confirmStatementImport_transferCorrectionPreservesOriginalFingerprint() = runBlocking {
        val entry = RawStatementEntry("Pagamento da fatura", Money.fromCents(40_000), LocalDate.of(2026, 1, 10), TransactionType.EXPENSE)
        val repository = FakeStatementImportRepository()
        val useCase = ConfirmStatementImportUseCase(repository, FakeDateProvider(LocalDateTime.of(2026, 1, 15, 10, 0)))
        val expense = CategorySuggestion(entry, Category(1, "Outros", CategoryType.EXPENSE), 0.8f)
        val transfer = expense.copy(
            entry = entry.copy(type = TransactionType.TRANSFER),
            suggestedCategory = Category(2, "Transferência", CategoryType.TRANSFER)
        )

        useCase("original.csv", listOf(expense))
        useCase("corrigido.csv", listOf(transfer))

        assertEquals(repository.fingerprints[0], repository.fingerprints[1])
        assertEquals(TransactionType.TRANSFER, repository.addedTransactions.last().type)
    }

    @Test
    fun findPotentialOverlaps_detectsPartialOverlapDespiteDescriptionSpacing() = runBlocking {
        val date = LocalDate.of(2026, 1, 10)
        val stored = Transaction(1, "Mercado  Central", Money.fromCents(5_000), 1, date, TransactionType.EXPENSE)
        val entries = listOf(
            RawStatementEntry(" mercado central ", Money.fromCents(5_000), date, TransactionType.EXPENSE),
            RawStatementEntry("Farmácia", Money.fromCents(2_000), date, TransactionType.EXPENSE)
        )

        assertEquals(1, FindPotentialOverlapsUseCase(FakeTransactionsRepository(listOf(stored)))(entries))
    }

    @Test
    fun correctionsPreserveTheOriginalBatchFingerprint() = runBlocking {
        val original = RawStatementEntry("Mercado", Money(1000), LocalDate.of(2026, 1, 10), TransactionType.EXPENSE)
        val category = Category(1, "Mercado", CategoryType.EXPENSE)
        val suggestion = CategorySuggestion(original, category, 0.8f)
        val repository = FakeStatementImportRepository()
        val confirm = ConfirmStatementImportUseCase(repository, FakeDateProvider(LocalDateTime.of(2026, 1, 15, 10, 0)))
        confirm("scan.pdf", listOf(suggestion))
        confirm("scan.pdf", listOf(suggestion.copy(entry = original.copy(amount = Money(2000), description = "Corrigida", date = original.date.plusDays(1)))))
        assertEquals(repository.fingerprints[0], repository.fingerprints[1])
        assertEquals(2000L, repository.addedTransactions.last().amount.cents)
    }

    private class FakeStatementParserRepository(
        private val entries: List<RawStatementEntry>
    ) : StatementParserRepository {
        var lastFileName: String? = null
        var lastRawContent: ByteArray? = null
        var lastKind: StatementKind? = null

        override suspend fun parse(fileName: String, rawContent: ByteArray, kind: StatementKind, password: String?): List<RawStatementEntry> {
            lastFileName = fileName
            lastRawContent = rawContent
            lastKind = kind
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

    private class FakeTransactionsRepository(private val transactions: List<Transaction> = emptyList()) : TransactionsRepository {
        override fun hasTransactions(): Flow<Boolean> = flowOf(transactions.isNotEmpty())
        override fun getTransaction(id: Long): Flow<Transaction?> = flowOf(null)
        val addedTransactions = mutableListOf<Transaction>()

        override fun getTransactions(period: MonthPeriod): Flow<List<Transaction>> =
            flowOf(transactions.filter { it.date.year == period.year && it.date.monthValue == period.month })
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
        val compatibilityFingerprints = mutableListOf<List<String>>()

        override suspend fun confirmReviewedImport(statementImport: ImportedStatement, transactions: List<Transaction>, fingerprint: String,
            legacyFingerprint: String?, memories: List<br.com.otavioesteves.finances.domain.model.CategoryMemory>, additionalLegacyFingerprints: List<String>) {
            compatibilityFingerprints += listOfNotNull(legacyFingerprint) + additionalLegacyFingerprints
            confirmImport(statementImport, transactions, fingerprint, legacyFingerprint)
        }

        override fun getImports(): Flow<List<ImportedStatement>> = flowOf(addedImports)
        override suspend fun confirmImport(
            statementImport: ImportedStatement,
            transactions: List<Transaction>,
            fingerprint: String,
            legacyFingerprint: String?
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
