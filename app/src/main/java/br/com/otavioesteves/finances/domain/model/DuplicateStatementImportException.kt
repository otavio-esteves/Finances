package br.com.otavioesteves.finances.domain.model

class DuplicateStatementImportException : IllegalStateException(
    "Este extrato já foi importado. Nenhuma transação foi adicionada."
)
