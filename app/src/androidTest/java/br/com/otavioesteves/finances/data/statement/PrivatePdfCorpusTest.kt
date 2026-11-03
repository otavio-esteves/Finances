package br.com.otavioesteves.finances.data.statement

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.otavioesteves.finances.domain.model.StatementKind
import br.com.otavioesteves.finances.domain.model.TransactionType
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Opt-in local corpus: PDFs are staged in private cache, never packaged in the test APK. */
@RunWith(AndroidJUnit4::class)
class PrivatePdfCorpusTest {
    @Test
    fun validatesStagedDocuments() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("privatePdfValidation") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "private-pdf-validation")
        val cases = JSONArray(File(directory, "cases.json").readText())
        require(cases.length() > 0) { "The private corpus is empty." }
        val results = JSONArray()
        var failures = 0
        for (index in 0 until cases.length()) {
            val case = cases.getJSONObject(index)
            val id = case.getString("id")
            require(id.matches(Regex("[a-zA-Z0-9_-]+")))
            val result = JSONObject().put("id", id)
            try {
                val content = File(directory, "$id.pdf").readBytes()
                val extraction = PdfBoxTextExtractor(context).extractWithMetadata(content)
                File(directory, "$id.extracted.txt").writeText(extraction.text)
                result.put("usedOcr", extraction.usedOcr)
                val extractor = object : PdfTextExtractor {
                    override fun extract(content: ByteArray) = extraction.text
                    override fun extractWithMetadata(content: ByteArray, password: String?, checkCancelled: () -> Unit) = extraction
                }
                val entries = PdfStatementParser(extractor)
                    .parse("$id.pdf", content, StatementKind.valueOf(case.getString("kind")))
                val expense = entries.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount.cents }
                val income = entries.filter { it.type == TransactionType.INCOME }.sumOf { it.amount.cents }
                val transfers = entries.filter { it.type == TransactionType.TRANSFER }.sumOf { it.amount.cents }
                result.put("count", entries.size).put("expenseCents", expense).put("incomeCents", income).put("transferCents", transfers)
                result.put("entries", JSONArray(entries.map {
                    JSONObject().put("date", it.date.toString()).put("description", it.description)
                        .put("cents", it.amount.cents).put("type", it.type.name)
                }))
                if (case.has("count")) assertEquals("$id: count", case.getInt("count"), entries.size)
                if (case.has("expenseCents")) assertEquals("$id: expenses", case.getLong("expenseCents"), expense)
                if (case.has("incomeCents")) assertEquals("$id: income", case.getLong("incomeCents"), income)
                if (case.has("transferCents")) assertEquals("$id: transfers", case.getLong("transferCents"), transfers)
                result.put("ok", true)
            } catch (error: Exception) {
                failures++
                result.put("ok", false).put("error", error.message)
            } catch (error: AssertionError) {
                failures++
                result.put("ok", false).put("error", error.message)
            }
            results.put(result)
        }
        File(directory, "results.json").writeText(results.toString(2))
        assertEquals("See private cache results.json for local diagnostics", 0, failures)
    }

}
