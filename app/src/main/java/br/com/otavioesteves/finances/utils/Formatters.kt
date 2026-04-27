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
    fun format(value: Money): String {
        val ptBrLocale = Locale.forLanguageTag("pt-BR")
        val decimalValue = BigDecimal.valueOf(value.cents, 2)
        val formatter = NumberFormat.getCurrencyInstance(ptBrLocale)
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
        try {
            var sanitized = value.trim()
            if (sanitized.isEmpty()) return null

            // Retain only digits, comma, dot, and minus sign
            sanitized = sanitized.replace(Regex("[^0-9.,-]"), "")
            
            if (sanitized.isEmpty()) return null

            // Handle cases like "1.250,75" or "1,250.75" or "1250.75" or "1250,75"
            val lastCommaIndex = sanitized.lastIndexOf(',')
            val lastDotIndex = sanitized.lastIndexOf('.')
            
            val standardized = if (lastCommaIndex > lastDotIndex) {
                // Brazilian format: "1.250,75" or "1250,75"
                sanitized.replace(".", "").replace(',', '.')
            } else if (lastDotIndex > lastCommaIndex) {
                // US format: "1,250.75" or "1250.75"
                sanitized.replace(",", "")
            } else {
                // No separators, e.g., "1250"
                sanitized
            }

            val parsed = BigDecimal(standardized)
            val cents = parsed.multiply(BigDecimal(100)).toLong()
            return Money.fromCents(cents)
        } catch (e: Exception) {
            return null
        }
    }
}

object DateFormatter {
    fun format(date: LocalDate): String {
        val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
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
