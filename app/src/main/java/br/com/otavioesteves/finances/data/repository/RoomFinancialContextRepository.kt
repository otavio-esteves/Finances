package br.com.otavioesteves.finances.data.repository

import androidx.room.withTransaction
import br.com.otavioesteves.finances.data.local.AppDatabase
import br.com.otavioesteves.finances.data.local.mapper.toDomain
import br.com.otavioesteves.finances.domain.model.FinancialContext
import br.com.otavioesteves.finances.domain.model.Money
import br.com.otavioesteves.finances.domain.model.MonthPeriod
import br.com.otavioesteves.finances.domain.repository.FinancialContextRepository

class RoomFinancialContextRepository(private val database: AppDatabase) : FinancialContextRepository {
    override suspend fun getContext(period: MonthPeriod): FinancialContext = database.withTransaction {
        val start = period.toYearMonth().atDay(1).toString()
        val end = period.toYearMonth().atEndOfMonth().toString()
        FinancialContext(
            period = period,
            monthlyBalance = Money.fromCents(database.transactionDao().getBalanceSnapshot(start, end) ?: 0L),
            categorySummaries = database.categoryDao().getCategorySummarySnapshot(start, end)
                .map { it.toDomain() }.sortedBy { it.category.id },
            categories = database.categoryDao().getAllCategoriesForBackup().map { it.toDomain() }
        )
    }
}
