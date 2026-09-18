package org.jls.makeitrun.history.detail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.jls.makeitrun.R
import org.jls.makeitrun.history.model.SessionSample
import org.jls.makeitrun.workout.model.Formats
import kotlin.math.ceil

@Composable
fun SessionChart(samples: List<SessionSample>, modifier: Modifier = Modifier) {
    val lastSecond = samples.maxOfOrNull { it.elapsedSeconds } ?: 0
    val speeds = samples.mapNotNull { it.speedKmh }.filter { it > 0.0 }
    val beats = samples.mapNotNull { it.heartRateBpm }

    val speedColor = MaterialTheme.colorScheme.primary
    val heartColor = MaterialTheme.colorScheme.error
    val axisColor = MaterialTheme.colorScheme.outlineVariant

    val speedCeiling = ceil((speeds.maxOrNull() ?: 0.0) + 1.0)
    val beatsFloor = ((beats.minOrNull() ?: 0) - BEATS_MARGIN).toDouble()
    val beatsCeiling = ((beats.maxOrNull() ?: 0) + BEATS_MARGIN).toDouble()

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(CHART_HEIGHT.dp)
        ) {
            drawLine(
                color = axisColor,
                start = Offset(0f, size.height),
                end = Offset(size.width, size.height),
                strokeWidth = 1.dp.toPx(),
            )

            if (speeds.isNotEmpty()) {
                drawSeries(samples, lastSecond, 0.0, speedCeiling, speedColor) { it.speedKmh }
            }
            if (beats.isNotEmpty()) {
                drawSeries(samples, lastSecond, beatsFloor, beatsCeiling, heartColor) {
                    it.heartRateBpm?.toDouble()
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = Formats.duration(0), style = MaterialTheme.typography.labelSmall)
            Text(
                text = Formats.duration(lastSecond),
                style = MaterialTheme.typography.labelSmall,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            speeds.maxOrNull()?.let {
                LegendEntry(
                    color = speedColor,
                    label = stringResource(R.string.session_report_chart_speed) +
                        " · " + Formats.speed(it),
                )
            }
            beats.maxOrNull()?.let {
                LegendEntry(
                    color = heartColor,
                    label = stringResource(R.string.session_report_chart_heart_rate) +
                        " · " + stringResource(R.string.heart_rate_bpm, it),
                )
            }
        }
    }
}

@Composable
private fun LegendEntry(color: Color, label: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(text = label, style = MaterialTheme.typography.labelSmall)
    }
}

private fun DrawScope.drawSeries(
    samples: List<SessionSample>,
    lastSecond: Int,
    minimum: Double,
    maximum: Double,
    color: Color,
    value: (SessionSample) -> Double?,
) {
    val span = maximum - minimum
    if (span <= 0.0) return

    val duration = lastSecond.coerceAtLeast(1).toFloat()
    val path = Path()
    var drawing = false

    samples.forEach { sample ->
        val raw = value(sample)
        if (raw == null) {
            drawing = false
            return@forEach
        }
        val x = size.width * sample.elapsedSeconds / duration
        val y = size.height * (1f - ((raw - minimum) / span).toFloat().coerceIn(0f, 1f))
        if (drawing) path.lineTo(x, y) else path.moveTo(x, y)
        drawing = true
    }

    drawPath(
        path = path,
        color = color,
        style = Stroke(
            width = 2.dp.toPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        ),
    )
}

private const val CHART_HEIGHT = 180
private const val BEATS_MARGIN = 5
