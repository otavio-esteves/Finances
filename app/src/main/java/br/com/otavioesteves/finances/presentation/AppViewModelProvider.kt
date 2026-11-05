package br.com.otavioesteves.finances.presentation

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.createSavedStateHandle
import br.com.otavioesteves.finances.MainApplication
import br.com.otavioesteves.finances.presentation.addtransaction.AddTransactionViewModel
import br.com.otavioesteves.finances.presentation.aimodel.AiModelViewModel
import br.com.otavioesteves.finances.presentation.categories.CategoriesViewModel
import br.com.otavioesteves.finances.presentation.categories.CategoryDetailsViewModel
import br.com.otavioesteves.finances.domain.model.MonthPeriod
import br.com.otavioesteves.finances.presentation.charts.ChartsViewModel
import br.com.otavioesteves.finances.presentation.dashboard.DashboardViewModel
import br.com.otavioesteves.finances.presentation.importstatement.ImportStatementViewModel
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
                dateProvider = financesApplication().container.dateProvider,
                categoriesRepository = financesApplication().container.categoriesRepository,
                monthSelection = financesApplication().container.selectedMonth
            )
        }
        initializer {
            val args = createSavedStateHandle()
            val year = args.get<Int>(FinancesRoute.CategoryDetails.ARG_YEAR) ?: 0
            val month = args.get<Int>(FinancesRoute.CategoryDetails.ARG_MONTH) ?: 0
            CategoryDetailsViewModel(
                categoryId = requireNotNull(args.get<Long>(FinancesRoute.CategoryDetails.ARG_CATEGORY_ID)),
                period = if (year in 1..9999 && month in 1..12) MonthPeriod(year, month)
                    else financesApplication().container.selectedMonth.period.value,
                categoriesRepository = financesApplication().container.categoriesRepository,
                getTransactionsByMonth = financesApplication().container.getTransactionsByMonthUseCase,
                monthSelection = financesApplication().container.selectedMonth
            )
        }
        initializer {
            DashboardViewModel(
                getMonthlyBalance = financesApplication().container.getMonthlyBalanceUseCase,
                getTransactionsByMonth = financesApplication().container.getTransactionsByMonthUseCase,
                getCategorySummaries = financesApplication().container.getCategorySummariesUseCase,
                categoriesRepository = financesApplication().container.categoriesRepository,
                dateProvider = financesApplication().container.dateProvider,
                selectedMonth = financesApplication().container.selectedMonth
            )
        }
        initializer {
            ChartsViewModel(
                getCategorySummaries = financesApplication().container.getCategorySummariesUseCase,
                transactionsRepository = financesApplication().container.transactionsRepository,
                categoriesRepository = financesApplication().container.categoriesRepository,
                dateProvider = financesApplication().container.dateProvider,
                monthSelection = financesApplication().container.selectedMonth
            )
        }
        initializer {
            TransactionsViewModel(
                getTransactionsByMonth = financesApplication().container.getTransactionsByMonthUseCase,
                deleteTransaction = financesApplication().container.deleteTransactionUseCase,
                categoriesRepository = financesApplication().container.categoriesRepository,
                dateProvider = financesApplication().container.dateProvider,
                savedStateHandle = createSavedStateHandle(),
                monthSelection = financesApplication().container.selectedMonth
            )
        }
        initializer {
            SettingsViewModel(
                createBackup = financesApplication().container.createBackupUseCase,
                restoreBackup = financesApplication().container.restoreBackupUseCase
            )
        }
        initializer {
            ImportStatementViewModel(
                importStatement = financesApplication().container.importStatementUseCase,
                synthesizeStatement = financesApplication().container.synthesizeStatementUseCase,
                confirmStatementImport = financesApplication().container.confirmStatementImportUseCase,
                categoriesRepository = financesApplication().container.categoriesRepository,
                findPotentialOverlaps = br.com.otavioesteves.finances.domain.usecase.FindPotentialOverlapsUseCase(
                    financesApplication().container.transactionsRepository
                )
            )
        }
        initializer {
            AiModelViewModel(
                localAiEngine = financesApplication().container.localAiEngine,
                modelImporter = financesApplication().container.modelImporter
            )
        }
    }
}

fun CreationExtras.financesApplication(): MainApplication =
    (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MainApplication)
