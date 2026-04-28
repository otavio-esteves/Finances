package br.com.otavioesteves.finances.di

import android.content.Context
import br.com.otavioesteves.finances.data.DefaultDateProvider
import br.com.otavioesteves.finances.data.local.AppDatabase
import br.com.otavioesteves.finances.data.repository.RoomCategoriesRepository
import br.com.otavioesteves.finances.data.repository.RoomTransactionsRepository
import br.com.otavioesteves.finances.domain.DateProvider
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
    val getTransactionUseCase: br.com.otavioesteves.finances.domain.usecase.GetTransactionUseCase
    val deleteTransactionUseCase: br.com.otavioesteves.finances.domain.usecase.DeleteTransactionUseCase
    val updateTransactionUseCase: br.com.otavioesteves.finances.domain.usecase.UpdateTransactionUseCase
    val createBackupUseCase: br.com.otavioesteves.finances.domain.usecase.CreateBackupUseCase
    val restoreBackupUseCase: br.com.otavioesteves.finances.domain.usecase.RestoreBackupUseCase
    val dateProvider: DateProvider
}

class DefaultAppContainer(
    private val context: Context,
    private val applicationScope: CoroutineScope
) : AppContainer {

    override val dateProvider: DateProvider by lazy {
        DefaultDateProvider()
    }
    
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

    override val getTransactionUseCase: br.com.otavioesteves.finances.domain.usecase.GetTransactionUseCase by lazy {
        br.com.otavioesteves.finances.domain.usecase.GetTransactionUseCase(transactionsRepository)
    }

    override val deleteTransactionUseCase: br.com.otavioesteves.finances.domain.usecase.DeleteTransactionUseCase by lazy {
        br.com.otavioesteves.finances.domain.usecase.DeleteTransactionUseCase(transactionsRepository)
    }

    override val updateTransactionUseCase: br.com.otavioesteves.finances.domain.usecase.UpdateTransactionUseCase by lazy {
        br.com.otavioesteves.finances.domain.usecase.UpdateTransactionUseCase(transactionsRepository)
    }

    private val backupRepository: br.com.otavioesteves.finances.domain.repository.BackupRepository by lazy {
        br.com.otavioesteves.finances.data.repository.RoomBackupRepository(database)
    }

    override val createBackupUseCase: br.com.otavioesteves.finances.domain.usecase.CreateBackupUseCase by lazy {
        br.com.otavioesteves.finances.domain.usecase.CreateBackupUseCase(backupRepository)
    }

    override val restoreBackupUseCase: br.com.otavioesteves.finances.domain.usecase.RestoreBackupUseCase by lazy {
        br.com.otavioesteves.finances.domain.usecase.RestoreBackupUseCase(backupRepository)
    }
}
