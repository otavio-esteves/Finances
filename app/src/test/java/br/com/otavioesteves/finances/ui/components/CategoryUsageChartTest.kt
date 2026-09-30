package br.com.otavioesteves.finances.ui.components

import br.com.otavioesteves.finances.domain.model.Money
import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryUsageChartTest {
    @Test
    fun centerAmountUsesCompactUnitsForLargeTotals() {
        assertEquals("R$ 1,2 mi", chartCenterAmount(Money.fromCents(123_456_789)))
        assertEquals("R$ 125 mil", chartCenterAmount(Money.fromCents(12_500_000)))
    }
}
