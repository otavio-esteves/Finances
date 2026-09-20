package br.com.otavioesteves.finances.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import br.com.otavioesteves.finances.ui.screens.addtransaction.AddTransactionScreen
import br.com.otavioesteves.finances.ui.screens.aimodel.AiModelScreen
import br.com.otavioesteves.finances.ui.screens.categorydetails.CategoryDetailsScreen
import br.com.otavioesteves.finances.ui.screens.home.MainTabsScreen
import br.com.otavioesteves.finances.ui.screens.importstatement.ImportStatementScreen
import br.com.otavioesteves.finances.ui.screens.transactions.TransactionsScreen

@Composable
fun FinancesNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = FinancesRoute.Dashboard.route,
        modifier = modifier
    ) {
        composable(FinancesRoute.Dashboard.route) {
            MainTabsScreen(
                onAddTransactionClick = {
                    navController.navigate(FinancesRoute.AddTransaction.route)
                },
                onHistoryClick = {
                    navController.navigate(FinancesRoute.Transactions.route)
                },
                onImportStatementClick = {
                    navController.navigate(FinancesRoute.ImportStatement.route)
                },
                onCategoryClick = { categorySummary ->
                    navController.navigate(
                        FinancesRoute.CategoryDetails(categorySummary.category.id).route
                    )
                },
                onAiModelClick = {
                    navController.navigate(FinancesRoute.AiModel.route)
                }
            )
        }

        composable(FinancesRoute.ImportStatement.route) {
            ImportStatementScreen(
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(FinancesRoute.AiModel.route) {
            AiModelScreen(
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(FinancesRoute.Transactions.route) {
            TransactionsScreen(
                onBackClick = { navController.popBackStack() },
                onTransactionClick = { transaction ->
                    navController.navigate(FinancesRoute.EditTransaction(transaction.id).route)
                }
            )
        }

        composable(
            route = FinancesRoute.EditTransaction.ROUTE_PATTERN,
            arguments = listOf(
                navArgument(FinancesRoute.EditTransaction.ARG_TRANSACTION_ID) {
                    type = NavType.LongType
                }
            )
        ) {
            AddTransactionScreen(
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(
            route = FinancesRoute.CategoryDetails.ROUTE_PATTERN,
            arguments = listOf(
                navArgument(FinancesRoute.CategoryDetails.ARG_CATEGORY_ID) {
                    type = NavType.LongType
                }
            )
        ) { backStackEntry ->
            val categoryId = backStackEntry.arguments?.getLong(
                FinancesRoute.CategoryDetails.ARG_CATEGORY_ID
            ) ?: return@composable

            CategoryDetailsScreen(
                categoryId = categoryId,
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(FinancesRoute.AddTransaction.route) {
            AddTransactionScreen(
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}
