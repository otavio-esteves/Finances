package br.com.otavioesteves.finances.utils

import br.com.otavioesteves.finances.domain.model.Category
import br.com.otavioesteves.finances.domain.model.Transaction
import br.com.otavioesteves.finances.domain.model.TransactionExportModel
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class ExportFormat {
    CSV, JSON
}

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
            "${t.id},${t.date},\"${t.description.escapeCsv()}\",${t.amount.cents},${t.type},\"$categoryName\",\"${(t.notes ?: "").escapeCsv()}\""
        }
        return (listOf(header) + rows).joinToString("\n")
    }

    private fun toJson(transactions: List<Transaction>, categories: Map<Long, Category>): String {
        val models = transactions.map { t ->
            TransactionExportModel(
                id = t.id,
                description = t.description,
                amountCents = t.amount.cents,
                categoryId = t.categoryId,
                date = t.date.toString(),
                type = t.type,
                notes = t.notes
            )
        }
        val json = Json { prettyPrint = true }
        return json.encodeToString(models)
    }

    private fun String.escapeCsv(): String {
        return this.replace("\"", "\"\"")
    }
}
