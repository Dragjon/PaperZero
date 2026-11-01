package dev.olaf.paperzero.ui

import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import dev.olaf.paperzero.R
import dev.olaf.paperzero.core.data.Stats
import dev.olaf.paperzero.core.data.Subject
import dev.olaf.paperzero.core.data.Trend
import dev.olaf.paperzero.core.design.skinCard
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Is it getting better with more questions? Over every answer, oldest to newest: the share right
 * of the last few (a rolling line) and a straight trend through them all, with a plain verdict on
 * whether the trend is clear of chance. One science or all of them; a window of 10, 20 or 50. Touch
 * the chart to read any point.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccuracyTrend(s: Stats) {
    val answered = s.subjects.filter { ss -> s.history.any { it.first == ss.subject } }.map { it.subject }
    var subject by rememberSaveable { mutableStateOf<Subject?>(null) }
    var window by rememberSaveable { mutableStateOf(20) }
    val results = remember(s.history, subject) { s.history.filter { subject == null || it.first == subject }.map { it.second } }
    val trend = remember(results, window) { Trend(results, window) }

    SubjectTheme(subject, fill = false) {
        val colors = MaterialTheme.colorScheme
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = colors.surfaceContainer,
            modifier = Modifier.fillMaxWidth().skinCard(MaterialTheme.shapes.extraLarge),
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(stringResource(R.string.trend_title), style = MaterialTheme.typography.titleMedium)
                Text(verdict(trend), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                // Which answers: all of them, or one science's.
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(stringResource(R.string.trend_all), subject == null, subtle = true) { subject = null }
                    answered.forEach { sub -> Chip(subjectName(sub), subject == sub, subtle = true) { subject = sub } }
                }
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.trend_window), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant, modifier = Modifier.align(Alignment.CenterVertically))
                    WINDOWS.forEach { w -> Chip(w.toString(), window == w, subtle = true) { window = w } }
                }
                Spacer(Modifier.height(16.dp))
                if (!trend.enough) {
                    Text(
                        stringResource(R.string.trend_need, window + Trend.MIN_PAST_WINDOW - results.size),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                } else {
                    Chart(trend)
                    Spacer(Modifier.height(8.dp))
                    Legend(window)
                }
            }
        }
    }
}

private val WINDOWS = listOf(10, 20, 50)

@Composable
private fun verdict(t: Trend): String {
    if (!t.enough) return stringResource(R.string.trend_intro)
    val points = abs(t.pointsPer100).roundToInt()
    val change = stringResource(R.string.trend_first_last, t.window, t.first ?: 0, t.last ?: 0)
    return when (t.direction) {
        Trend.Direction.Up -> stringResource(R.string.trend_up, points) + " " + change
        Trend.Direction.Down -> stringResource(R.string.trend_down, points) + " " + change
        Trend.Direction.Unclear -> stringResource(R.string.trend_unclear) + " " + change
    }
}

@Composable
private fun Chart(t: Trend) {
    val colors = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    val measurer = rememberTextMeasurer()
    val n = t.results.size
    // The answer being read, while a finger is on the chart.
    var reading by remember(t) { mutableStateOf<Int?>(null) }
    val described = stringResource(R.string.trend_chart_description, n, t.first ?: 0, t.last ?: 0)
    val tickStyle = type.labelSmall.copy(color = colors.onSurfaceVariant)
    val readStyle = type.labelMedium.copy(color = colors.onSurface)
    // Room at the left for the percentages, as wide as "100%" is at the text size in use.
    val density = LocalDensity.current
    val axis = remember(tickStyle, density) { measurer.measure("100%", tickStyle).size.width + with(density) { 6.dp.toPx() } }
    // Room at the top for a reading, one line of it.
    val readRoom = remember(readStyle, density) { measurer.measure("0", readStyle).size.height + with(density) { 6.dp.toPx() } }
    val readText = reading?.let { i ->
        stringResource(R.string.trend_reading, i + 1, (t.rolling[i - (t.window - 1)] * 100).roundToInt(), t.window)
    }

    Box(Modifier.fillMaxWidth()) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(200.dp)
                .semantics { contentDescription = described }
                .pointerInput(t, axis) {
                    val left = axis
                    fun at(x: Float) = (((x - left) / (size.width - left)) * (n - 1)).roundToInt().coerceIn(t.window - 1, n - 1)
                    detectTapGestures(onPress = { reading = at(it.x); tryAwaitRelease(); reading = null })
                }
                .pointerInput(t, axis) {
                    val left = axis
                    fun at(x: Float) = (((x - left) / (size.width - left)) * (n - 1)).roundToInt().coerceIn(t.window - 1, n - 1)
                    detectHorizontalDragGestures(
                        onDragStart = { reading = at(it.x) },
                        onDragEnd = { reading = null },
                        onDragCancel = { reading = null },
                    ) { change, _ -> reading = at(change.position.x) }
                },
        ) {
            val left = axis
            val top = readRoom
            val bottom = size.height - 18.dp.toPx()
            val w = size.width - left
            fun x(i: Int) = left + w * i / (n - 1).coerceAtLeast(1)
            fun y(v: Double) = bottom - (bottom - top) * v.toFloat()

            // The grid: quarters, faint, with the halves labelled.
            listOf(0.0, 0.25, 0.5, 0.75, 1.0).forEach { v ->
                drawLine(colors.outlineVariant, Offset(left, y(v)), Offset(size.width, y(v)), 1.dp.toPx())
                if (v == 0.0 || v == 0.5 || v == 1.0) {
                    val label = measurer.measure("${(v * 100).roundToInt()}%", tickStyle)
                    drawText(label, topLeft = Offset(left - label.size.width - 6.dp.toPx(), y(v) - label.size.height / 2))
                }
            }
            // The answers' ends, under the chart.
            measurer.measure("1", tickStyle).let { drawText(it, topLeft = Offset(left, bottom + 4.dp.toPx())) }
            measurer.measure(n.toString(), tickStyle).let { drawText(it, topLeft = Offset(size.width - it.size.width, bottom + 4.dp.toPx())) }

            // The trend: a straight dashed line through every answer.
            drawLine(
                colors.onSurfaceVariant,
                Offset(x(0), y(t.fitted(0))),
                Offset(x(n - 1), y(t.fitted(n - 1))),
                1.5.dp.toPx(),
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx())),
            )
            // The rolling share right: starts once there's a full window of answers.
            val path = Path()
            t.rolling.forEachIndexed { k, v ->
                val px = x(k + t.window - 1)
                val py = y(v.toDouble())
                if (k == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            drawPath(path, colors.primary, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            // The last point, marked, with a ring of the card's colour round it.
            val end = Offset(x(n - 1), y(t.rolling.last().toDouble()))
            drawCircle(colors.surfaceContainer, 6.dp.toPx(), end)
            drawCircle(colors.primary, 4.dp.toPx(), end)

            reading?.let { i ->
                val v = t.rolling[i - (t.window - 1)].toDouble()
                val p = Offset(x(i), y(v))
                drawLine(colors.onSurfaceVariant.copy(alpha = 0.5f), Offset(p.x, top), Offset(p.x, bottom), 1.dp.toPx())
                drawCircle(colors.surfaceContainer, 7.dp.toPx(), p)
                drawCircle(colors.primary, 5.dp.toPx(), p)
                readText?.let { text ->
                    val label = measurer.measure(
                        text,
                        readStyle,
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1,
                        constraints = Constraints(maxWidth = size.width.toInt().coerceAtLeast(0)),
                    )
                    val lx = (p.x - label.size.width / 2).coerceAtMost(size.width - label.size.width).coerceAtLeast(0f)
                    drawText(label, topLeft = Offset(lx, 0f))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Legend(window: Int) {
    val colors = MaterialTheme.colorScheme
    FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Key(colors.primary, dashed = false, stringResource(R.string.trend_key_rolling, window))
        Key(colors.onSurfaceVariant, dashed = true, stringResource(R.string.trend_key_trend))
    }
}

@Composable
private fun Key(color: Color, dashed: Boolean, label: String) {
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(width = 20.dp, height = 8.dp)) {
            drawLine(
                color,
                Offset(0f, size.height / 2),
                Offset(size.width, size.height / 2),
                2.dp.toPx(),
                cap = StrokeCap.Round,
                pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())) else null,
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
    }
}
