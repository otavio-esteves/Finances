package br.com.otavioesteves.finances.ui.components

import androidx.compose.ui.graphics.Color
import br.com.otavioesteves.finances.domain.model.Category
import br.com.otavioesteves.finances.domain.model.CategorySummary
import br.com.otavioesteves.finances.domain.model.CategoryType
import br.com.otavioesteves.finances.domain.model.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryUsageChartTest {
    @Test
    fun centerAmountUsesCompactUnitsForLargeTotals() {
        assertEquals("R$ 1,2 mi", chartCenterAmount(Money.fromCents(123_456_789)))
        assertEquals("R$ 125 mil", chartCenterAmount(Money.fromCents(12_500_000)))
    }

    @Test
    fun pieAndDonutSlicesKeepExpenseTotalsAndGroupOnlyTheTail() {
        val summaries = listOf(
            summary(1, "Moradia", 30_000), summary(2, "Alimentação", 20_000),
            summary(3, "Transporte", 10_000), summary(4, "Saúde", 5_000),
            summary(5, "Salário", 100_000, CategoryType.INCOME),
            summary(6, "Transferência", 40_000, CategoryType.TRANSFER), summary(7, "Lazer", 0)
        )
        val slices = buildChartSlices(summaries, listOf(Color.Red, Color.Green, Color.Blue), Color.Gray)
        assertEquals(listOf("Moradia", "Alimentação", "Outros"), slices.map { it.label })
        assertEquals(listOf(30_000L, 20_000L, 15_000L), slices.map { it.amount.cents })
        assertEquals(1f, slices.sumOf { it.fraction.toDouble() }.toFloat(), 0.00001f)
        assertEquals(Color.Gray, slices.last().color)
        assertTrue(slices.all { it.fraction.isFinite() && it.fraction > 0f })
    }

    @Test
    fun noExpensesProducesNoSlices() {
        val slices = buildChartSlices(listOf(summary(1, "Salário", 100_000, CategoryType.INCOME)), listOf(Color.Red), Color.Gray)
        assertTrue(slices.isEmpty())
    }

    @Test
    fun singleCategoryOccupiesTheWholeCircle() {
        val slices = buildChartSlices(listOf(summary(1, "Alimentação", 500)), listOf(Color.Red), Color.Gray)
        assertEquals(1, slices.size)
        assertEquals(1f, slices.single().fraction, 0f)
    }

    private fun summary(id: Long, name: String, cents: Long, type: CategoryType = CategoryType.EXPENSE) =
        CategorySummary(Category(id, name, type), Money.fromCents(cents))
}
