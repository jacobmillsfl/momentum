package com.momentum.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.dp
import com.momentum.app.data.repo.DomainCompletionTotals

private val MindPastelBlue = Color(0xFFB8D4F0)
private val BodyPastelPink = Color(0xFFF5C6D8)
private val GaugeRed = Color(0xFFE57373)
private val GaugeGreen = Color(0xFF66BB6A)
private const val NET_CAP = 3.0

private data class HalfVisual(
    val fillFraction: Float,
    val arcStrokeColor: Color,
    val drawFill: Boolean,
)

private fun halfVisual(totals: DomainCompletionTotals): HalfVisual {
    val p = totals.positive
    val n = totals.negative
    val hasActivity = p > 1e-9 || n > 1e-9
    if (!hasActivity) {
        return HalfVisual(
            fillFraction = 0f,
            arcStrokeColor = Color.Transparent,
            drawFill = false,
        )
    }
    val net = p - n
    val fillFraction = if (net > 0) {
        (kotlin.math.min(net, NET_CAP) / NET_CAP).toFloat().coerceIn(0f, 1f)
    } else {
        0f
    }
    val arcStroke = when {
        n > p + 1e-9 -> GaugeRed
        net >= NET_CAP - 1e-9 -> GaugeGreen
        else -> Color.Transparent
    }
    return HalfVisual(
        fillFraction = fillFraction,
        arcStrokeColor = arcStroke,
        drawFill = net > 0 && fillFraction > 0f,
    )
}

/**
 * Dual half-circle gauge: Mind (left, pastel blue) and Body (right, pastel pink).
 * Fill rises bottom-to-top when net positive completions are in (0, 3]; arcs highlight red/green per spec.
 */
@Composable
fun TodayMindBodyGauge(
    mind: DomainCompletionTotals,
    body: DomainCompletionTotals,
    modifier: Modifier = Modifier,
) {
    val mindV = remember(mind.positive, mind.negative) { halfVisual(mind) }
    val bodyV = remember(body.positive, body.negative) { halfVisual(body) }
    val neutralArc = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    val dividerColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
    val ringBg = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)

    Column(modifier = modifier) {
        Text(
            "How today is going",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Mind (left) and Body (right) from today's habit completions. Fill scales with net positive (0-3).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .padding(horizontal = 8.dp),
        ) {
            val r = size.minDimension * 0.38f
            val c = Offset(size.width / 2f, size.height / 2f)

            fun leftWedgePath(): Path = Path().apply {
                moveTo(c.x, c.y)
                arcTo(
                    rect = Rect(c.x - r, c.y - r, c.x + r, c.y + r),
                    startAngleDegrees = 90f,
                    sweepAngleDegrees = 180f,
                    forceMoveTo = false,
                )
                close()
            }

            fun rightWedgePath(): Path = Path().apply {
                moveTo(c.x, c.y)
                arcTo(
                    rect = Rect(c.x - r, c.y - r, c.x + r, c.y + r),
                    startAngleDegrees = 270f,
                    sweepAngleDegrees = 180f,
                    forceMoveTo = false,
                )
                close()
            }

            // Subtle full orb silhouette
            drawCircle(
                color = ringBg,
                radius = r,
                center = c,
                style = Stroke(width = 3.dp.toPx()),
            )

            // Mind fill
            if (mindV.drawFill) {
                clipPath(leftWedgePath()) {
                    val h = 2f * r * mindV.fillFraction
                    drawRect(
                        color = MindPastelBlue.copy(alpha = 0.92f),
                        topLeft = Offset(c.x - r, c.y + r - h),
                        size = Size(2f * r, h),
                    )
                }
            }

            // Body fill
            if (bodyV.drawFill) {
                clipPath(rightWedgePath()) {
                    val h = 2f * r * bodyV.fillFraction
                    drawRect(
                        color = BodyPastelPink.copy(alpha = 0.92f),
                        topLeft = Offset(c.x - r, c.y + r - h),
                        size = Size(2f * r, h),
                    )
                }
            }

            // Center divider
            drawLine(
                color = dividerColor,
                start = Offset(c.x, c.y - r),
                end = Offset(c.x, c.y + r),
                strokeWidth = 2.5.dp.toPx(),
            )

            fun strokeColor(arcStroke: Color) =
                if (arcStroke != Color.Transparent) arcStroke else neutralArc

            // Left arc stroke
            drawPath(
                path = Path().apply {
                    addArc(
                        oval = Rect(c.x - r, c.y - r, c.x + r, c.y + r),
                        startAngleDegrees = 90f,
                        sweepAngleDegrees = 180f,
                    )
                },
                color = strokeColor(mindV.arcStrokeColor),
                style = Stroke(width = 3.5.dp.toPx()),
            )
            // Right arc stroke
            drawPath(
                path = Path().apply {
                    addArc(
                        oval = Rect(c.x - r, c.y - r, c.x + r, c.y + r),
                        startAngleDegrees = 270f,
                        sweepAngleDegrees = 180f,
                    )
                },
                color = strokeColor(bodyV.arcStrokeColor),
                style = Stroke(width = 3.5.dp.toPx()),
            )
        }
        RowLabels()
    }
}

@Composable
private fun RowLabels() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, start = 12.dp, end = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            "Mind",
            style = MaterialTheme.typography.labelLarge,
            color = MindPastelBlue.copy(alpha = 0.95f),
        )
        Text(
            "Body",
            style = MaterialTheme.typography.labelLarge,
            color = BodyPastelPink.copy(alpha = 0.95f),
        )
    }
}
