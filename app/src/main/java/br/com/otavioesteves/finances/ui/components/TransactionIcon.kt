package br.com.otavioesteves.finances.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.ui.graphics.vector.ImageVector
import br.com.otavioesteves.finances.domain.model.TransactionType

data class TransactionIconOption(
    val key: String,
    val label: String,
    val image: ImageVector
)

/** Stable keys are stored with the transaction; changing the order does not change saved icons. */
val transactionIconOptions = listOf(
    TransactionIconOption("market", "Mercado", Icons.Filled.ShoppingCart),
    TransactionIconOption("food", "Comida", Icons.Filled.Fastfood),
    TransactionIconOption("transport", "Transporte", Icons.Filled.DirectionsCar),
    TransactionIconOption("home", "Casa", Icons.Filled.Home),
    TransactionIconOption("health", "Saúde", Icons.Filled.LocalHospital),
    TransactionIconOption("shopping", "Compras", Icons.Filled.Checkroom),
    TransactionIconOption("leisure", "Lazer", Icons.Filled.Movie),
    TransactionIconOption("education", "Estudos", Icons.Filled.School),
    TransactionIconOption("travel", "Viagem", Icons.Filled.Flight),
    TransactionIconOption("income", "Receita", Icons.Filled.AttachMoney),
    TransactionIconOption("transfer", "Transferência", Icons.Filled.AccountBalanceWallet),
    TransactionIconOption("other", "Outros", Icons.Filled.Receipt)
)

fun transactionIconOption(key: String?): TransactionIconOption? =
    transactionIconOptions.firstOrNull { it.key == key }

/** Uses the saved choice first, then recognizable words from the transaction and category. */
fun transactionIcon(
    categoryName: String?,
    type: TransactionType,
    description: String = "",
    iconKey: String? = null
): ImageVector {
    transactionIconOption(iconKey)?.let { return it.image }
    val text = "${categoryName.orEmpty()} $description".lowercase()
    val key = when {
        listOf("mercado", "supermercado").any(text::contains) -> "market"
        listOf("restaurante", "lanche", "ifood", "comida", "aliment", "café", "cafe", "padaria").any(text::contains) -> "food"
        listOf("uber", "táxi", "taxi", "transporte", "combust", "gasolina").any(text::contains) -> "transport"
        listOf("moradia", "aluguel", "condom", "casa").any(text::contains) -> "home"
        listOf("saúde", "saude", "farmácia", "farmacia", "médico", "medico", "hospital").any(text::contains) -> "health"
        listOf("roupa", "loja", "vestu").any(text::contains) -> "shopping"
        listOf("cinema", "streaming", "lazer", "show").any(text::contains) -> "leisure"
        listOf("escola", "curso", "educa", "faculdade").any(text::contains) -> "education"
        listOf("viagem", "passagem", "hotel").any(text::contains) -> "travel"
        type == TransactionType.TRANSFER -> "transfer"
        type == TransactionType.INCOME -> "income"
        else -> "other"
    }
    return transactionIconOption(key)!!.image
}
