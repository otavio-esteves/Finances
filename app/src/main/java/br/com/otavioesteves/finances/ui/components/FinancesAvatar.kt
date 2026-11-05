package br.com.otavioesteves.finances.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import br.com.otavioesteves.finances.R

/** The app's own mark gives the local assistant a familiar, consistent identity. */
@Composable
fun FinancesAvatar(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.clip(CircleShape)
            .background(Brush.linearGradient(listOf(Color(0xFF2563B9), Color(0xFF102C59))))
            .border(1.dp, Color(0xFF79ADFF).copy(alpha = 0.55f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = "Assistente Finances",
            modifier = Modifier.fillMaxSize().padding(1.dp).graphicsLayer {
                // The launcher mark includes adaptive-icon insets; crop those for a legible avatar.
                scaleX = 1.65f
                scaleY = 1.65f
            }
        )
    }
}
