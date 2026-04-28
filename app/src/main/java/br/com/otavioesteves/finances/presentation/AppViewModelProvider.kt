package br.com.otavioesteves.finances.presentation

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.createSavedStateHandle
import br.com.otavioesteves.finances.MainApplication
import br.com.otavioesteves.finances.presentation.addtransaction.AddTransactionViewModel
import br.com.otavioesteves.finances.presentation.categories.CategoriesViewModel
import br.com.otavioesteves.finances.presentation.dashboard.DashboardViewModel
import br.com.otavioesteves.finances.presentation.transactions.TransactionsViewModel
import br.com.otavioesteves.finances.presentation.settings.SettingsViewModel
import br.com.otavioesteves.finances.ui.navigation.FinancesRoute

object AppViewModelProvider {
    val Factory = viewModelFactory {
        initializer {
            val savedStateHandle = createSavedStateHandle()
            val transactionId: Long? = savedStateHandle[FinancesRoute.EditTransaction.ARG_TRANSACTION_ID]
            
            AddTransactionViewModel(
                addTransactionUseCase = financesApplication().container.addTransactionUseCase,
                updateTransactionUseCase = financesApplication().container.updateTransactionUseCase,
                getTransactionUseCase = financesApplication().container.getTransactionUseCase,
                deleteTransactionUseCase = financesApplication().container.deleteTransactionUseCase,
                categoriesRepository = financesApplication().container.categoriesRepository,
                dateProvider = financesApplication().container.dateProvider,
                transactionId = transactionId
            )
        }
        initializer {
            CategoriesViewModel(
                getCategorySummaries = financesApplication().container.getCategorySummariesUseCase,
                dateProvider = financesApplication().container.dateProvider
            )
        }
        initializer {
            DashboardViewModel(
                getMonthlyBalance = financesApplication().container.getMonthlyBalanceUseCase,
                getTransactionsByMonth = financesApplication().container.getTransactionsByMonthUseCase,
                dateProvider = financesApplication().container.dateProvider
            )
        }
        initializer {
            TransactionsViewModel(
                getTransactionsByMonth = financesApplication().container.getTransactionsByMonthUseCase,
                deleteTransaction = financesApplication().container.deleteTransactionUseCase,
                categoriesRepository = financesApplication().container.categoriesRepository,
                dateProvider = financesApplication().container.dateProvider
            )
        }
        initializer {
            SettingsViewModel(
                createBackup = financesApplication().container.createBackupUseCase,
                restoreBackup = financesApplication().container.restoreBackupUseCase
            )
        }
    }
}

fun CreationExtras.financesApplication(): MainApplication =
    (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MainApplication)
