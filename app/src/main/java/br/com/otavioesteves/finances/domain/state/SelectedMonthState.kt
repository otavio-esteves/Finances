package br.com.otavioesteves.finances.domain.state

import br.com.otavioesteves.finances.domain.model.MonthPeriod
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One period for every monthly view, independent of page and navigation lifetimes. */
class SelectedMonthState(
    initialPeriod: MonthPeriod,
    private val persist: (MonthPeriod) -> Unit = {}
) {
    private val selected = MutableStateFlow(initialPeriod)
    val period = selected.asStateFlow()

    fun select(period: MonthPeriod) {
        if (selected.value == period) return
        persist(period)
        selected.value = period
    }
}
