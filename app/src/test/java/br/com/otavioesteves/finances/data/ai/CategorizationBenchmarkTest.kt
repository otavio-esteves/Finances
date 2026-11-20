package br.com.otavioesteves.finances.data.ai

import org.junit.Test

class CategorizationBenchmarkTest {
    @Test fun benchmarkRulesMemoryAndEffectiveFlow() = CategorizationEvaluation().runJvmBenchmark()
    @Test fun metricsCountWrongHighConfidenceAndSafeAbstentionSeparately() = kotlinx.coroutines.runBlocking {
        val evaluation = CategorizationEvaluation()
        val cases = listOf(CategorizationEvaluation.Case("Ambígua", category = "Alimentação"),
            CategorizationEvaluation.Case("Farmácia", category = "Saúde"),
            CategorizationEvaluation.Case("Crédito genérico", type = br.com.otavioesteves.finances.domain.model.TransactionType.INCOME))
        val health = evaluation.categories.single { it.name == "Saúde" }
        val resolver = object : br.com.otavioesteves.finances.domain.ai.TransactionCategorizer {
            override suspend fun categorize(entries: List<br.com.otavioesteves.finances.domain.model.RawStatementEntry>,
                categories: List<br.com.otavioesteves.finances.domain.model.Category>, onProgress: (Int, Int) -> Unit) =
                Result.success(entries.mapIndexed { index, entry -> br.com.otavioesteves.finances.domain.model.CategorySuggestion(
                    entry, if (index < 2) health else null, listOf(.9f, .6f, 0f)[index]) })
        }
        val report = evaluation.evaluate("metric_fixture", resolver, cases)
        org.junit.Assert.assertEquals(2.0 / 3, report.accuracy, .000001)
        org.junit.Assert.assertEquals(1.0 / 3, report.uncategorizedRate, .000001)
        org.junit.Assert.assertEquals(2.0 / 3, report.correctionRate, .000001)
        org.junit.Assert.assertEquals(2.0 / 3, report.reviewRate, .000001)
        org.junit.Assert.assertEquals(1, report.highConfidenceErrors)
        org.junit.Assert.assertEquals(1.0, report.highConfidenceErrorRate!!, .000001)
        val healthScore = report.byCategory.getValue("EXPENSE:Saúde")
        org.junit.Assert.assertEquals(.5, healthScore.precision!!, .000001)
        org.junit.Assert.assertEquals(1.0, healthScore.recall!!, .000001)
        org.junit.Assert.assertEquals(2.0 / 3, healthScore.f1!!, .000001)
        val food = report.byCategory.getValue("EXPENSE:Alimentação")
        org.junit.Assert.assertEquals(0.0, food.f1!!, .000001)
        org.junit.Assert.assertEquals(0.0, food.accuracy!!, .000001)
    }

}
