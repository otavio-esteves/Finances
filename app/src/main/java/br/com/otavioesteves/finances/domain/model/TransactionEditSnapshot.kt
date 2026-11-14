package br.com.otavioesteves.finances.domain.model

/** The row and dataset generation must be read together before opening an editor. */
data class TransactionEditSnapshot(val transaction: Transaction, val dataRevision: Long)

class StaleTransactionEditException : IllegalArgumentException(
    "Os dados foram restaurados enquanto este lançamento estava aberto. Volte e abra o lançamento novamente."
)
