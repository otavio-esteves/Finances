package br.com.otavioesteves.finances.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import br.com.otavioesteves.finances.ui.theme.AppIncome
import br.com.otavioesteves.finances.ui.theme.AppExpense

@Composable
fun FinanceCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        ),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        content()
    }
}

@Composable
fun AmountText(
    amount: String,
    modifier: Modifier = Modifier,
    isPositive: Boolean? = null,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.titleLarge
) {
    val color = when (isPositive) {
        true -> AppIncome
        false -> AppExpense
        null -> MaterialTheme.colorScheme.onSurface
    }

    Text(
        text = amount,
        style = style,
        color = color,
        fontWeight = FontWeight.Bold,
        modifier = modifier
    )
}

@Composable
fun SectionTitle(
    title: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(vertical = 8.dp)
    )
}

@Composable
fun PrimaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        )
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(vertical = 4.dp)
        )
    }
}

@Composable
fun EmptyState(
    message: String,
    modifier: Modifier = Modifier,
    onActionClick: (() -> Unit)? = null,
    actionLabel: String = "Tentar novamente"
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        onActionClick?.let {
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(16.dp))
            PrimaryActionButton(
                text = actionLabel,
                onClick = it,
                modifier = Modifier.fillMaxWidth(0.6f)
            )
        }
    }
}
