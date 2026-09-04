package br.com.otavioesteves.finances.domain.model

@JvmInline
value class Money(
    val cents: Long
) {
    operator fun plus(other: Money): Money = Money(Math.addExact(cents, other.cents))

    operator fun minus(other: Money): Money = Money(Math.subtractExact(cents, other.cents))

    operator fun unaryMinus(): Money = Money(Math.negateExact(cents))

    fun isPositive(): Boolean = cents > 0

    fun isNegative(): Boolean = cents < 0

    fun isZero(): Boolean = cents == 0L

    companion object {
        val Zero = Money(0)

        fun fromCents(cents: Long): Money = Money(cents)
    }
}

fun Iterable<Money>.sumMoney(): Money = fold(Money.Zero, Money::plus)
