package br.com.otavioesteves.finances.ui

import android.app.UiAutomation
import android.os.SystemClock
import android.os.Build
import android.graphics.Bitmap
import android.view.WindowManager
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.otavioesteves.finances.MainActivity
import br.com.otavioesteves.finances.MainApplication
import br.com.otavioesteves.finances.data.local.AppDatabase
import br.com.otavioesteves.finances.data.local.entity.ChatMessageEntity
import br.com.otavioesteves.finances.data.local.entity.TransactionEntity
import br.com.otavioesteves.finances.domain.model.CategoryType
import br.com.otavioesteves.finances.domain.model.ChatRole
import br.com.otavioesteves.finances.domain.model.TransactionOrigin
import br.com.otavioesteves.finances.domain.model.TransactionType
import br.com.otavioesteves.finances.utils.MoneyFormatter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime

/** Opt-in visual comparison on a disposable emulator; never seeds ordinary CI or personal devices. */
@RunWith(AndroidJUnit4::class)
class FinancesVisualCaptureTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()
    private lateinit var automation: UiAutomation

    @Test
    fun capturePrimaryPagesWithLargeSyntheticHistory() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("visualCapture") == "true")
        assumeTrue(Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
        automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val app = composeRule.activity.application as MainApplication
        val expectedBalance = runBlocking {
            val categories = app.container.categoriesRepository.getCategories().first { it.isNotEmpty() }
            val database = AppDatabase.getDatabase(app, app.applicationScope)
            val marker = "VALIDACAO_VISUAL_PERFORMANCE"
            if (database.transactionDao().getAllTransactions().none { it.notes == marker }) {
                val expenses = categories.filter { it.type == CategoryType.EXPENSE }
                database.transactionDao().insertTransactions((1..12).flatMap { month ->
                    (1..1000).map { index ->
                        TransactionEntity(
                            id = 0, description = "Validação $index", amountCents = 1000L + index * 13,
                            categoryId = expenses[index % expenses.size].id,
                            date = LocalDate.of(2026, month, (index - 1) % 28 + 1),
                            type = TransactionType.EXPENSE, notes = marker, origin = TransactionOrigin.MANUAL
                        )
                    }
                })
                database.chatMessageDao().insertMessages((1..500).map { index ->
                    ChatMessageEntity(0, if (index % 2 == 0) ChatRole.ASSISTANT else ChatRole.USER,
                        "Conversa de validação $index", LocalDateTime.of(2026, 10, 25, 8, 0).plusSeconds(index.toLong()))
                })
            }
            app.container.getMonthlyBalanceUseCase(app.container.dateProvider.getCurrentMonthPeriod()).first()
        }
        // This only relaxes the test Activity, so production keeps FLAG_SECURE.
        composeRule.runOnUiThread { composeRule.activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
        composeRule.waitUntil(15_000) {
            composeRule.onAllNodesWithText("Validação", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitUntil(15_000) {
            composeRule.onAllNodesWithText(MoneyFormatter.format(expectedBalance)).fetchSemanticsNodes().isNotEmpty()
        }
        // Compare the settled page after one navigation round trip, including its backdrop layers.
        composeRule.onNodeWithContentDescription("Transações").performClick()
        composeRule.onNodeWithContentDescription("Início").performClick()
        composeRule.waitUntil(15_000) {
            composeRule.onAllNodesWithText(MoneyFormatter.format(expectedBalance)).fetchSemanticsNodes().isNotEmpty()
        }
        capture("home", args.getString("captureLabel") ?: "current")
        composeRule.onNodeWithContentDescription("Transações").performClick()
        composeRule.onNodeWithTag("transactions_title").assertExists()
        composeRule.waitUntil(15_000) {
            composeRule.onAllNodesWithText("Validação", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        capture("transactions", args.getString("captureLabel") ?: "current")
        composeRule.onNodeWithContentDescription("Chat").performClick()
        composeRule.waitUntil(15_000) {
            composeRule.onAllNodesWithText("Conversa de validação 500").fetchSemanticsNodes().isNotEmpty()
        }
        capture("chat", args.getString("captureLabel") ?: "current")
        composeRule.onNodeWithContentDescription("Gráficos").performClick()
        composeRule.waitUntil(15_000) {
            composeRule.onAllNodesWithText("Alimentação").fetchSemanticsNodes().isNotEmpty()
        }
        capture("charts", args.getString("captureLabel") ?: "current")
    }

    private fun capture(page: String, label: String) {
        composeRule.waitForIdle()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        // Wait for the software renderer's buffer to reach SurfaceFlinger after Compose is idle.
        SystemClock.sleep(500)
        val output = File(composeRule.activity.getExternalFilesDir(null), "visual-validation/$label-$page.png")
        output.parentFile!!.mkdirs()
        check(automation.rootInActiveWindow?.packageName?.toString() == composeRule.activity.packageName) {
            "The app is covered by another window; refusing an invalid visual capture."
        }
        val bitmap = requireNotNull(automation.takeScreenshot())
        output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
