package br.com.otavioesteves.finances.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import br.com.otavioesteves.finances.MainActivity
import br.com.otavioesteves.finances.MainApplication
import br.com.otavioesteves.finances.data.local.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CategoryCreationUiTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun newCategoryIsAvailableInTheTransactionForm() {
        val name = "Categoria teste ${System.nanoTime()}"
        val app = composeRule.activity.application as MainApplication
        try {
            composeRule.onNodeWithContentDescription("Categorias").performClick()
            composeRule.onNodeWithText("Criar categoria").performClick()
            composeRule.onNodeWithText("Nome da categoria").performTextInput(name)
            composeRule.onNode(hasText("Criar") and hasAnyAncestor(isDialog())).performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithContentDescription("Importar fatura ou extrato").performClick()
            composeRule.onNodeWithText("Adicionar lançamento").performScrollTo().performClick()
            composeRule.onNodeWithText("Categoria").performScrollTo().performClick()
            composeRule.onNodeWithText(name).performScrollTo().performClick()
            composeRule.onNodeWithText(name).assertIsDisplayed()
        } finally {
            runBlocking {
                val database = AppDatabase.getDatabase(app, app.applicationScope)
                database.openHelper.writableDatabase.execSQL("DELETE FROM categories WHERE name = ?", arrayOf(name))
            }
        }
    }
    @Test
    fun categoryCanBeCreatedWithoutLeavingTheTransactionForm() {
        val name = "Categoria formulário ${System.nanoTime()}"
        val app = composeRule.activity.application as MainApplication
        try {
            composeRule.onNodeWithContentDescription("Importar fatura ou extrato").performClick()
            composeRule.onNodeWithText("Extrato bancário").assertIsDisplayed()
            composeRule.onNodeWithText("Fatura de cartão").performClick()
            composeRule.onNodeWithText("Adicionar lançamento").performScrollTo().performClick()
            composeRule.onNodeWithText("Descrição").performTextInput("Compra preservada")
            composeRule.onNodeWithText("Categoria").performScrollTo().performClick()
            composeRule.onNodeWithText("Nova categoria").performScrollTo().performClick()
            composeRule.onNodeWithText("Nome da categoria").performTextInput(name)
            composeRule.onNode(hasText("Criar") and hasAnyAncestor(isDialog())).performClick()
            composeRule.waitUntil(10_000) {
                composeRule.onAllNodesWithText(name).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText(name).assertIsDisplayed()
            composeRule.onNodeWithText("Compra preservada").performScrollTo().assertIsDisplayed()
        } finally {
            runBlocking {
                val database = AppDatabase.getDatabase(app, app.applicationScope)
                database.openHelper.writableDatabase.execSQL("DELETE FROM categories WHERE name = ?", arrayOf(name))
            }
        }
    }

}
