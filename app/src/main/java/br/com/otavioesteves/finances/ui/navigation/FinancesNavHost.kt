package br.com.otavioesteves.finances.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import br.com.otavioesteves.finances.ui.screens.addtransaction.AddTransactionScreen
import br.com.otavioesteves.finances.ui.screens.categories.CategoriesScreen
import br.com.otavioesteves.finances.ui.screens.categorydetails.CategoryDetailsScreen
import br.com.otavioesteves.finances.ui.screens.dashboard.DashboardScreen

@Composable
fun FinancesNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = FinancesRoute.Dashboard.route,
        modifier = modifier
    ) {
        composable(FinancesRoute.Dashboard.route) {
            DashboardScreen(
                onCategoriesClick = {
                    navController.navigate(FinancesRoute.Categories.route)
                },
                onAddTransactionClick = {
                    navController.navigate(FinancesRoute.AddTransaction.route)
                }
            )
        }

        composable(FinancesRoute.Categories.route) {
            CategoriesScreen(
                onCategoryClick = { category ->
                    navController.navigate(
                        FinancesRoute.CategoryDetails(category.category.id).route
                    )
                }
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
