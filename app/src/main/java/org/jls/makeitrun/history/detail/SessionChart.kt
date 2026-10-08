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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import org.jls.makeitrun.R
import org.jls.makeitrun.history.model.SessionSample
import org.jls.makeitrun.workout.model.Formats
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

@Composable
fun SessionChart(samples: List<SessionSample>, modifier: Modifier = Modifier) {
    val lastSecond = samples.maxOfOrNull { it.elapsedSeconds } ?: 0
    val speeds = samples.mapNotNull { it.speedKmh }.filter { it > 0.0 }
    val beats = samples.mapNotNull { it.heartRateBpm }

    val speedColor = MaterialTheme.colorScheme.primary
    val heartColor = MaterialTheme.colorScheme.error
    val axisColor = MaterialTheme.colorScheme.outlineVariant
    val timeColor = MaterialTheme.colorScheme.onSurfaceVariant
    val labelStyle = MaterialTheme.typography.labelSmall
    val textMeasurer = rememberTextMeasurer()

    val speedAxis = speeds.takeIf { it.isNotEmpty() }
        ?.let { ChartAxis.covering(it.min(), it.max()) }
    val beatsAxis = beats.takeIf { it.isNotEmpty() }
        ?.let { ChartAxis.covering(it.min().toDouble(), it.max().toDouble()) }
    val timeTicks = timeTicks(lastSecond)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(CHART_HEIGHT.dp)
        ) {
            val gap = LABEL_GAP.dp.toPx()
            val speedLabels = speedAxis?.labels().orEmpty()
                .map { textMeasurer.measure(it, labelStyle.copy(color = speedColor)) }
            val beatsLabels = beatsAxis?.labels().orEmpty()
                .map { textMeasurer.measure(it, labelStyle.copy(color = heartColor)) }
            val timeLabels = timeTicks
                .map { textMeasurer.measure(Formats.duration(it), labelStyle.copy(color = timeColor)) }
            val labelHeight = (speedLabels + beatsLabels + timeLabels)
                .maxOfOrNull { it.size.height }?.toFloat() ?: 0f

            val plot = Rect(
                left = (speedLabels.maxOfOrNull { it.size.width }?.plus(gap)) ?: 0f,
                top = labelHeight / 2f,
                right = size.width - ((beatsLabels.maxOfOrNull { it.size.width }?.plus(gap)) ?: 0f),
                bottom = size.height - labelHeight - gap,
            )
            val duration = lastSecond.coerceAtLeast(1).toFloat()

            for (tick in 0..ChartAxis.INTERVALS) {
                val y = plot.bottom - plot.height * tick / ChartAxis.INTERVALS
                drawLine(
                    color = axisColor,
                    start = Offset(plot.left, y),
                    end = Offset(plot.right, y),
                    strokeWidth = (if (tick == 0) 1.dp else 0.5.dp).toPx(),
                )
                speedLabels.getOrNull(tick)?.let { label ->
                    drawText(
                        textLayoutResult = label,
                        topLeft = Offset(
                            plot.left - gap - label.size.width,
                            y - label.size.height / 2f,
                        ),
                    )
                }
                beatsLabels.getOrNull(tick)?.let { label ->
                    drawText(
                        textLayoutResult = label,
                        topLeft = Offset(plot.right + gap, y - label.size.height / 2f),
                    )
                }
            }

            timeTicks.forEachIndexed { index, second ->
                val x = plot.left + plot.width * second / duration
                drawLine(
                    color = axisColor,
                    start = Offset(x, plot.top),
                    end = Offset(x, plot.bottom),
                    strokeWidth = 0.5.dp.toPx(),
                )
                val label = timeLabels[index]
                drawText(
                    textLayoutResult = label,
                    topLeft = Offset(
                        (x - label.size.width / 2f)
                            .coerceIn(0f, size.width - label.size.width),
                        plot.bottom + gap,
                    ),
                )
            }

            speedAxis?.let { axis ->
                drawSeries(samples, plot, duration, axis, speedColor) { it.speedKmh }
            }
            beatsAxis?.let { axis ->
                drawSeries(samples, plot, duration, axis, heartColor) {
                    it.heartRateBpm?.toDouble()
                }
            }
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

internal data class ChartAxis(val minimum: Double, val step: Double) {

    val maximum: Double
        get() = minimum + step * INTERVALS

    fun labels(): List<String> = (0..INTERVALS).map { tick ->
        val value = minimum + step * tick
        if (step % 1.0 == 0.0) "%.0f".format(value) else "%.1f".format(value)
    }

    companion object {
        const val INTERVALS = 4
        private val NICE_FACTORS = listOf(1.0, 2.0, 2.5, 5.0, 10.0)

        fun covering(lowest: Double, highest: Double): ChartAxis {
            val span = (highest - lowest).coerceAtLeast(1.0)
            val magnitude = 10.0.pow(floor(log10(span / INTERVALS)))
            val steps = NICE_FACTORS.map { it * magnitude } + NICE_FACTORS.map { it * magnitude * 10 }
            return steps.firstNotNullOf { step ->
                val minimum = floor(lowest / step) * step
                ChartAxis(minimum, step).takeIf { it.maximum >= highest }
            }
        }
    }
}

internal fun timeTicks(lastSecond: Int): List<Int> {
    if (lastSecond <= 0) return listOf(0)
    val step = TIME_STEPS_SECONDS.firstOrNull { lastSecond / it <= MAX_TIME_TICKS }
        ?: (ceil(lastSecond / (MAX_TIME_TICKS * 3_600.0)).toInt() * 3_600)
    return (0..lastSecond step step).toList()
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
    plot: Rect,
    duration: Float,
    axis: ChartAxis,
    color: Color,
    value: (SessionSample) -> Double?,
) {
    val span = axis.maximum - axis.minimum
    if (span <= 0.0) return

    val path = Path()
    var drawing = false

    samples.forEach { sample ->
        val raw = value(sample)
        if (raw == null) {
            drawing = false
            return@forEach
        }
        val x = plot.left + plot.width * sample.elapsedSeconds / duration
        val y = plot.bottom -
            plot.height * ((raw - axis.minimum) / span).toFloat().coerceIn(0f, 1f)
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

private const val CHART_HEIGHT = 200
private const val LABEL_GAP = 4
private const val MAX_TIME_TICKS = 5
private val TIME_STEPS_SECONDS = listOf(60, 120, 300, 600, 900, 1_200, 1_800, 3_600)
