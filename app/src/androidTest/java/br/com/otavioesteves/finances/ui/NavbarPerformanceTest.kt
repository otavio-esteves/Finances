package br.com.otavioesteves.finances.ui

import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.view.FrameMetrics
import android.view.Window
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.otavioesteves.finances.MainActivity
import br.com.otavioesteves.finances.MainApplication
import br.com.otavioesteves.finances.data.local.AppDatabase
import br.com.otavioesteves.finances.data.local.entity.TransactionEntity
import br.com.otavioesteves.finances.domain.model.CategoryType
import br.com.otavioesteves.finances.domain.model.TransactionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

/** Opt-in comparison on an isolated app; timing is diagnostic, never a device-independent assertion. */
@RunWith(AndroidJUnit4::class)
class NavbarPerformanceTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun recordsNavbarFramesWithPopulatedHistory() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue(InstrumentationRegistry.getArguments().getString("navigationBenchmark") == "true")
        val context = instrumentation.targetContext
        require(context.packageName.endsWith(".validation")) { "Use the isolated validation build." }
        val automation = instrumentation.uiAutomation
        val app = context.applicationContext as MainApplication
        val database = AppDatabase.getDatabase(context, app.applicationScope)
        fun removeFixtures() = database.openHelper.writableDatabase.execSQL(
            "DELETE FROM transactions WHERE notes = ?", arrayOf(FIXTURE_NOTE)
        )
        runBlocking(Dispatchers.IO) {
            removeFixtures()
            val categories = withTimeout(30_000) {
                app.container.categoriesRepository.getCategories().first { it.any { c -> c.type == CategoryType.EXPENSE } }
            }.filter { it.type == CategoryType.EXPENSE }
            val date = app.container.dateProvider.getCurrentDate()
            database.transactionDao().insertTransactions(List(1_200) { index ->
                TransactionEntity(
                    description = "Lançamento de desempenho ${index + 1}", amountCents = 500L + index,
                    categoryId = categories[index % categories.size].id,
                    date = date.withDayOfMonth(index % 28 + 1), type = TransactionType.EXPENSE, notes = FIXTURE_NOTE
                )
            })
        }
        val thread = HandlerThread("navbar-frame-metrics").apply { start() }
        val frames = Collections.synchronizedList(mutableListOf<Double>())
        val missed = AtomicInteger()
        val listener = Window.OnFrameMetricsAvailableListener { _, metrics, dropped ->
            frames += metrics.getMetric(FrameMetrics.TOTAL_DURATION) / 1_000_000.0
            missed.addAndGet(dropped)
        }
        val timings = JSONArray()
        var listenerAdded = false
        try {
            rule.waitForIdle()
            // Warm the renderer on every page before comparing repeated taps.
            listOf("Gráficos", "Transações", "Chat", "Início").forEach {
                rule.onNodeWithContentDescription(it).performClick(); rule.waitForIdle()
            }
            fun requireAppInForeground() {
                val foreground = automation.rootInActiveWindow?.packageName?.toString()
                check(foreground == context.packageName) {
                    "Active window: $foreground. Dismiss system dialogs before benchmarking."
                }
            }
            requireAppInForeground()
            rule.runOnUiThread { rule.activity.window.addOnFrameMetricsAvailableListener(listener, Handler(thread.looper)) }
            listenerAdded = true
            val sequence = listOf("Gráficos", "Transações", "Chat", "Início", "Chat", "Gráficos", "Início", "Transações")
            repeat(3) {
                sequence.forEach { tab ->
                    val start = SystemClock.elapsedRealtimeNanos()
                    rule.onNodeWithContentDescription(tab).performClick()
                    rule.waitForIdle()
                    timings.put(JSONObject().put("tab", tab)
                        .put("clickToIdleMs", (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000.0))
                }
            }
            requireAppInForeground()
        } finally {
            if (listenerAdded) rule.runOnUiThread { rule.activity.window.removeOnFrameMetricsAvailableListener(listener) }
            thread.quitSafely(); thread.join()
            val sorted = synchronized(frames) { frames.sorted() }
            fun percentile(fraction: Double) = sorted.getOrNull(((sorted.size - 1) * fraction).toInt()) ?: 0.0
            val report = JSONObject().put("frames", sorted.size).put("p50FrameMs", percentile(0.5))
                .put("p95FrameMs", percentile(0.95)).put("framesOver16ms", sorted.count { it > 16.67 })
                .put("framesOver32ms", sorted.count { it > 33.33 }).put("droppedMetricReports", missed.get())
                .put("navigation", timings)
            File(context.filesDir, "navbar-performance.json").writeText(report.toString(2))
            runBlocking { withContext(Dispatchers.IO) { removeFixtures() } }
        }
    }

    private companion object { const val FIXTURE_NOTE = "NAVBAR_PERFORMANCE_FIXTURE" }
}
