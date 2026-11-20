package br.com.otavioesteves.finances.data.ai

import android.os.Debug
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.otavioesteves.finances.domain.ai.*
import br.com.otavioesteves.finances.domain.model.*
import br.com.otavioesteves.finances.domain.repository.CategoryMemoryRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.junit.Assume.assumeTrue
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicLong

/** Opt-in uses the SAME evaluator/corpus as JVM; no financial DB or user correction memory is read. */
@RunWith(AndroidJUnit4::class)
class CategorizationDeviceBenchmarkTest {
    @Test fun compareOnDeviceWithOptionalRealModel() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("categorizationBenchmark") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val json = Json { prettyPrint = true; encodeDefaults = true }
        val cases = json.decodeFromString<List<CategorizationEvaluation.Case>>(
            instrumentation.context.assets.open("corpus.json").bufferedReader().use { it.readText() })
        val evaluation = CategorizationEvaluation()
        val memory = object : CategoryMemoryRepository {
            override suspend fun clear() = Unit
            override suspend fun getMemories() = listOf(
                CategoryMemory(categoryMemoryKey("PAG*ARMAZEM ALFA 001"), TransactionType.EXPENSE, 1),
                CategoryMemory(categoryMemoryKey("LOJA VIRTUAL PARCELA 01/03"), TransactionType.EXPENSE, 5),
                CategoryMemory(categoryMemoryKey("RESGATE CDB 001"), TransactionType.INCOME, 10))
        }
        val abstain = object : TransactionCategorizer {
            override suspend fun categorize(entries: List<RawStatementEntry>, categories: List<Category>, onProgress: (Int, Int) -> Unit) =
                Result.success(entries.map { CategorySuggestion(it, null, 0f) })
        }
        val rules = RuleBasedLocalAiRepository(evaluation.dates)
        val modes = linkedMapOf<String, TransactionCategorizer>("rules" to rules,
            "memory_only" to RememberingTransactionCategorizer(abstain, memory),
            "combined_rules" to RememberingTransactionCategorizer(rules, memory))
        val session = LiteRtEngineSession()
        val path = InstrumentationRegistry.getArguments().getString("validationModelPath")
        val unsupported = AndroidDeviceCapabilityGate(context).check()
        var modelDigest: String? = null
        var modelStatus = "not_run: ${unsupported?.name ?: "bundle not supplied"}"
        if (unsupported == null && !path.isNullOrBlank()) {
            val file = File(path)
            assertTrue("Compatible real bundle must be readable", file.isFile && file.canRead() && file.length() > 0)
            val hash = java.security.MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(65_536)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    hash.update(buffer, 0, count)
                }
            }
            modelDigest = hash.digest().joinToString("") { "%02x".format(it) }
            val engine = object : LocalAiEngine {
                override val state = MutableStateFlow<AiEngineState>(AiEngineState.Ready(
                    InstalledModel("benchmark", "benchmark", ModelSource.USER_IMPORTED, false, file.absolutePath)))
                override suspend fun warmUp() = Result.success(Unit)
                override suspend fun release() = Unit
            }
            // An abstaining fallback exposes model coverage separately from the effective rules fallback.
            modes["model_only"] = LiteRtTransactionCategorizer(engine, abstain, engineSession = session)
            modes["combined_model"] = RememberingTransactionCategorizer(
                LiteRtTransactionCategorizer(engine, rules, engineSession = session), memory)
            modelStatus = "executed_attempt: inspect sources.AI; full fallback is not model quality evidence"
        }
        val reports = mutableListOf<CategorizationEvaluation.Report>()
        val memoryReports = mutableMapOf<String, JsonObject>()
        try {
            for ((name, resolver) in modes) {
                val before = Debug.getPss().toLong() * 1024
                val peak = AtomicLong(before)
                val sampler = launch(Dispatchers.Default) { while (isActive) {
                    peak.accumulateAndGet(Debug.getPss().toLong() * 1024, ::maxOf); delay(100)
                } }
                try { reports += evaluation.evaluate(name, resolver, cases) }
                finally { peak.accumulateAndGet(Debug.getPss().toLong() * 1024, ::maxOf); sampler.cancelAndJoin() }
                val after = Debug.getPss().toLong() * 1024
                peak.accumulateAndGet(after, ::maxOf)
                memoryReports[name] = buildJsonObject {
                    put("pssBeforeBytes", before); put("pssAfterBytes", after)
                    put("observedPssPeakBytes", peak.get()); put("sampleIntervalMs", 100)
                }
            }
        } finally { withContext(NonCancellable) { session.release() } }
        val output = File(context.filesDir, "categorization-benchmark").apply { mkdirs() }
        File(output, "results.json").writeText(json.encodeToString(reports))
        File(output, "device.json").writeText(buildJsonObject {
            put("corpusSha256", evaluation.corpusDigest(cases)); put("catalogSha256", evaluation.catalogDigest())
            put("modelSha256", modelDigest?.let { JsonPrimitive(it) } ?: JsonNull)
            put("implementationLabel", InstrumentationRegistry.getArguments().getString("implementationLabel") ?: "local-workspace")
            put("abi", android.os.Build.SUPPORTED_ABIS.joinToString(","))
            put("timestampUtc", java.time.Instant.now().toString())
            put("modelStatus", modelStatus); put("sdk", android.os.Build.VERSION.SDK_INT)
            put("memory", JsonObject(memoryReports)); put("note", "Synthetic data only; PSS is whole process, includes warmup/native initialization, sampled peak may miss spikes.")
        }.toString())
        instrumentation.sendStatus(0, android.os.Bundle().apply {
            putString("benchmark_directory", output.absolutePath)
        })
    }
}
