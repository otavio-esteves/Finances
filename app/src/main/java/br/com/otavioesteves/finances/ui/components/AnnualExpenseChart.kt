package br.com.otavioesteves.finances.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.otavioesteves.finances.domain.model.Money
import br.com.otavioesteves.finances.utils.MoneyFormatter

private val monthLabels = listOf("jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set", "out", "nov", "dez")

enum class AnnualChartStyle(val label: String) {
    BARS("Barras"), LINE("Linhas"), AREA("Área")
}

internal data class AnnualChartData(val total: Money, val fractions: List<Float>)

/** Keep zero months in their calendar positions for every presentation of the same series. */
internal fun annualChartData(totals: List<Long>): AnnualChartData {
    require(totals.size == 12 && totals.all { it >= 0L })
    val maximum = (totals.maxOrNull() ?: 0L).coerceAtLeast(1L).toDouble()
    return AnnualChartData(
        total = Money.fromCents(totals.fold(0L, Math::addExact)),
        fractions = totals.map { (it.toDouble() / maximum).toFloat() }
    )
}

@Composable
fun AnnualExpenseChart(totals: List<Long>, style: AnnualChartStyle = AnnualChartStyle.BARS) {
    val data = remember(totals) { annualChartData(totals) }
    val barColor = MaterialTheme.colorScheme.primary
    val baselineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
    val description = remember(totals, style) {
        "${style.label}. " + monthLabels.mapIndexed { index, month ->
            "$month: ${MoneyFormatter.format(Money.fromCents(totals[index]))}"
        }.joinToString("; ")
    }
    Text(
        text = MoneyFormatter.format(data.total),
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center
    )
    Canvas(
        modifier = Modifier.fillMaxWidth().height(150.dp).semantics { contentDescription = description }
    ) {
        val slot = size.width / 12f
        val bottom = size.height - 1.dp.toPx()
        val availableHeight = (size.height - 8.dp.toPx()).coerceAtLeast(0f)
        drawLine(baselineColor, Offset(0f, bottom), Offset(size.width, bottom), 1.dp.toPx())
        if (style == AnnualChartStyle.BARS) {
            val barWidth = slot * 0.56f
            data.fractions.forEachIndexed { index, fraction ->
                if (fraction > 0f) {
                    val barHeight = (fraction * availableHeight).coerceAtLeast(3.dp.toPx())
                    drawRoundRect(
                        color = barColor,
                        topLeft = Offset(slot * index + (slot - barWidth) / 2f, bottom - barHeight),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(barWidth / 3f, barWidth / 3f)
                    )
                }
            }
        } else if (data.fractions.any { it > 0f }) {
            val path = Path()
            data.fractions.forEachIndexed { index, fraction ->
                val x = slot * (index + 0.5f)
                val y = bottom - fraction * availableHeight
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            if (style == AnnualChartStyle.AREA) {
                val area = Path().apply {
                    addPath(path)
                    lineTo(slot * 11.5f, bottom)
                    lineTo(slot * 0.5f, bottom)
                    close()
                }
                drawPath(area, barColor.copy(alpha = 0.16f))
            }
            drawPath(path, barColor, style = Stroke(width = 2.dp.toPx()))
            data.fractions.forEachIndexed { index, fraction ->
                drawCircle(barColor, radius = 3.dp.toPx(), center = Offset(slot * (index + 0.5f), bottom - fraction * availableHeight))
            }
        }
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(0.dp)) {
        monthLabels.forEach { month ->
            Text(
                text = month,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}
