package br.com.otavioesteves.finances.ui

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

/** Opt-in captures of synthetic test fixtures on the disposable validation emulator. */
internal fun captureReviewScreenshot(rule: ComposeContentTestRule, name: String) {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    if (Build.VERSION.SDK_INT < 26 || InstrumentationRegistry.getArguments().getString("captureReview") != "true" ||
        (Build.HARDWARE != "ranchu" && Build.HARDWARE != "goldfish")) return
    val directory = File(instrumentation.targetContext.filesDir, "review-captures").apply { mkdirs() }
    rule.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
