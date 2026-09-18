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

    @Test
    fun previousMonth_movesBackOneMonth() {
        assertEquals(MonthPeriod(year = 2026, month = 1), MonthPeriod(year = 2026, month = 2).previousMonth())
    }

    @Test
    fun previousMonth_crossesYearBoundary() {
        assertEquals(MonthPeriod(year = 2025, month = 12), MonthPeriod(year = 2026, month = 1).previousMonth())
    }

    @Test
    fun nextMonth_movesForwardOneMonth() {
        assertEquals(MonthPeriod(year = 2026, month = 2), MonthPeriod(year = 2026, month = 1).nextMonth())
    }

    @Test
    fun nextMonth_crossesYearBoundary() {
        assertEquals(MonthPeriod(year = 2026, month = 1), MonthPeriod(year = 2025, month = 12).nextMonth())
    }
}
