package br.com.otavioesteves.finances.domain.model

class StaleStatementImportException : IllegalArgumentException(
    "Os dados foram restaurados. Selecione o arquivo novamente para importar ou confira o histórico antes de desfazer."
)
