package br.com.otavioesteves.finances.domain

import br.com.otavioesteves.finances.domain.model.MonthPeriod
import java.time.LocalDate

interface DateProvider {
    fun getCurrentMonthPeriod(): MonthPeriod
    fun getCurrentDate(): LocalDate
}
