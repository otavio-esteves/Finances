package br.com.otavioesteves.finances.presentation.categories

import br.com.otavioesteves.finances.domain.model.CategorySummary
import br.com.otavioesteves.finances.domain.model.MonthPeriod

sealed interface CategoriesEvent {
    data class OnMonthChanged(val monthPeriod: MonthPeriod) : CategoriesEvent
    data class OnCategoryClicked(val category: CategorySummary) : CategoriesEvent
    data object OnRetryClicked : CategoriesEvent
}
