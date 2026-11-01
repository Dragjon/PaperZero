package dev.olaf.paperzero.core.data

import kotlin.math.sqrt

/**
 * Whether answers are getting more right as they go: the share right over a sliding window of the
 * last few, and a straight line fitted through every answer (right as 1, wrong as 0), with how sure
 * that line's slope is.
 */
class Trend(
    /** Right or wrong, oldest first. */
    val results: List<Boolean>,
    val window: Int,
) {
    /** For each answer from the [window]th on: the share of the last [window] that were right, 0 to 1. */
    val rolling: List<Float> = rolling(results, window)

    /** The fitted line: share right at the first answer, and its change per answer. */
    val start: Double
    val perAnswer: Double

    /** The slope's standard error, per answer; NaN with too few answers to say. */
    private val error: Double

    init {
        val n = results.size
        if (n < 3) {
            start = if (n == 0) 0.0 else results.count { it }.toDouble() / n
            perAnswer = 0.0
            error = Double.NaN
        } else {
            val meanX = (n - 1) / 2.0
            val meanY = results.count { it }.toDouble() / n
            var sxx = 0.0
            var sxy = 0.0
            results.forEachIndexed { i, r ->
                val dx = i - meanX
                sxx += dx * dx
                sxy += dx * ((if (r) 1.0 else 0.0) - meanY)
            }
            perAnswer = sxy / sxx
            start = meanY - perAnswer * meanX
            var residual = 0.0
            results.forEachIndexed { i, r ->
                val e = (if (r) 1.0 else 0.0) - (start + perAnswer * i)
                residual += e * e
            }
            error = sqrt(residual / (n - 2) / sxx)
        }
    }

    /** The line's height at answer [i] (from 0), kept to 0 to 1. */
    fun fitted(i: Int): Double = (start + perAnswer * i).coerceIn(0.0, 1.0)

    /** Percentage points gained (or lost) per hundred answers, by the fitted line. */
    val pointsPer100: Double get() = perAnswer * 100 * 100

    /** Enough answers for a trend to mean anything. */
    val enough: Boolean get() = results.size >= window + MIN_PAST_WINDOW

    /** Which way it's going, if the line's slope is clear of chance (about 95% sure). */
    val direction: Direction
        get() = when {
            !enough || error.isNaN() || error == 0.0 -> Direction.Unclear
            perAnswer > 2 * error -> Direction.Up
            perAnswer < -2 * error -> Direction.Down
            else -> Direction.Unclear
        }

    /** Share right in the first and the last [window] answers, 0 to 100. */
    val first: Int? get() = if (results.size < window) null else results.take(window).count { it } * 100 / window
    val last: Int? get() = if (results.size < window) null else results.takeLast(window).count { it } * 100 / window

    enum class Direction { Up, Down, Unclear }

    companion object {
        /** Answers past the first window before a trend is drawn. */
        const val MIN_PAST_WINDOW = 5

        fun rolling(results: List<Boolean>, window: Int): List<Float> {
            if (window <= 0 || results.size < window) return emptyList()
            val out = ArrayList<Float>(results.size - window + 1)
            var right = results.take(window).count { it }
            out += right.toFloat() / window
            for (i in window until results.size) {
                if (results[i]) right++
                if (results[i - window]) right--
                out += right.toFloat() / window
            }
            return out
        }
    }
}
