package br.com.otavioesteves.finances.presentation.transactions

import br.com.otavioesteves.finances.domain.model.Category
import br.com.otavioesteves.finances.domain.model.CategoryType
import br.com.otavioesteves.finances.domain.model.Money
import br.com.otavioesteves.finances.domain.model.Transaction
import br.com.otavioesteves.finances.domain.model.TransactionType
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class TransactionItemMapperTest {
    private val food = Category(1, "Alimentação", CategoryType.EXPENSE)
    private fun transaction(id: Long) = Transaction(id, "Compra $id", Money(1234), 1,
        LocalDate.of(2026, 2, 1), TransactionType.EXPENSE)

    @Test
    fun insertionDeletionAndReorderingReuseUnchangedFormattedRows() {
        val mapper = TransactionItemMapper()
        val first = transaction(1)
        val second = transaction(2)
        val initial = mapper.map(listOf(first, second), listOf(food))
        val inserted = mapper.map(listOf(transaction(3), second, first), listOf(food))
        assertSame(initial[0], inserted[2])
        assertSame(initial[1], inserted[1])
        val deleted = mapper.map(listOf(second), listOf(food))
        assertSame(initial[1], deleted.single())
        assertEquals("R$ 12,34", deleted.single().formattedAmount)
    }

    @Test
    fun editedAmountDateAndCategoryReplaceOnlyTheAffectedRows() {
        val mapper = TransactionItemMapper()
        val first = transaction(1)
        val second = transaction(2).copy(categoryId = 2)
        val other = food.copy(id = 2, name = "Outros")
        val initial = mapper.map(listOf(first, second), listOf(food, other))
        val changed = mapper.map(listOf(first.copy(amount = Money(9999), date = first.date.plusDays(1)), second), listOf(food, other))
        assertNotSame(initial[0], changed[0])
        assertEquals("R$ 99,99", changed[0].formattedAmount)
        assertEquals("02/02/2026", changed[0].formattedDate)
        assertSame(initial[1], changed[1])
        val renamed = mapper.map(listOf(changed[0].transaction, second), listOf(food, other.copy(name = "Diversos")))
        assertSame(changed[0], renamed[0])
        assertNotSame(changed[1], renamed[1])
        assertEquals("Diversos", renamed[1].category?.name)
    }

    @Test
    fun removedRowsAreEvictedInsteadOfAccumulatingAcrossMonths() {
        val mapper = TransactionItemMapper()
        val transaction = transaction(1)
        val initial = mapper.map(listOf(transaction), listOf(food)).single()
        assertTrue(mapper.map(emptyList(), listOf(food)).isEmpty())
        val restored = mapper.map(listOf(transaction), listOf(food)).single()
        assertNotSame(initial, restored)
        assertEquals(initial, restored)
    }
}
