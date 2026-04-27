package br.com.otavioesteves.finances.di

import android.content.Context
import br.com.otavioesteves.finances.data.local.AppDatabase
import br.com.otavioesteves.finances.data.repository.RoomCategoriesRepository
import br.com.otavioesteves.finances.data.repository.RoomTransactionsRepository
import br.com.otavioesteves.finances.domain.repository.CategoriesRepository
import br.com.otavioesteves.finances.domain.repository.TransactionsRepository
import br.com.otavioesteves.finances.domain.usecase.AddTransactionUseCase
import br.com.otavioesteves.finances.domain.usecase.GetCategorySummariesUseCase
import br.com.otavioesteves.finances.domain.usecase.GetMonthlyBalanceUseCase
import br.com.otavioesteves.finances.domain.usecase.GetTransactionsByMonthUseCase
import kotlinx.coroutines.CoroutineScope

interface AppContainer {
    val categoriesRepository: CategoriesRepository
    val transactionsRepository: TransactionsRepository
    val addTransactionUseCase: AddTransactionUseCase
    val getCategorySummariesUseCase: GetCategorySummariesUseCase
    val getMonthlyBalanceUseCase: GetMonthlyBalanceUseCase
    val getTransactionsByMonthUseCase: GetTransactionsByMonthUseCase
}

class DefaultAppContainer(
    private val context: Context,
    private val applicationScope: CoroutineScope
) : AppContainer {
    
    private val database: AppDatabase by lazy {
        AppDatabase.getDatabase(context, applicationScope)
    }

    override val categoriesRepository: CategoriesRepository by lazy {
        RoomCategoriesRepository(database.categoryDao(), database.transactionDao())
    }

    override val transactionsRepository: TransactionsRepository by lazy {
        RoomTransactionsRepository(database.transactionDao())
    }

    override val addTransactionUseCase: AddTransactionUseCase by lazy {
        AddTransactionUseCase(transactionsRepository)
    }

    override val getCategorySummariesUseCase: GetCategorySummariesUseCase by lazy {
        GetCategorySummariesUseCase(categoriesRepository)
    }

    override val getMonthlyBalanceUseCase: GetMonthlyBalanceUseCase by lazy {
        GetMonthlyBalanceUseCase(transactionsRepository)
    }

    override val getTransactionsByMonthUseCase: GetTransactionsByMonthUseCase by lazy {
        GetTransactionsByMonthUseCase(transactionsRepository)
    }
}
