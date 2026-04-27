package br.com.otavioesteves.finances.ui.screens.categorydetails

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun CategoryDetailsScreen(
    categoryId: Long,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Detalhes da categoria",
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            text = "categoryId: $categoryId",
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            text = "Tela inicial para evoluir com resumo e transações da categoria.",
            style = MaterialTheme.typography.bodyMedium
        )
        Button(
            onClick = onBackClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Voltar")
        }
    }
}
