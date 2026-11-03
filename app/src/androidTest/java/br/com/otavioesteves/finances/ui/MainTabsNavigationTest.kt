package br.com.otavioesteves.finances.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import br.com.otavioesteves.finances.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainTabsNavigationTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun bottomBarOpensEachPrimaryPage() {
        composeRule.onNodeWithContentDescription("Gráficos").performClick()
        composeRule.onNodeWithTag("charts_title").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Transações").performClick()
        composeRule.onNodeWithTag("transactions_title").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Chat").performClick()
        composeRule.onNodeWithText("Seu assistente").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Início").performClick()
        composeRule.onNodeWithContentDescription("Abrir menu").assertIsDisplayed()
    }

    @Test
    fun horizontalSwipesActivateEachPrefetchedPage() {
        composeRule.onRoot().performTouchInput { swipeLeft(durationMillis = 250) }
        composeRule.onNodeWithTag("charts_title").assertIsDisplayed()
        composeRule.onRoot().performTouchInput { swipeLeft(durationMillis = 250) }
        composeRule.onNodeWithTag("transactions_title").assertIsDisplayed()
        composeRule.onRoot().performTouchInput { swipeLeft(durationMillis = 250) }
        composeRule.onNodeWithText("Seu assistente").assertIsDisplayed()
    }

    @Test
    fun rapidTabRequestsKeepTheLastDestinationSelected() {
        val actions = listOf("Gráficos", "Chat", "Início", "Transações", "Chat").map { label ->
            requireNotNull(composeRule.onNodeWithContentDescription(label).fetchSemanticsNode()
                .config[SemanticsActions.OnClick].action)
        }
        composeRule.runOnUiThread { actions.forEach { it() } }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Chat").assertIsSelected()
        composeRule.onNodeWithText("Seu assistente").assertIsDisplayed()
    }

    @Test
    fun chatDraftSurvivesLeavingAndRecreatingThePage() {
        composeRule.onNodeWithContentDescription("Chat").performClick()
        composeRule.onNode(hasSetTextAction()).performTextInput("Rascunho de navegação")
        composeRule.onNodeWithContentDescription("Início").performClick()
        composeRule.onNodeWithContentDescription("Chat").performClick()
        composeRule.onNode(hasSetTextAction()).assertTextEquals("Rascunho de navegação")
    }

    @Test
    fun chartStyleSurvivesLeavingAndRecreatingThePage() {
        composeRule.onNodeWithContentDescription("Gráficos").performClick()
        composeRule.onNodeWithContentDescription("Tipo do gráfico Categorias: Rosca")
            .performScrollTo().performClick()
        composeRule.onNodeWithText("Pizza").performClick()
        composeRule.onNodeWithContentDescription("Chat").performClick()
        composeRule.onNodeWithContentDescription("Gráficos").performClick()
        composeRule.onNodeWithContentDescription("Tipo do gráfico Categorias: Pizza")
            .performScrollTo().assertIsDisplayed()
    }

    @Test
    fun tappingOutsideDrawerClosesIt() {
        composeRule.onNodeWithContentDescription("Abrir menu").performClick()
        composeRule.onNodeWithText("Seu espaço financeiro").assertIsDisplayed()

        composeRule.onRoot().performTouchInput {
            click(Offset(center.x * 1.9f, center.y))
        }

        composeRule.onNodeWithText("Seu espaço financeiro").assertIsNotDisplayed()
    }
}
