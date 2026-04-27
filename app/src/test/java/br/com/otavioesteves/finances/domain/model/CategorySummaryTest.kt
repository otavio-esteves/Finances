package br.com.otavioesteves.finances.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class CategorySummaryTest {
    @Test
    fun totalAmount_usesMoneyTypeBackedByCents() {
        val category = Category(id = 1L, name = "Mercado", type = CategoryType.EXPENSE)
        val totalAmount = Money.fromCents(125_075)

        val summary = CategorySummary(
            category = category,
            totalAmount = totalAmount
        )

        assertSame(Money::class.java, summary.totalAmount::class.java)
        assertEquals(125_075L, summary.totalAmount.cents)
        assertEquals(category, summary.category)
    }
}
