package br.com.otavioesteves.finances.domain.model

class StatementPasswordRequiredException(val incorrectPassword: Boolean = false) : IllegalArgumentException(
    if (incorrectPassword) "Senha incorreta. Confira a senha do PDF e tente novamente." else "Este PDF precisa de senha para ser aberto."
)
