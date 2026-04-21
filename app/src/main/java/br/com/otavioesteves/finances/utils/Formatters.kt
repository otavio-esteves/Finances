package br.com.otavioesteves.finances.utils

import br.com.otavioesteves.finances.domain.model.MonthPeriod
import br.com.otavioesteves.finances.domain.model.Money
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

fun formatCurrency(value: Money): String {
    val ptBrLocale = Locale.forLanguageTag("pt-BR")
    val decimalValue = BigDecimal.valueOf(value.cents, 2)
    return NumberFormat.getCurrencyInstance(ptBrLocale).format(decimalValue)
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
