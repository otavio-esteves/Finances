package br.com.otavioesteves.finances.presentation.importstatement

import br.com.otavioesteves.finances.domain.model.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.time.LocalDate

/** Measures derived review data, not Compose drawing, FPS, OCR or physical-device performance. */
class ImportReviewSummaryBenchmarkTest {
    private val categories = listOf(Category(1, "Despesa", CategoryType.EXPENSE),
        Category(2, "Receita", CategoryType.INCOME), Category(3, "Transferência", CategoryType.TRANSFER))
    private fun state(): ImportStatementUiState.ReviewingSuggestions {
        val suggestions = (0 until 6000).map { index ->
            val category = categories[index % 3]
            CategorySuggestion(RawStatementEntry("Sintético $index", Money(1234), LocalDate.of(2026, 1, 1),
                TransactionType.valueOf(category.type.name)), category, .8f)
        }
        return ImportStatementUiState.ReviewingSuggestions("sintético.csv", suggestions, categories,
            excludedIndices = (0 until 6000 step 10).toSet())
    }
    private fun read(state: ImportStatementUiState.ReviewingSuggestions): Long =
        state.expenseTotal!!.cents + state.incomeTotal!!.cents + state.transferTotal!!.cents +
            TransactionType.entries.sumOf { state.countFor(it).toLong() } + state.includedSuggestions.size +
            (if (state.canConfirm) 1 else 0) + (if (state.amountError == null) 1 else 0)

    @Test fun profileDerivedSummaryForSixThousandRows() {
        val state = state()
        val expected = 6_663_600L + 5400 + 5400 + 2
        repeat(3) { assertEquals(expected, read(state.copy(sourceLabel = "warmup $it"))) }
        val cold = mutableListOf<Double>()
        val warm = mutableListOf<Double>()
        repeat(30) { index ->
            val copy = state.copy(sourceLabel = "profile $index")
            val started = System.nanoTime()
            assertEquals(expected, read(copy))
            cold += (System.nanoTime() - started) / 1_000_000.0
            val repeated = System.nanoTime()
            assertEquals(expected, read(copy))
            warm += (System.nanoTime() - repeated) / 1_000_000.0
        }
        val output = File("build/reports/categorization/review-summary.json")
        requireNotNull(output.parentFile).mkdirs()
        output.writeText(buildJsonObject {
            put("rows", 6000); put("included", 5400); put("samples", 30)
            put("coldMeanMs", cold.average()); put("coldMedianMs", cold.sorted()[cold.size / 2])
            put("warmMeanMs", warm.average()); put("warmMedianMs", warm.sorted()[warm.size / 2])
            put("note", "JVM derived summary only; cold=new immutable state, warm=repeat reads; no rendering/FPS claim")
        }.toString())
    }

    @Test fun editsExclusionsAndTypesAlwaysRecomputeTheExactSummary() {
        val original = state()
        assertEquals(2_221_200L, original.expenseTotal!!.cents)
        assertEquals(2_221_200L, original.incomeTotal!!.cents)
        assertEquals(2_221_200L, original.transferTotal!!.cents)
        assertEquals(1800, original.countFor(TransactionType.TRANSFER))
        val edited = original.copy(suggestions = original.suggestions.mapIndexed { index, suggestion -> when (index) {
            1 -> suggestion.copy(entry = suggestion.entry.copy(amount = Money(9999)))
            2 -> suggestion.copy(entry = suggestion.entry.copy(type = TransactionType.EXPENSE), suggestedCategory = categories[0], source = SuggestionSource.USER)
            else -> suggestion
        } })
        assertEquals(2_222_434L, edited.expenseTotal!!.cents)
        assertEquals(2_229_965L, edited.incomeTotal!!.cents)
        assertEquals(2_219_966L, edited.transferTotal!!.cents)
        assertEquals(1801, edited.countFor(TransactionType.EXPENSE))
        assertEquals(1799, edited.countFor(TransactionType.TRANSFER))
        assertTrue(edited.canConfirm)
        assertEquals(2_468_000L, original.copy(excludedIndices = emptySet()).expenseTotal!!.cents)
        assertEquals(2_221_200L, original.expenseTotal!!.cents)
    }
}
