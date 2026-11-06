package br.com.otavioesteves.finances.ui

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import br.com.otavioesteves.finances.MainActivity
import br.com.otavioesteves.finances.MainApplication
import br.com.otavioesteves.finances.data.preferences.ThemePreferences
import br.com.otavioesteves.finances.domain.model.ThemeMode
import br.com.otavioesteves.finances.domain.model.ThemePalette
import android.view.WindowManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppearanceSettingsTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun screenshotChoiceAppliesImmediatelyAndSurvivesRecreation() {
        val preferences = (composeRule.activity.application as MainApplication).themePreferences
        val original = preferences.allowScreenshots.value
        fun isSecure() = composeRule.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0
        try {
            composeRule.runOnIdle { preferences.setAllowScreenshots(false) }
            composeRule.waitForIdle()
            composeRule.runOnIdle { assertTrue(isSecure()) }
            composeRule.onNodeWithContentDescription("Abrir menu").performClick()
            composeRule.onNodeWithText("Configurações").performScrollTo().performClick()
            composeRule.onNodeWithText("Permitir capturas de tela").performScrollTo().performClick()
            composeRule.waitUntil(5_000) { !isSecure() }
            composeRule.runOnIdle {
                assertTrue(ThemePreferences(composeRule.activity).allowScreenshots.value)
                assertFalse(isSecure())
            }
            composeRule.activityRule.scenario.recreate()
            composeRule.waitUntil(5_000) { !isSecure() }
            composeRule.runOnIdle { assertFalse(isSecure()) }
            composeRule.onNodeWithText("Permitir capturas de tela").performScrollTo().performClick()
            composeRule.waitUntil(5_000) { isSecure() }
            composeRule.runOnIdle { assertTrue(isSecure()) }
        } finally {
            composeRule.runOnIdle { preferences.setAllowScreenshots(original) }
        }
    }

    @Test
    fun appearanceChoicesApplyAndPersist() {
        val preferences = (composeRule.activity.application as MainApplication).themePreferences
        val originalMode = preferences.mode.value
        val originalPalette = preferences.palette.value
        try {
            composeRule.onNodeWithContentDescription("Abrir menu").performClick()
            composeRule.onNodeWithText("Configurações").performScrollTo().performClick()
            ThemeMode.entries.forEach { mode ->
                composeRule.onNodeWithText("Tema").performScrollTo().performClick()
                composeRule.onNode(hasText(mode.label) and hasAnyAncestor(isDialog())).performClick()
                composeRule.onNodeWithText(mode.label).assertIsDisplayed()
                composeRule.runOnIdle {
                    assertEquals(mode, ThemePreferences(composeRule.activity).mode.value)
                }
            }
            ThemePalette.entries.forEach { palette ->
                composeRule.onNodeWithText("Paleta de cores").performScrollTo().performClick()
                composeRule.onNode(hasText(palette.label) and hasAnyAncestor(isDialog())).performScrollTo().performClick()
                composeRule.onNodeWithText(palette.label).assertIsDisplayed()
                composeRule.runOnIdle {
                    assertEquals(palette, ThemePreferences(composeRule.activity).palette.value)
                }
            }
            composeRule.activityRule.scenario.recreate()
            composeRule.onNodeWithText("Aparelho").assertIsDisplayed()
            composeRule.onNodeWithText(ThemePalette.entries.last().label).assertIsDisplayed()
        } finally {
            composeRule.runOnIdle {
                preferences.setMode(originalMode)
                preferences.setPalette(originalPalette)
            }
        }
    }
}
