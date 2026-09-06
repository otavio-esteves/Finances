package br.com.otavioesteves.finances.utils

import br.com.otavioesteves.finances.domain.model.Category
import br.com.otavioesteves.finances.domain.model.Transaction
import br.com.otavioesteves.finances.domain.model.TransactionType
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class ExportFormat {
    CSV, JSON
}

@Serializable
private data class ExportedTransaction(
    val id: Long,
    val description: String,
    val amountCents: Long,
    val categoryId: Long,
    val categoryName: String,
    val date: String,
    val type: TransactionType,
    val notes: String?
)

class TransactionExporter {
    fun export(transactions: List<Transaction>, categories: Map<Long, Category>, format: ExportFormat): String {
        return when (format) {
            ExportFormat.CSV -> toCsv(transactions, categories)
            ExportFormat.JSON -> toJson(transactions, categories)
        }
    }

    private fun toCsv(transactions: List<Transaction>, categories: Map<Long, Category>): String {
        val header = "ID,Data,Descrição,Valor (Centavos),Tipo,Categoria,Observações"
        val rows = transactions.map { t ->
            val categoryName = categories[t.categoryId]?.name ?: "Desconhecida"
            "${t.id},${t.date},${t.description.toCsvField()},${t.amount.cents},${t.type},${categoryName.toCsvField()},${(t.notes ?: "").toCsvField()}"
        }
        return (listOf(header) + rows).joinToString("\n")
    }

    private fun toJson(transactions: List<Transaction>, categories: Map<Long, Category>): String {
        val models = transactions.map { t ->
            ExportedTransaction(
                id = t.id,
                description = t.description,
                amountCents = t.amount.cents,
                categoryId = t.categoryId,
                categoryName = categories[t.categoryId]?.name ?: "Desconhecida",
                date = t.date.toString(),
                type = t.type,
                notes = t.notes
            )
        }
        val json = Json { prettyPrint = true }
        return json.encodeToString(models)
    }

    private fun String.toCsvField(): String {
        val safeValue = if (firstOrNull() in FORMULA_PREFIXES) "'$this" else this
        return "\"${safeValue.replace("\"", "\"\"")}\""
    }

    private companion object {
        val FORMULA_PREFIXES = setOf('=', '+', '-', '@')
    }
}
