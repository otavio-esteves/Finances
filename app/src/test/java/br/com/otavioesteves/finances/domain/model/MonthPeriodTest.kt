package br.com.otavioesteves.finances.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.YearMonth

class MonthPeriodTest {
    @Test
    fun toYearMonth_preservesYearAndMonth() {
        assertEquals(YearMonth.of(2026, 1), MonthPeriod(year = 2026, month = 1).toYearMonth())
    }

    @Test
    fun fromYearMonth_createsEquivalentPeriod() {
        assertEquals(MonthPeriod(year = 2026, month = 1), MonthPeriod.from(YearMonth.of(2026, 1)))
    }

    @Test
    fun constructor_rejectsYearBelowRange() {
        assertThrows(IllegalArgumentException::class.java) {
            MonthPeriod(year = 0, month = 1)
        }
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
