package br.com.otavioesteves.finances.domain.usecase

import br.com.otavioesteves.finances.domain.model.Category
import br.com.otavioesteves.finances.domain.model.CategoryType
import br.com.otavioesteves.finances.domain.model.Money
import br.com.otavioesteves.finances.domain.model.Transaction
import br.com.otavioesteves.finances.domain.model.TransactionType
import java.time.LocalDate

const val EXAMPLE_TRANSACTIONS_NOTE = "EXEMPLO_GRAFICOS_2026"

/** Six clearly marked examples for each month from January through October 2026. */
fun exampleTransactions2026(categories: List<Category>, today: LocalDate): List<Transaction> {
    val expenseCategories = categories.filter { it.type == CategoryType.EXPENSE }
    val incomeCategories = categories.filter { it.type == CategoryType.INCOME }
    require(expenseCategories.isNotEmpty() && incomeCategories.isNotEmpty()) {
        "É preciso ter categorias de despesa e receita para criar os exemplos."
    }

    data class Template(val description: String, val category: String, val day: Int, val baseCents: Long, val monthlyStep: Long, val type: TransactionType)
    val templates = listOf(
        Template("[EXEMPLO] Salário", "Salário", 1, 450_000, 4_000, TransactionType.INCOME),
        Template("[EXEMPLO] Aluguel", "Moradia", 3, 150_000, 2_000, TransactionType.EXPENSE),
        Template("[EXEMPLO] Mercado", "Alimentação", 8, 48_000, 1_200, TransactionType.EXPENSE),
        Template("[EXEMPLO] Transporte", "Transporte", 12, 22_000, 700, TransactionType.EXPENSE),
        Template("[EXEMPLO] Farmácia", "Saúde", 18, 9_000, 350, TransactionType.EXPENSE),
        Template("[EXEMPLO] Lazer", "Lazer", 24, 15_000, 900, TransactionType.EXPENSE)
    )
    val lastMonth = if (today.year == 2026) minOf(today.monthValue, 10) else 10
    return (1..lastMonth).flatMap { month ->
        templates.map { template ->
            val pool = if (template.type == TransactionType.INCOME) incomeCategories else expenseCategories
            val category = pool.firstOrNull { it.name == template.category } ?: pool.first()
            val day = if (today.year == 2026 && month == today.monthValue) minOf(template.day, today.dayOfMonth) else template.day
            Transaction(
                id = 0,
                description = template.description,
                amount = Money.fromCents(template.baseCents + (month - 1) * template.monthlyStep),
                categoryId = category.id,
                date = LocalDate.of(2026, month, day),
                type = template.type,
                notes = EXAMPLE_TRANSACTIONS_NOTE
            )
        }
    }
}
