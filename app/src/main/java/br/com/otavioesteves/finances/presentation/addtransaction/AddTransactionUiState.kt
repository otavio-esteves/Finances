package br.com.otavioesteves.finances.presentation.addtransaction

import br.com.otavioesteves.finances.domain.model.Category
import br.com.otavioesteves.finances.domain.model.TransactionType
import java.time.LocalDate

data class AddTransactionUiState(
    val description: String = "",
    val descriptionError: String? = null,
    val amount: String = "",
    val amountError: String? = null,
    val type: TransactionType = TransactionType.EXPENSE,
    val selectedCategory: Category? = null,
    val categoryError: String? = null,
    val date: LocalDate = LocalDate.now(),
    val dateError: String? = null,
    val notes: String = "",
    val categories: List<Category> = emptyList(),
    val isSaveSuccessful: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val showDeleteConfirmation: Boolean = false
)
