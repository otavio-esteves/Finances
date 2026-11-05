package br.com.otavioesteves.finances.presentation.transactions

import androidx.lifecycle.ViewModel
import br.com.otavioesteves.finances.domain.state.SelectedMonthState
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import br.com.otavioesteves.finances.domain.DateProvider
import br.com.otavioesteves.finances.domain.model.MonthPeriod
import br.com.otavioesteves.finances.domain.model.Transaction
import br.com.otavioesteves.finances.domain.repository.CategoriesRepository
import br.com.otavioesteves.finances.domain.usecase.DeleteTransactionUseCase
import br.com.otavioesteves.finances.domain.usecase.GetTransactionsByMonthUseCase
import br.com.otavioesteves.finances.utils.ExportFormat
import br.com.otavioesteves.finances.utils.TransactionExporter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionsViewModel(
    private val getTransactionsByMonth: GetTransactionsByMonthUseCase,
    private val deleteTransaction: DeleteTransactionUseCase,
    private val categoriesRepository: CategoriesRepository,
    dateProvider: DateProvider,
    private val mappingDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
    private val monthSelection: SelectedMonthState = SelectedMonthState(
        savedStateHandle.get<String>("selected_month")?.let {
            runCatching { MonthPeriod.from(java.time.YearMonth.parse(it)) }.getOrNull()
        } ?: dateProvider.getCurrentMonthPeriod()
    )
) : ViewModel() {

    private val _selectedMonthPeriod = monthSelection.period
    val selectedMonthPeriod: StateFlow<MonthPeriod> = _selectedMonthPeriod

    private val _transactionToDelete = MutableStateFlow<Transaction?>(null)
    private val _error = MutableStateFlow<String?>(null)
    private var mappedPeriod: MonthPeriod? = null
    private var itemMapper = TransactionItemMapper()
    private var lastMonthlyState: TransactionsUiState? = null

    val uiState: StateFlow<TransactionsUiState> = combine(
        _selectedMonthPeriod.flatMapLatest { period ->
            if (mappedPeriod != period) {
                itemMapper = TransactionItemMapper()
                mappedPeriod = period
            }
            val mapper = itemMapper
            combine(
                getTransactionsByMonth(period),
                categoriesRepository.getCategories()
            ) { transactions, categories ->
                TransactionsUiState(monthPeriod = period, transactions = mapper.map(transactions, categories))
                    .also { lastMonthlyState = it }
            }.onStart {
                // Re-entering the same month keeps cached rows visible while Room refreshes.
                // Emptying the list here would also reset its restored scroll position.
                if (lastMonthlyState?.monthPeriod != period || lastMonthlyState?.isLoading != false) {
                    val loading = TransactionsUiState(monthPeriod = period, isLoading = true)
                    lastMonthlyState = loading
                    emit(loading)
                }
            }
        }.flowOn(mappingDispatcher),
        _transactionToDelete,
        _error
    ) { monthlyState, toDelete, errorMsg ->
        monthlyState.copy(
            transactionToDelete = toDelete,
            error = errorMsg
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TransactionsUiState(
            monthPeriod = _selectedMonthPeriod.value,
            isLoading = true
        )
    )

    fun onMonthSelected(period: MonthPeriod) {
        savedStateHandle["selected_month"] = period.toYearMonth().toString()
        monthSelection.select(period)
    }

    fun initializeMonthPeriod(period: MonthPeriod?) {
        if (savedStateHandle.get<Boolean>("initial_month_applied") == true) return
        if (period != null) onMonthSelected(period)
        savedStateHandle["initial_month_applied"] = true
    }

    fun onDeleteRequest(transaction: Transaction) {
        _transactionToDelete.value = transaction
    }

    fun onDeleteCancel() {
        _transactionToDelete.value = null
    }

    fun onDeleteConfirm() {
        val transaction = _transactionToDelete.value ?: return
        _transactionToDelete.value = null
        
        viewModelScope.launch {
            try {
                deleteTransaction(transaction.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = "Erro ao excluir transação"
            }
        }
    }

    fun onErrorDismiss() {
        _error.value = null
    }

    suspend fun getExportData(format: ExportFormat, period: MonthPeriod): String = withContext(mappingDispatcher) {
        // Export a fresh query for the requested month, independent of UI subscriptions and loading state.
        val transactions = getTransactionsByMonth(period).first()
        val categoryMap = categoriesRepository.getCategories().first().associateBy { it.id }
        TransactionExporter().export(transactions, categoryMap, format)
    }
}
