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
import kotlinx.coroutines.launch

class TransactionsViewModel(
    private val getTransactionsByMonth: GetTransactionsByMonthUseCase,
    private val deleteTransaction: DeleteTransactionUseCase,
    private val categoriesRepository: CategoriesRepository,
    dateProvider: DateProvider
) : ViewModel() {

    private val _selectedMonthPeriod = MutableStateFlow(dateProvider.getCurrentMonthPeriod())
    val selectedMonthPeriod: StateFlow<MonthPeriod> = _selectedMonthPeriod

    private val _transactionToDelete = MutableStateFlow<Transaction?>(null)
    private val _error = MutableStateFlow<String?>(null)

    val uiState: StateFlow<TransactionsUiState> = combine(
        _selectedMonthPeriod.flatMapLatest { period ->
            combine(
                getTransactionsByMonth(period),
                categoriesRepository.getCategories()
            ) { transactions, categories ->
                val categoryMap = categories.associateBy { it.id }
                transactions.map { transaction ->
                    TransactionItem(
                        transaction = transaction,
                        category = categoryMap[transaction.categoryId]
                    )
                }
            }
        },
        _transactionToDelete,
        _error
    ) { items, toDelete, errorMsg ->
        TransactionsUiState(
            monthPeriod = _selectedMonthPeriod.value,
            transactions = items,
            isLoading = false,
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
