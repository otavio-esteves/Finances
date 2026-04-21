package br.com.otavioesteves.finances.utils

import br.com.otavioesteves.finances.domain.model.Money
import org.junit.Assert.assertEquals
import org.junit.Test

class FormattersTest {
    @Test
    fun formatCurrency_formatsZeroInCents() {
        assertEquals("R$ 0,00", formatCurrency(Money.Zero))
    }

    @Test
    fun formatCurrency_formatsPositiveAmountsInCents() {
        assertEquals("R$ 45,90", formatCurrency(Money.fromCents(4_590)))
    }

    @Test
    fun formatCurrency_formatsValuesAboveOneThousand() {
        assertEquals("R$ 1.250,75", formatCurrency(Money.fromCents(125_075)))
    }
}
