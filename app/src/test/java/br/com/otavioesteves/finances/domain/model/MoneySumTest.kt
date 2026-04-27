package br.com.otavioesteves.finances.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class MoneySumTest {
    @Test
    fun fold_sumsPositiveAmounts() {
        val result = listOf(
            Money.fromCents(1_000),
            Money.fromCents(250),
            Money.fromCents(99)
        ).fold(Money.Zero, Money::plus)

        assertEquals(1_349L, result.cents)
    }

    @Test
    fun fold_sumsPositiveAndNegativeAmounts() {
        val result = listOf(
            Money.fromCents(2_000),
            Money.fromCents(-500),
            Money.fromCents(-250)
        ).fold(Money.Zero, Money::plus)

        assertEquals(1_250L, result.cents)
    }
}
