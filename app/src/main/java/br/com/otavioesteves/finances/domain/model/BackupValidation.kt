package br.com.otavioesteves.finances.domain.model

/** Apply storage limits before replacing data, including fields outside transactions. */
fun BackupModel.requirePersistableText() {
    categories.forEach { requirePersistableText(it.name, "nome da categoria") }
    transactions.forEach {
        requirePersistableText(it.description, "descrição")
        requirePersistableText(it.notes, "observações")
        requirePersistableText(it.iconKey, "ícone")
        requirePersistableText(it.sourceLabel, "conta/cartão")
        requirePersistableText(it.categoryMemoryDescriptionKey, "memória da descrição")
        requirePersistableText(it.date, "data")
    }
    statementImports.forEach {
        requirePersistableText(it.fileName, "nome do arquivo")
        requirePersistableText(it.sourceLabel, "conta/cartão")
        requirePersistableText(it.fingerprint, "identificação da importação")
        requirePersistableText(it.importedAt, "data da importação")
    }
    chatMessages.forEach {
        requirePersistableText(it.content, "mensagem")
        requirePersistableText(it.createdAt, "data da mensagem")
    }
    categoryMemories.forEach { requirePersistableText(it.descriptionKey, "memória da descrição") }
}
