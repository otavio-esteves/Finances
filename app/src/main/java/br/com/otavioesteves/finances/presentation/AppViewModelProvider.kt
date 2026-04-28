package br.com.otavioesteves.finances.presentation

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import br.com.otavioesteves.finances.MainApplication
import br.com.otavioesteves.finances.presentation.addtransaction.AddTransactionViewModel
import br.com.otavioesteves.finances.presentation.categories.CategoriesViewModel
import br.com.otavioesteves.finances.presentation.dashboard.DashboardViewModel

object AppViewModelProvider {
    val Factory = viewModelFactory {
        initializer {
            AddTransactionViewModel(
                addTransactionUseCase = financesApplication().container.addTransactionUseCase,
                categoriesRepository = financesApplication().container.categoriesRepository,
                dateProvider = financesApplication().container.dateProvider
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
    }
}

fun CreationExtras.financesApplication(): MainApplication =
    (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MainApplication)
