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

    data class CategoryDetails(val categoryId: Long) : FinancesRoute {
        override val route: String = "categories/$categoryId"

        companion object {
            const val ARG_CATEGORY_ID = "categoryId"
            const val ROUTE_PATTERN = "categories/{$ARG_CATEGORY_ID}"
        }
    }
}
