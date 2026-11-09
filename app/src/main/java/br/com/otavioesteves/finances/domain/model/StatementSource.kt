package br.com.otavioesteves.finances.domain.model

import java.text.Normalizer
import java.util.Locale

/** The user-supplied account/card label scopes duplicate claims, including historical ones. */
fun statementSourceKey(label: String?): String = Normalizer.normalize(label.orEmpty(), Normalizer.Form.NFKC)
    .trim().lowercase(Locale.ROOT).replace(SOURCE_WHITESPACE, " ")

private val SOURCE_WHITESPACE = Regex("\\s+")
