package br.com.otavioesteves.finances.domain.model

import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderScheduleTest {
    private fun next(value: String) = nextInvoiceReminder(ZonedDateTime.parse(value)).toLocalDateTime().toString()
    @Test fun fifthBeforeNineAndAfterNine() {
        assertEquals("2026-11-05T09:00", next("2026-11-05T08:59:00-03:00[America/Sao_Paulo]"))
        assertEquals("2026-11-30T09:00", next("2026-11-05T09:00:00-03:00[America/Sao_Paulo]"))
    }
    @Test fun leapYearAndShortFebruary() {
        assertEquals("2028-02-29T09:00", next("2028-02-06T12:00:00Z"))
        assertEquals("2027-02-28T09:00", next("2027-02-06T12:00:00Z"))
    }
    @Test fun monthAndYearRollover() {
        assertEquals("2027-01-05T09:00", next("2026-12-31T10:00:00Z"))
        assertEquals("2026-12-05T09:00", next("2026-11-30T10:00:00Z"))
    }
    @Test fun preservesLocalNineAcrossDaylightSaving() {
        val next = nextInvoiceReminder(ZonedDateTime.parse("2026-03-06T12:00:00-05:00[America/New_York]"))
        assertEquals("2026-03-31T09:00-04:00[America/New_York]", next.toString())
    }
}
