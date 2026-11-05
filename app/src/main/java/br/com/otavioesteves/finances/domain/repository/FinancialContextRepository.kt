package br.com.otavioesteves.finances.domain.repository

import br.com.otavioesteves.finances.domain.model.FinancialContext
import br.com.otavioesteves.finances.domain.model.MonthPeriod

/** Reads the figures and categories from one consistent database snapshot. */
fun interface FinancialContextRepository {
    suspend fun getContext(period: MonthPeriod): FinancialContext
}
