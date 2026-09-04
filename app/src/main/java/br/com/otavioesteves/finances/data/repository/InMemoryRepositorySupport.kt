package br.com.otavioesteves.finances.data.repository

import br.com.otavioesteves.finances.domain.model.Money
import br.com.otavioesteves.finances.domain.model.Transaction
import br.com.otavioesteves.finances.domain.model.TransactionType

internal object InMemoryRepositorySupport {
    val sharedStore: InMemoryFinanceStore = InMemoryFinanceStore.createDefault()
}

internal fun Transaction.contributionAmount(): Money {
    return when (type) {
        TransactionType.INCOME -> amount
        TransactionType.EXPENSE -> amount
        TransactionType.TRANSFER -> Money.Zero
    }
}
