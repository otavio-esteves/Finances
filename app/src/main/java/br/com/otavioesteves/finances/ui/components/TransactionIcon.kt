package br.com.otavioesteves.finances.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.ui.graphics.vector.ImageVector
import br.com.otavioesteves.finances.domain.model.TransactionType

private val keywordIcons: List<Pair<List<String>, ImageVector>> = listOf(
    listOf("mercado", "super", "supermercado") to Icons.Filled.ShoppingCart,
    listOf("café", "cafe", "padaria") to Icons.Filled.LocalCafe,
    listOf("restaurante", "lanche", "ifood", "comida", "alimenta") to Icons.Filled.Fastfood,
    listOf("uber", "táxi", "taxi", "transporte", "combustível", "combustivel", "gasolina", "99") to Icons.Filled.DirectionsCar,
    listOf("moradia", "aluguel", "condom", "casa") to Icons.Filled.Home,
    listOf("academia", "saúde", "saude", "farmácia", "farmacia", "médico", "medico") to Icons.Filled.FitnessCenter,
    listOf("hospital", "plano de saúde", "plano de saude") to Icons.Filled.LocalHospital,
    listOf("roupa", "loja", "vestu") to Icons.Filled.Checkroom,
    listOf("cinema", "streaming", "lazer", "show") to Icons.Filled.Movie,
    listOf("escola", "curso", "educa", "faculdade") to Icons.Filled.School,
    listOf("viagem", "passagem", "hotel") to Icons.Filled.Flight
)

/**
 * Picks an icon for a transaction/category row by keyword-matching the category name.
 * Falls back to a generic money icon for income and a receipt icon for unmatched expenses.
 */
fun transactionIcon(categoryName: String?, type: TransactionType): ImageVector {
    val normalized = categoryName.orEmpty().lowercase()
    keywordIcons.forEach { (keywords, icon) ->
        if (keywords.any { normalized.contains(it) }) return icon
    }
    return if (type == TransactionType.INCOME) Icons.Filled.AttachMoney else Icons.Filled.Receipt
}
