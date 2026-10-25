package br.com.otavioesteves.finances.presentation.transactions

import androidx.lifecycle.ViewModel
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionsViewModel(
    private val getTransactionsByMonth: GetTransactionsByMonthUseCase,
    private val deleteTransaction: DeleteTransactionUseCase,
    private val categoriesRepository: CategoriesRepository,
    dateProvider: DateProvider,
    private val mappingDispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    private val _selectedMonthPeriod = MutableStateFlow(dateProvider.getCurrentMonthPeriod())
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
        _selectedMonthPeriod.value = period
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

    fun getExportData(format: ExportFormat): String {
        val currentState = uiState.value
        val transactions = currentState.transactions.map { it.transaction }
        val categoryMap = currentState.transactions
            .mapNotNull { it.category }
            .associateBy { it.id }
        
        return TransactionExporter().export(transactions, categoryMap, format)
    }
}
