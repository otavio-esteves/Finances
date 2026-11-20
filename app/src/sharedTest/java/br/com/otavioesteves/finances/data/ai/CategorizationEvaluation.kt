package br.com.otavioesteves.finances.data.ai

import br.com.otavioesteves.finances.domain.DateProvider
import br.com.otavioesteves.finances.domain.ai.TransactionCategorizer
import br.com.otavioesteves.finances.domain.model.*
import br.com.otavioesteves.finances.domain.repository.*
import br.com.otavioesteves.finances.domain.usecase.SynthesizeStatementUseCase
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.junit.Assert.*
import java.io.File
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicLong

/** Public synthetic holdout, separate from explicit correction training. No native model is simulated. */
class CategorizationEvaluation {
    @Serializable data class Case(val description: String, val type: TransactionType = TransactionType.EXPENSE,
        val category: String? = null, val correctedType: TransactionType = type, val cohort: String = "unseen")
    @Serializable data class ClassScore(val support: Int, val precision: Double?, val recall: Double?, val f1: Double?, val accuracy: Double? = recall)
    @Serializable data class Report(val mode: String, val cases: Int, val repeats: Int, val accuracy: Double,
        val uncategorizedRate: Double, val correctionRate: Double, val reviewRate: Double,
        val highConfidenceErrors: Int, val highConfidenceErrorRate: Double?, val byCategory: Map<String, ClassScore>,
        val byCohort: Map<String, Double>, val sources: Map<String, Int>, val meanBatchMs: Double,
        val p50BatchMs: Double, val p95BatchMs: Double, val amortizedMsPerTransaction: Double,
        val heapBeforeBytes: Long, val heapAfterBytes: Long, val observedProcessHeapPeakBytes: Long)

    private val json = Json { prettyPrint = true; encodeDefaults = true }
    private val date = LocalDate.of(2026, 1, 15)
    val categories = listOf("Alimentação", "Transporte", "Saúde", "Moradia", "Lazer", "Educação", "Outros")
        .mapIndexed { i, name -> Category(i + 1L, name, CategoryType.EXPENSE) } +
        listOf(Category(8, "Salário", CategoryType.INCOME), Category(9, "Investimentos", CategoryType.INCOME),
            Category(10, "Transferência", CategoryType.TRANSFER))
    val dates = object : DateProvider {
        override fun getCurrentDate() = date
        override fun getCurrentDateTime() = date.atStartOfDay()
        override fun getCurrentMonthPeriod() = MonthPeriod(date.year, date.monthValue)
    }
    private val catalog = object : CategoriesRepository {
        override fun getCategories() = flowOf(categories)
        override fun getCategorySummaries(period: MonthPeriod) = flowOf(emptyList<CategorySummary>())
    }
    private fun entry(c: Case) = RawStatementEntry(c.description, Money(1234), date, c.type)
    private fun label(category: String?, type: TransactionType) = "${type.name}:${category ?: "<sem categoria>"}"

    fun corpusDigest(cases: List<Case>) = digest(json.encodeToString(cases))
    fun catalogDigest() = digest(categories.joinToString("\n") { "${it.id}|${it.type}|${it.name}" })
    private fun digest(value: String) = java.security.MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    fun runJvmBenchmark() = runBlocking {
        val cases = json.decodeFromString<List<Case>>(File("../samples/categorization/corpus.json").readText())
        require(cases.size >= 40 && cases.map { it.description }.distinct().size == cases.size)
        val training = listOf(
            CategoryMemory(categoryMemoryKey("PAG*ARMAZEM ALFA 001"), TransactionType.EXPENSE, 1),
            CategoryMemory(categoryMemoryKey("LOJA VIRTUAL PARCELA 01/03"), TransactionType.EXPENSE, 5),
            CategoryMemory(categoryMemoryKey("RESGATE CDB 001"), TransactionType.INCOME, 10))
        val memory = object : CategoryMemoryRepository {
            override suspend fun getMemories() = training
            override suspend fun clear() = Unit
        }
        val abstain = object : TransactionCategorizer {
            override suspend fun categorize(entries: List<RawStatementEntry>, categories: List<Category>, onProgress: (Int, Int) -> Unit) =
                Result.success(entries.map { CategorySuggestion(it, null, 0f) })
        }
        val rules = RuleBasedLocalAiRepository(dates)
        val modes = linkedMapOf("rules" to rules, "memory_only" to RememberingTransactionCategorizer(abstain, memory),
            "combined_rules" to RememberingTransactionCategorizer(rules, memory))
        val reports = modes.map { (name, resolver) -> evaluate(name, resolver, cases) }
        val directory = File("build/reports/categorization").apply { mkdirs() }
        File(directory, "results.json").writeText(json.encodeToString(reports))
        File(directory, "environment.json").writeText(buildJsonObject {
            put("corpusSha256", corpusDigest(cases)); put("catalogSha256", catalogDigest())
            put("caseCount", cases.size); put("timestampUtc", java.time.Instant.now().toString())
            put("javaVersion", System.getProperty("java.version")); put("vm", System.getProperty("java.vm.name"))
            put("os", System.getProperty("os.name")); put("arch", System.getProperty("os.arch"))
            put("availableProcessors", Runtime.getRuntime().availableProcessors())
            put("implementationLabel", System.getenv("FINANCES_BENCHMARK_LABEL") ?: "local-workspace")
            put("memoryMetric", "whole-process Java heap, sampled every 20ms; not native allocation")
        }.toString())
        File(directory, "model-status.json").writeText("""{"status":"not_run","reason":"JVM benchmark: native LiteRT-LM requires supported Android hardware and a compatible real bundle. Use CategorizationDeviceBenchmarkTest with categorizationBenchmark=true; no model metrics were fabricated."}""")
        val combined = reports.single { it.mode == "combined_rules" }
        // These known synthetic cases are a regression contract; candidate evaluators remain unrestricted.
        assertTrue("Rules lost supported corpus cases", reports.single { it.mode == "rules" }.accuracy >= 55.0 / 58.0)
        assertEquals("Combined flow lost supported corpus cases", 1.0, combined.accuracy, 0.0)
        assertEquals(1.0, combined.byCohort.getValue("seen_correction"), 0.0)
        // Explicit corrections must never merge installment numbers or generic transfers.
        val output = SynthesizeStatementUseCase(modes.getValue("combined_rules"), catalog)(cases.map(::entry))
        val unseen = cases.indexOfFirst { it.description == "LOJA VIRTUAL PARCELA 02/03" }
        assertNotEquals(SuggestionSource.MEMORY, output[unseen].source)
        val generic = cases.indexOfFirst { it.description == "RESGATE CDB 002" }
        assertEquals(TransactionType.INCOME, output[generic].entry.type)
        println("Categorization reports: ${directory.absolutePath}")
    }

    suspend fun evaluate(name: String, resolver: TransactionCategorizer, cases: List<Case>): Report = coroutineScope {
        val entries = cases.map(::entry)
        suspend fun runBatch(batch: List<RawStatementEntry>) = if (name.startsWith("combined"))
            SynthesizeStatementUseCase(resolver, catalog)(batch, StatementKind.CARD_BILL)
            else resolver.categorize(batch, categories).getOrThrow()
        runBatch(entries) // Explicit unmeasured warm-up.
        val runtime = Runtime.getRuntime()
        fun heap() = runtime.totalMemory() - runtime.freeMemory()
        val before = heap()
        val peak = AtomicLong(before)
        val sampler = launch(Dispatchers.Default) { while (isActive) { peak.accumulateAndGet(heap(), ::maxOf); delay(20) } }
        val timings = mutableListOf<Double>()
        var output = emptyList<CategorySuggestion>()
        val repeats = 5
        try {
            repeat(repeats) {
                val result = mutableListOf<CategorySuggestion>()
                for (batch in entries.chunked(20)) {
                    val start = System.nanoTime()
                    result += runBatch(batch)
                    timings += (System.nanoTime() - start) / 1_000_000.0
                }
                output = result
            }
        } finally { peak.accumulateAndGet(heap(), ::maxOf); sampler.cancelAndJoin() }
        val after = heap()
        peak.accumulateAndGet(after, ::maxOf)
        assertEquals(entries.size, output.size)
        output.forEachIndexed { index, suggestion ->
            assertEquals(entries[index].amount, suggestion.entry.amount)
            assertEquals(entries[index].date, suggestion.entry.date)
            assertEquals(entries[index].description, suggestion.entry.description)
        }
        val expected = cases.map { label(it.category, it.correctedType) }
        val actual = output.map { label(it.suggestedCategory?.name, it.entry.type) }
        fun correct(index: Int) = expected[index] == actual[index]
        val scores = (expected + actual).toSortedSet().associateWith { label ->
            val tp = cases.indices.count { expected[it] == label && actual[it] == label }
            val predicted = actual.count { it == label }; val support = expected.count { it == label }
            val precision = if (predicted == 0) null else tp.toDouble() / predicted
            val recall = if (support == 0) null else tp.toDouble() / support
            ClassScore(support, precision, recall, if (predicted + support == 0) null else 2.0 * tp / (predicted + support))
        }
        val high = cases.indices.filter { output[it].confidence >= .75f && output[it].suggestedCategory != null }
        val sorted = timings.sorted()
        val count = cases.size.toDouble()
        Report(name, cases.size, repeats, cases.indices.count(::correct) / count,
            output.count { it.suggestedCategory == null } / count,
            cases.indices.count { !correct(it) || output[it].suggestedCategory == null } / count,
            output.count { it.suggestedCategory == null || it.confidence < .75f } / count,
            high.count { !correct(it) }, if (high.isEmpty()) null else high.count { !correct(it) }.toDouble() / high.size,
            scores, cases.groupBy { it.cohort }.mapValues { (cohort, values) ->
                cases.indices.count { cases[it].cohort == cohort && correct(it) }.toDouble() / values.size },
            output.groupingBy { it.source.name }.eachCount(), timings.average(), sorted[sorted.size / 2],
            sorted[((sorted.size - 1) * .95).toInt()], timings.sum() / (cases.size * repeats), before, after, peak.get())
            .also { println("$name accuracy=${it.accuracy}, highConfidenceErrors=${it.highConfidenceErrors}, amortizedMs=${it.amortizedMsPerTransaction}") }
    }
}
