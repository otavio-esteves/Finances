package br.com.otavioesteves.finances.domain

import br.com.otavioesteves.finances.domain.model.MonthPeriod
import java.time.LocalDate
import java.time.LocalDateTime

interface DateProvider {
    fun getCurrentMonthPeriod(): MonthPeriod
    fun getCurrentDate(): LocalDate
    fun getCurrentDateTime(): LocalDateTime
}
