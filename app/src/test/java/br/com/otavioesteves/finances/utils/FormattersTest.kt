package br.com.otavioesteves.finances.utils

import br.com.otavioesteves.finances.domain.model.Money
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class FormattersTest {
    @Test
    fun formatCurrency_formatsZeroInCents() {
        assertEquals("R$ 0,00", MoneyFormatter.format(Money.Zero))
    }

    @Test
    fun formatCurrency_formatsPositiveAmountsInCents() {
        assertEquals("R$ 10,50", MoneyFormatter.format(Money.fromCents(1050)))
    }

    @Test
    fun formatCurrency_formatsNegativeAmountsInCents() {
        assertEquals("-R$ 25,90", MoneyFormatter.format(Money.fromCents(-2590)))
    }

    @Test
    fun formatCurrency_formatsValuesAboveOneThousand() {
        assertEquals("R$ 1.250,75", MoneyFormatter.format(Money.fromCents(125_075)))
    }

    @Test
    fun parseMoney_parsesBrazilianFormat() {
        assertEquals(Money.fromCents(125075), MoneyFormatter.parse("1.250,75"))
        assertEquals(Money.fromCents(4590), MoneyFormatter.parse("45,90"))
    }

    @Test
    fun parseMoney_parsesUSFormat() {
        assertEquals(Money.fromCents(125075), MoneyFormatter.parse("1,250.75"))
        assertEquals(Money.fromCents(4590), MoneyFormatter.parse("45.90"))
    }

    @Test
    fun parseMoney_parsesWithCurrencySymbol() {
        assertEquals(Money.fromCents(1050), MoneyFormatter.parse("R$ 10,50"))
        assertEquals(Money.fromCents(1050), MoneyFormatter.parse("R$10,50"))
        assertEquals(Money.fromCents(1050), MoneyFormatter.parse(" R$   10,50  "))
    }

    @Test
    fun parseMoney_parsesWholeNumbers() {
        assertEquals(Money.fromCents(1000), MoneyFormatter.parse("10"))
        assertEquals(Money.fromCents(100000), MoneyFormatter.parse("1000"))
    }

    @Test
    fun parseMoney_returnsNullForInvalidFormats() {
        assertEquals(null, MoneyFormatter.parse("abc"))
        assertEquals(null, MoneyFormatter.parse(""))
    }

    @Test
    fun formatDate_formatsToBrazilianStandard() {
        val date = LocalDate.of(2026, 4, 26)
        assertEquals("26/04/2026", DateFormatter.format(date))
    }
}
