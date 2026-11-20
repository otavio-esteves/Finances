package br.com.otavioesteves.finances.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import br.com.otavioesteves.finances.ui.screens.importstatement.ReviewAcknowledgment
import br.com.otavioesteves.finances.ui.theme.FinancesTheme
import org.junit.Rule
import org.junit.Test

class ReviewAccessibilityTest {
    @get:Rule val rule = createComposeRule()
    @Test fun completeLabelIsOneToggleableControlOnSmallScreensWithLargeFonts() {
        val checked = mutableStateOf(false)
        val enabled = mutableStateOf(true)
        val label = "Revisei os possíveis lançamentos repetidos"
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                FinancesTheme {
                    Box(Modifier.width(320.dp)) {
                        ReviewAcknowledgment(label, checked.value, enabled.value, { checked.value = it })
                    }
                }
            }
        }
        rule.onNodeWithText(label).assertIsDisplayed().assertIsOff().assertHeightIsAtLeast(48.dp).performClick()
        rule.onNodeWithText(label).assertIsOn()
        rule.runOnIdle { enabled.value = false }
        rule.onNodeWithText(label).assertIsNotEnabled().assertIsOn()
    }
}
