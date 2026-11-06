package br.com.otavioesteves.finances.domain.model

import java.text.Normalizer
import java.util.Locale

/** Exact normalized description, retaining digits and punctuation to avoid merging merchants. */
fun categoryMemoryKey(description: String): String = Normalizer.normalize(description, Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT).trim().replace(Regex("\\s+"), " ")

data class CategoryMemory(val descriptionKey: String, val inputType: TransactionType, val categoryId: Long)
