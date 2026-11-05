package br.com.otavioesteves.finances.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import br.com.otavioesteves.finances.ui.components.FinanceGlassPage
import br.com.otavioesteves.finances.domain.model.MonthPeriod
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
                onHistoryClick = { period ->
                    navController.navigate(FinancesRoute.Transactions.forPeriod(period))
                },
                onTransactionClick = { transaction ->
                    navController.navigate(FinancesRoute.EditTransaction(transaction.id).route)
                },
                onImportStatementClick = {
                    navController.navigate(FinancesRoute.ImportStatement.route)
                },
                onCategoryClick = { categorySummary, period ->
                    navController.navigate(
                        FinancesRoute.CategoryDetails(categorySummary.category.id, period).route
                    )
                },
                onAiModelClick = {
                    navController.navigate(FinancesRoute.AiModel.route)
                }
            )
        }

        composable(FinancesRoute.ImportStatement.route) {
            FinanceGlassPage {
                ImportStatementScreen(
                    onBackClick = { navController.popBackStack() },
                    onAddTransactionClick = { navController.navigate(FinancesRoute.AddTransaction.route) }
                )
            }
        }

        composable(FinancesRoute.AiModel.route) {
            FinanceGlassPage {
                AiModelScreen(
                    onBackClick = { navController.popBackStack() }
                )
            }
        }

        composable(
            route = FinancesRoute.Transactions.ROUTE_PATTERN,
            arguments = listOf(
                navArgument(FinancesRoute.Transactions.ARG_YEAR) { type = NavType.IntType; defaultValue = 0 },
                navArgument(FinancesRoute.Transactions.ARG_MONTH) { type = NavType.IntType; defaultValue = 0 }
            )
        ) { entry ->
            val year = entry.arguments?.getInt(FinancesRoute.Transactions.ARG_YEAR) ?: 0
            val month = entry.arguments?.getInt(FinancesRoute.Transactions.ARG_MONTH) ?: 0
            val initialPeriod = if (year in 1..9999 && month in 1..12) MonthPeriod(year, month) else null
            TransactionsScreen(
                initialMonthPeriod = initialPeriod,
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
            FinanceGlassPage {
                AddTransactionScreen(
                    onBackClick = { navController.popBackStack() }
                )
            }
        }

        composable(
            route = FinancesRoute.CategoryDetails.ROUTE_PATTERN,
            arguments = listOf(
                navArgument(FinancesRoute.CategoryDetails.ARG_CATEGORY_ID) {
                    type = NavType.LongType
                },
                navArgument(FinancesRoute.CategoryDetails.ARG_YEAR) { type = NavType.IntType; defaultValue = 0 },
                navArgument(FinancesRoute.CategoryDetails.ARG_MONTH) { type = NavType.IntType; defaultValue = 0 }
            )
        ) { backStackEntry ->
            val categoryId = backStackEntry.arguments?.getLong(
                FinancesRoute.CategoryDetails.ARG_CATEGORY_ID
            ) ?: return@composable

            FinanceGlassPage {
                CategoryDetailsScreen(
                    categoryId = categoryId,
                    onBackClick = { navController.popBackStack() },
                    onTransactionClick = { transaction ->
                        navController.navigate(FinancesRoute.EditTransaction(transaction.id).route)
                    }
                )
            }
        }

        composable(FinancesRoute.AddTransaction.route) {
            FinanceGlassPage {
                AddTransactionScreen(
                    onBackClick = { navController.popBackStack() }
                )
            }
        }
    }
}
