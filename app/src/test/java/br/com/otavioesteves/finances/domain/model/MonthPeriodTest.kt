package br.com.otavioesteves.finances.domain.model

import br.com.otavioesteves.finances.utils.formatMonthPeriod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MonthPeriodTest {
    @Test
    fun formatMonthPeriod_formatsMonthNameInPortuguese() {
        assertEquals("Janeiro", formatMonthPeriod(MonthPeriod(year = 2026, month = 1)))
    }

    @Test
    fun constructor_rejectsMonthBelowRange() {
        assertThrows(IllegalArgumentException::class.java) {
            MonthPeriod(year = 2026, month = 0)
        }
    }

    @Test
    fun constructor_rejectsMonthAboveRange() {
        assertThrows(IllegalArgumentException::class.java) {
            MonthPeriod(year = 2026, month = 13)
        }
    }
}
