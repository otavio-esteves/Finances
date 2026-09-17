package br.com.otavioesteves.finances.data

import br.com.otavioesteves.finances.domain.DateProvider
import br.com.otavioesteves.finances.domain.model.MonthPeriod
import java.time.LocalDate
import java.time.LocalDateTime

class DefaultDateProvider : DateProvider {
    override fun getCurrentMonthPeriod(): MonthPeriod = MonthPeriod.now()
    override fun getCurrentDate(): LocalDate = LocalDate.now()
    override fun getCurrentDateTime(): LocalDateTime = LocalDateTime.now()
}
