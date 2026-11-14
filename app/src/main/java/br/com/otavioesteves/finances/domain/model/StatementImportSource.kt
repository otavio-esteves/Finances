package br.com.otavioesteves.finances.domain.model

import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale

/** Import labels are snapshots: later transaction edits do not change batch ownership. */
fun statementImportSourceKey(label: String?): String = Normalizer.normalize(label.orEmpty(), Normalizer.Form.NFD)
    .replace(SOURCE_DIACRITICS, "").trim().lowercase(Locale.ROOT).replace(SOURCE_WHITESPACE, " ")

/** Keep the content fingerprint intact for legacy lookups while claiming one batch per source. */
fun scopedStatementFingerprint(contentFingerprint: String, sourceLabel: String?): String {
    val sourceDigest = MessageDigest.getInstance("SHA-256")
        .digest(statementImportSourceKey(sourceLabel).toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
    return "v3:$contentFingerprint:$sourceDigest"
}

private val SOURCE_DIACRITICS = Regex("\\p{M}+")
private val SOURCE_WHITESPACE = Regex("[\\s\\p{Z}]+")
