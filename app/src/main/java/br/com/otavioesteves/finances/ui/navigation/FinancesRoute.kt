package br.com.otavioesteves.finances.ui.navigation

sealed interface FinancesRoute {
    val route: String

    data object Dashboard : FinancesRoute {
        override val route: String = "dashboard"
    }

    data object Categories : FinancesRoute {
        override val route: String = "categories"
    }

    data object AddTransaction : FinancesRoute {
        override val route: String = "add-transaction"
    }

    data object Transactions : FinancesRoute {
        override val route: String = "transactions"
    }

    data object Settings : FinancesRoute {
        override val route: String = "settings"
    }

    data class EditTransaction(val transactionId: Long) : FinancesRoute {
        override val route: String = "transactions/edit/$transactionId"

        companion object {
            const val ARG_TRANSACTION_ID = "transactionId"
            const val ROUTE_PATTERN = "transactions/edit/{$ARG_TRANSACTION_ID}"
        }
    }

    data class CategoryDetails(val categoryId: Long) : FinancesRoute {
        override val route: String = "categories/$categoryId"

        companion object {
            const val ARG_CATEGORY_ID = "categoryId"
            const val ROUTE_PATTERN = "categories/{$ARG_CATEGORY_ID}"
        }
    }
}
