package br.com.otavioesteves.finances.presentation.addtransaction

import br.com.otavioesteves.finances.data.repository.InMemoryCategoriesRepository
import br.com.otavioesteves.finances.data.repository.InMemoryTransactionsRepository
import br.com.otavioesteves.finances.domain.model.Category
import br.com.otavioesteves.finances.domain.model.CategoryType
import br.com.otavioesteves.finances.domain.usecase.AddTransactionUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AddTransactionViewModelTest {

    private lateinit var viewModel: AddTransactionViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = AddTransactionViewModel(
            AddTransactionUseCase(InMemoryTransactionsRepository()),
            InMemoryCategoriesRepository()
        )
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun saveTransaction_withEmptyDescription_setsError() {
        viewModel.onAmountChange("10,00")
        viewModel.onCategoryChange(Category(1, "Food", CategoryType.EXPENSE))
        viewModel.saveTransaction()

        assertNotNull(viewModel.uiState.value.descriptionError)
        assertEquals("Descrição é obrigatória", viewModel.uiState.value.descriptionError)
    }

    @Test
    fun saveTransaction_withInvalidAmount_setsError() {
        viewModel.onDescriptionChange("Lunch")
        viewModel.onAmountChange("-10,00")
        viewModel.onCategoryChange(Category(1, "Food", CategoryType.EXPENSE))
        viewModel.saveTransaction()

        assertNotNull(viewModel.uiState.value.amountError)
        assertEquals("Valor deve ser maior que zero", viewModel.uiState.value.amountError)
    }

    @Test
    fun saveTransaction_withNoCategory_setsError() {
        viewModel.onDescriptionChange("Lunch")
        viewModel.onAmountChange("10,00")
        viewModel.saveTransaction()

        assertNotNull(viewModel.uiState.value.categoryError)
        assertEquals("Categoria é obrigatória", viewModel.uiState.value.categoryError)
    }
}
