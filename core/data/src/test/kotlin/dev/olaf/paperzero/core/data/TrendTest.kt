package dev.olaf.paperzero.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrendTest {

    private fun answers(s: String) = s.map { it == '1' }

    @Test
    fun rollingIsTheShareRightOfTheLastFew() {
        assertEquals(listOf(0.5f, 1f, 1f), Trend.rolling(answers("0111"), 2))
        assertEquals(listOf(1f / 3, 2f / 3, 2f / 3), Trend.rolling(answers("00110"), 3))
    }

    @Test
    fun tooFewAnswersHaveNoRollingLine() {
        assertEquals(emptyList<Float>(), Trend.rolling(answers("01"), 3))
    }

    @Test
    fun gettingBetterIsUp() {
        // Mostly wrong at first, mostly right later.
        val results = answers("0010000100" + "0100100010" + "1011010110" + "1110111011" + "1111101111")
        val t = Trend(results, 10)
        assertEquals(Trend.Direction.Up, t.direction)
        assertTrue(t.pointsPer100 > 0)
        assertEquals(20, t.first)
        assertEquals(90, t.last)
    }

    @Test
    fun gettingWorseIsDown() {
        val results = answers("1111101111" + "1110111011" + "1011010110" + "0100100010" + "0010000100")
        assertEquals(Trend.Direction.Down, Trend(results, 10).direction)
    }

    @Test
    fun steadyIsUnclear() {
        val results = answers("10".repeat(30))
        assertEquals(Trend.Direction.Unclear, Trend(results, 10).direction)
    }

    @Test
    fun noTrendBeforeEnoughAnswers() {
        val t = Trend(answers("0000011111"), 10)
        assertEquals(false, t.enough)
        assertEquals(Trend.Direction.Unclear, t.direction)
    }
}
