package br.com.otavioesteves.finances.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnnualExpenseChartTest {
    @Test
    fun emptyYearKeepsTwelveZeroMonths() {
        val data = annualChartData(List(12) { 0L })
        assertEquals(0L, data.total.cents)
        assertEquals(List(12) { 0f }, data.fractions)
    }

    @Test
    fun missingMonthsRemainInCalendarPositionsAndShareTheSameScale() {
        val amounts = List(12) { index -> when (index) { 1 -> 5_000L; 7 -> 10_000L; else -> 0L } }
        val data = annualChartData(amounts)
        assertEquals(15_000L, data.total.cents)
        assertEquals(0.5f, data.fractions[1], 0f)
        assertEquals(1f, data.fractions[7], 0f)
        assertTrue(data.fractions.filterIndexed { index, _ -> index != 1 && index != 7 }.all { it == 0f })
    }

    @Test
    fun largeValuesProduceFiniteNormalizedCoordinates() {
        val data = annualChartData(List(12) { if (it == 0) Long.MAX_VALUE else 0L })
        assertEquals(Long.MAX_VALUE, data.total.cents)
        assertEquals(1f, data.fractions.first(), 0f)
        assertTrue(data.fractions.all { it.isFinite() && it in 0f..1f })
    }
}
