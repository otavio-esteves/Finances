package br.com.otavioesteves.finances.data.local.entity

/** Small projection used to check the final dataset before an atomic write. */
data class TransactionAmountSnapshot(val id: Long, val amountCents: Long)
