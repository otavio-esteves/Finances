package br.com.otavioesteves.finances.presentation.transactions

import br.com.otavioesteves.finances.domain.model.Category
import br.com.otavioesteves.finances.domain.model.MonthPeriod
import br.com.otavioesteves.finances.domain.model.Transaction

data class TransactionItem(
    val transaction: Transaction,
    val category: Category?
)

data class TransactionsUiState(
    val monthPeriod: MonthPeriod,
    val transactions: List<TransactionItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val transactionToDelete: Transaction? = null
)
