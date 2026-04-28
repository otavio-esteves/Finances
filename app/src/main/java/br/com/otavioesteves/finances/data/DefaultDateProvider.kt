package br.com.otavioesteves.finances.data

import br.com.otavioesteves.finances.domain.DateProvider
import br.com.otavioesteves.finances.domain.model.MonthPeriod
import java.time.LocalDate

class DefaultDateProvider : DateProvider {
    override fun getCurrentMonthPeriod(): MonthPeriod = MonthPeriod.now()
    override fun getCurrentDate(): LocalDate = LocalDate.now()
}
