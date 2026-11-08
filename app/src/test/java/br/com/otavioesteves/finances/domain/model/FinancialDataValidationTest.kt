package br.com.otavioesteves.finances.domain.model

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class FinancialDataValidationTest {
    @Test fun magnitudeTotalAcceptsExactLimitAndZero() {
        assertEquals(Money(Long.MAX_VALUE), checkedMagnitudeTotal(sequenceOf(Long.MAX_VALUE - 1, 1, 0)))
        assertEquals(Money.Zero, checkedMagnitudeTotal(emptySequence()))
    }

    @Test fun magnitudeTotalRejectsOverflowAndNegativeValuesWithValidationErrors() {
        for (values in listOf(sequenceOf(Long.MAX_VALUE, 1L), sequenceOf(-1L))) {
            assertThrows(IllegalArgumentException::class.java) { checkedMagnitudeTotal(values) }
        }
    }

    @Test fun datesMustBelongToTheSelectableCalendar() {
        for (year in listOf(0, -1, 10000)) {
            val date = LocalDate.of(year, 1, 1)
            assertThrows(IllegalArgumentException::class.java) { RawStatementEntry("Compra", Money(1), date, TransactionType.EXPENSE) }
            assertThrows(IllegalArgumentException::class.java) { Transaction(0, "Compra", Money(1), 1, date, TransactionType.EXPENSE) }
        }
        for (date in listOf(LocalDate.of(1, 1, 1), LocalDate.of(9999, 12, 31))) {
            assertEquals(date, requireSupportedTransactionDate(date))
        }
    }
}
