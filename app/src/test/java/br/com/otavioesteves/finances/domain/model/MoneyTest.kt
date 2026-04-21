package br.com.otavioesteves.finances.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyTest {
    @Test
    fun zero_startsAtZeroCents() {
        assertEquals(0L, Money.Zero.cents)
    }

    @Test
    fun fromCents_preservesProvidedValue() {
        assertEquals(4_590L, Money.fromCents(4_590).cents)
    }
}
