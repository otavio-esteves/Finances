package br.com.otavioesteves.finances.utils

import br.com.otavioesteves.finances.domain.model.MonthPeriod
import br.com.otavioesteves.finances.domain.model.Money
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.LocalDate
import java.time.Month
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

object MoneyFormatter {
    private val ptBrLocale = Locale.forLanguageTag("pt-BR")
    private val currencyFormat = ThreadLocal.withInitial { NumberFormat.getCurrencyInstance(ptBrLocale) }

    fun format(value: Money): String {
        val decimalValue = BigDecimal.valueOf(value.cents, 2)
        val formatter = requireNotNull(currencyFormat.get())
        val formatted = formatter.format(decimalValue)
        
        // Fix negative formatting if the JVM places minus sign after currency symbol
        // and replace standard space with non-breaking space if necessary to match tests
        return if (value.cents < 0 && !formatted.startsWith("-")) {
            val positiveFormat = formatter.format(decimalValue.abs())
            "-$positiveFormat"
        } else {
            formatted
        }
    }

    fun parse(value: String): Money? {
        val match = AMOUNT_INPUT.matchEntire(value.trim().replace('\u00a0', ' ')) ?: return null
        val number = match.groupValues[2]
        val standardized = when {
            BR_GROUPED.matches(number) -> number.replace(".", "").replace(',', '.')
            US_GROUPED.matches(number) -> number.replace(",", "")
            PLAIN_DECIMAL.matches(number) -> number.replace(',', '.')
            WHOLE_NUMBER.matches(number) -> number
            else -> return null
        }
        return runCatching {
            val cents = BigDecimal(standardized).movePointRight(2).longValueExact()
            Money.fromCents(if (match.groupValues[1] == "-") Math.negateExact(cents) else cents)
        }.getOrNull()
    }

    private val AMOUNT_INPUT = Regex("^(-?)\\s*(?:R\\$\\s*)?([0-9][0-9.,]*)$")
    private val BR_GROUPED = Regex("^[0-9]{1,3}(?:\\.[0-9]{3})+(?:,[0-9]{1,2})?$")
    private val US_GROUPED = Regex("^[0-9]{1,3}(?:,[0-9]{3})+(?:\\.[0-9]{1,2})?$")
    private val PLAIN_DECIMAL = Regex("^[0-9]+[.,][0-9]{1,2}$")
    private val WHOLE_NUMBER = Regex("^[0-9]+$")
}

object DateFormatter {
    private val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    fun format(date: LocalDate): String {
        return date.format(formatter)
    }
}

fun formatMonthPeriod(period: MonthPeriod): String {
    val ptBrLocale = Locale.forLanguageTag("pt-BR")
    val monthName = Month.of(period.month).getDisplayName(TextStyle.FULL, ptBrLocale)
    return monthName.replaceFirstChar { char ->
        if (char.isLowerCase()) {
            char.titlecase(ptBrLocale)
        } else {
            char.toString()
        }
    }
}
