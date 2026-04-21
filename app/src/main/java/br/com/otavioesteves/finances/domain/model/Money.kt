package br.com.otavioesteves.finances.domain.model

@JvmInline
value class Money(
    val cents: Long
) {
    companion object {
        val Zero = Money(0)

        fun fromCents(cents: Long): Money = Money(cents)
    }
}
