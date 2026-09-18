package br.com.otavioesteves.finances.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import br.com.otavioesteves.finances.domain.model.CategorySummary
import br.com.otavioesteves.finances.domain.model.CategoryType
import br.com.otavioesteves.finances.domain.model.Money
import br.com.otavioesteves.finances.utils.MoneyFormatter
import kotlin.math.roundToInt

// Categorical palette validated for the app's dark surface (#141414) with the
// dataviz skill's validator: fixed hue order, never cycled or reassigned by value.
private val CategoricalPalette = listOf(
    Color(0xFF3987E5), // blue
    Color(0xFFD95926), // orange
    Color(0xFF199E70), // aqua
    Color(0xFFC98500), // yellow
    Color(0xFFD55181), // magenta
    Color(0xFF008300), // green
    Color(0xFF9085E9), // violet
    Color(0xFFE66767) // red
)
private val MAX_SLOTS = CategoricalPalette.size - 1 // last slot reserved for "Outros"
private val OtherCategoryColor = Color(0xFF5C5C5C)

private data class CategoryChartSlice(
    val label: String,
    val amount: Money,
    val fraction: Float,
    val color: Color
)

private fun buildChartSlices(summaries: List<CategorySummary>): List<CategoryChartSlice> {
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
            color = CategoricalPalette[index]
        )
    }

    if (tail.isEmpty()) return headSlices

    val otherAmount = Money.fromCents(tail.sumOf { it.totalAmount.cents })
    return headSlices + CategoryChartSlice(
        label = "Outros",
        amount = otherAmount,
        fraction = otherAmount.cents / totalCents,
        color = OtherCategoryColor
    )
}

@Composable
fun CategoryUsageChart(
    summaries: List<CategorySummary>,
    modifier: Modifier = Modifier
) {
    val slices = remember(summaries) { buildChartSlices(summaries) }

    if (slices.isEmpty()) {
        EmptyState(
            message = "Nenhuma despesa categorizada neste período",
            modifier = modifier
        )
        return
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        CompositionBar(slices = slices)
        CategoryLegend(slices = slices)
    }
}

@Composable
private fun CompositionBar(
    slices: List<CategoryChartSlice>,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(20.dp)
            .clip(RoundedCornerShape(4.dp))
    ) {
        slices.forEachIndexed { index, slice ->
            Box(
                modifier = Modifier
                    .weight(slice.fraction.coerceAtLeast(0.001f))
                    .fillMaxHeight()
                    .background(slice.color)
            )
            // 2dp surface gap between segments, per the app's stacked-bar mark spec.
            if (index != slices.lastIndex) {
                Spacer(
                    modifier = Modifier
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surface)
                )
            }
        }
    }
}

@Composable
private fun CategoryLegend(
    slices: List<CategoryChartSlice>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        slices.forEach { slice ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(slice.color)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = slice.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${(slice.fraction * 100).roundToInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(48.dp)
                )
                Text(
                    text = MoneyFormatter.format(slice.amount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
