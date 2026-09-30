package br.com.otavioesteves.finances.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import br.com.otavioesteves.finances.domain.model.CategorySummary
import br.com.otavioesteves.finances.domain.model.CategoryType
import br.com.otavioesteves.finances.domain.model.Money
import br.com.otavioesteves.finances.ui.theme.CategoryPalette
import br.com.otavioesteves.finances.ui.theme.OtherCategoryColor
import br.com.otavioesteves.finances.ui.theme.FinancesThemeTokens
import br.com.otavioesteves.finances.utils.MoneyFormatter
import kotlin.math.roundToInt

private val MAX_SLOTS = CategoryPalette.size - 1 // last slot reserved for "Outros"
private val PT_BR = Locale.forLanguageTag("pt-BR")

internal fun chartCenterAmount(amount: Money): String {
    val value = BigDecimal.valueOf(amount.cents, 2)
    val billion = BigDecimal("1000000000")
    val million = BigDecimal("1000000")
    val thousand = BigDecimal("1000")
    val unit = when {
        value >= billion -> billion to "bi"
        value >= million -> million to "mi"
        value >= BigDecimal("10000") -> thousand to "mil"
        else -> return MoneyFormatter.format(amount)
    }
    val compact = value.divide(unit.first, 1, RoundingMode.DOWN)
    val formatter = DecimalFormat("#,##0.#", DecimalFormatSymbols(PT_BR))
    return "R$ ${formatter.format(compact)} ${unit.second}"
}

private data class CategoryChartSlice(
    val label: String,
    val amount: Money,
    val fraction: Float,
    val color: Color
)

private fun buildChartSlices(
    summaries: List<CategorySummary>,
    palette: List<Color>,
    otherColor: Color
): List<CategoryChartSlice> {
    val expenses = summaries
        .filter { it.category.type == CategoryType.EXPENSE && it.totalAmount.cents > 0 }
        .sortedByDescending { it.totalAmount.cents }

    if (expenses.isEmpty()) return emptyList()

    val totalCents = expenses.sumOf { it.totalAmount.cents }.toFloat()
    val head = expenses.take(MAX_SLOTS)
    val tail = expenses.drop(MAX_SLOTS)

    val headSlices = head.mapIndexed { index, summary ->
        CategoryChartSlice(
            label = summary.category.name,
            amount = summary.totalAmount,
            fraction = summary.totalAmount.cents / totalCents,
            color = palette[index]
        )
    }

    if (tail.isEmpty()) return headSlices

    val otherAmount = Money.fromCents(tail.sumOf { it.totalAmount.cents })
    return headSlices + CategoryChartSlice(
        label = "Outros",
        amount = otherAmount,
        fraction = otherAmount.cents / totalCents,
        color = otherColor
    )
}

@Composable
fun CategoryUsageChart(
    summaries: List<CategorySummary>,
    modifier: Modifier = Modifier
) {
    val colors = FinancesThemeTokens.colors
    val slices = remember(summaries, colors) {
        buildChartSlices(summaries, colors.categoryPalette, colors.otherCategory)
    }

    if (slices.isEmpty()) {
        EmptyState(
            message = "Nenhuma despesa categorizada neste período",
            modifier = modifier
        )
        return
    }

    val totalAmount = remember(slices) { Money.fromCents(slices.sumOf { it.amount.cents }) }
    val centerAmount = remember(totalAmount) { chartCenterAmount(totalAmount) }
    val fullAmount = remember(totalAmount) { MoneyFormatter.format(totalAmount) }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        DonutRing(
            slices = slices,
            centerLabel = centerAmount,
            centerCaption = "em gastos",
            modifier = Modifier
                .fillMaxWidth(0.62f)
                .aspectRatio(1f)
        )
        if (centerAmount != fullAmount) {
            Text(
                text = "Total: $fullAmount",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        DonutLegend(slices = slices)
    }
}

@Composable
private fun DonutRing(
    slices: List<CategoryChartSlice>,
    centerLabel: String,
    centerCaption: String,
    modifier: Modifier = Modifier
) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
            val strokeWidth = size.minDimension * 0.16f
            val stroke = Stroke(width = strokeWidth)
            val diameter = size.minDimension - strokeWidth
            val topLeft = androidx.compose.ui.geometry.Offset(
                (size.width - diameter) / 2f,
                (size.height - diameter) / 2f
            )
            val arcSize = androidx.compose.ui.geometry.Size(diameter, diameter)

            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = stroke
            )

            // 2° gaps between segments, matching the app's stacked-bar mark spec.
            val gapDegrees = if (slices.size > 1) 2f else 0f
            var startAngle = -90f
            slices.forEach { slice ->
                val sweep = (slice.fraction * 360f) - gapDegrees
                drawArc(
                    color = slice.color,
                    startAngle = startAngle,
                    sweepAngle = sweep.coerceAtLeast(0f),
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = stroke
                )
                startAngle += slice.fraction * 360f
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth(0.68f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = centerLabel,
                style = if (centerLabel.length > 10) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
            Text(
                text = centerCaption,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun DonutLegend(
    slices: List<CategoryChartSlice>,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        slices.forEach { slice ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(slice.color)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = slice.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${(slice.fraction * 100).roundToInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
