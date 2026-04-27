package br.com.otavioesteves.finances.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
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

    @Test
    fun plus_addsMoneyUsingCents() {
        val result = Money.fromCents(1_500) + Money.fromCents(350)

        assertEquals(1_850L, result.cents)
    }

    @Test
    fun minus_subtractsMoneyUsingCents() {
        val result = Money.fromCents(1_500) - Money.fromCents(350)

        assertEquals(1_150L, result.cents)
    }

    @Test
    fun unaryMinus_invertsSignal() {
        assertEquals(-4_590L, (-Money.fromCents(4_590)).cents)
    }

    @Test
    fun signalChecks_reflectMoneyState() {
        assertTrue(Money.fromCents(10).isPositive())
        assertTrue(Money.fromCents(-10).isNegative())
        assertTrue(Money.Zero.isZero())
        assertFalse(Money.Zero.isPositive())
        assertFalse(Money.Zero.isNegative())
    }

    @Test
    fun plus_throwsOnOverflow() {
        assertThrows(ArithmeticException::class.java) {
            Money.fromCents(Long.MAX_VALUE) + Money.fromCents(1)
        }
    }
}
