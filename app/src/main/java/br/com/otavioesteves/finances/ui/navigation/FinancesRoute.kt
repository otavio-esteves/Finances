package br.com.otavioesteves.finances.ui.navigation

import br.com.otavioesteves.finances.domain.model.MonthPeriod

sealed interface FinancesRoute {
    val route: String

    data object Dashboard : FinancesRoute {
        override val route: String = "dashboard"
    }

    data object AddTransaction : FinancesRoute {
        override val route: String = "add-transaction"
    }

    data object Transactions : FinancesRoute {
        override val route: String = "transactions"
        const val ARG_YEAR = "year"
        const val ARG_MONTH = "month"
        const val ROUTE_PATTERN = "transactions?year={year}&month={month}"

        fun forPeriod(period: MonthPeriod): String = "$route?year=${period.year}&month=${period.month}"
    }

    data object AiModel : FinancesRoute {
        override val route: String = "settings/ai-model"
    }

    data object ImportStatement : FinancesRoute {
        override val route: String = "import-statement"
    }

    data class EditTransaction(val transactionId: Long) : FinancesRoute {
        override val route: String = "transactions/edit/$transactionId"

        companion object {
            const val ARG_TRANSACTION_ID = "transactionId"
            const val ROUTE_PATTERN = "transactions/edit/{$ARG_TRANSACTION_ID}"
        }
    }

    data class CategoryDetails(val categoryId: Long, val period: MonthPeriod? = null) : FinancesRoute {
        override val route: String = "categories/$categoryId" +
            (period?.let { "?year=${it.year}&month=${it.month}" } ?: "")

        companion object {
            const val ARG_CATEGORY_ID = "categoryId"
            const val ARG_YEAR = "year"
            const val ARG_MONTH = "month"
            const val ROUTE_PATTERN = "categories/{$ARG_CATEGORY_ID}?year={year}&month={month}"
        }
    }
}
