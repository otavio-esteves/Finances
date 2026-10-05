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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import br.com.otavioesteves.finances.domain.model.TransactionType

data class TransactionIconOption(
    val key: String,
    val label: String,
    val image: ImageVector,
    val color: Color
)

/** Stable keys are stored with the transaction; changing the order does not change saved icons. */
val transactionIconOptions = listOf(
    TransactionIconOption("market", "Mercado", Icons.Filled.ShoppingCart, Color(0xFFFF4D32)),
    TransactionIconOption("food", "Comida", Icons.Filled.Fastfood, Color(0xFFFF8B21)),
    TransactionIconOption("transport", "Transporte", Icons.Filled.DirectionsCar, Color(0xFFD93039)),
    TransactionIconOption("home", "Casa", Icons.Filled.Home, Color(0xFFFF6840)),
    TransactionIconOption("health", "Saúde", Icons.Filled.LocalHospital, Color(0xFFC62242)),
    TransactionIconOption("shopping", "Compras", Icons.Filled.Checkroom, Color(0xFFE54C52)),
    TransactionIconOption("leisure", "Lazer", Icons.Filled.Movie, Color(0xFFE67800)),
    TransactionIconOption("education", "Estudos", Icons.Filled.School, Color(0xFFAD3042)),
    TransactionIconOption("travel", "Viagem", Icons.Filled.Flight, Color(0xFFFFA62B)),
    TransactionIconOption("income", "Receita", Icons.Filled.AttachMoney, Color(0xFFBE5A00)),
    TransactionIconOption("transfer", "Transferência", Icons.Filled.AccountBalanceWallet, Color(0xFF696A73)),
    TransactionIconOption("other", "Outros", Icons.Filled.Receipt, Color(0xFF37373D))
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
