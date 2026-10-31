package dev.olaf.paperzero.core.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class StreakTest {

    private val today = LocalDate.of(2026, 10, 31)
    private fun ago(vararg days: Long) = days.map { today.minusDays(it) }.toSet()

    @Test
    fun todayAndTheDaysBeforeItCount() {
        assertEquals(3 to 3, Stats.streaks(ago(0, 1, 2), today))
    }

    @Test
    fun aStreakLastsUntilTheEndOfTodayEvenBeforeAnythingIsDoneToday() {
        assertEquals(2 to 2, Stats.streaks(ago(1, 2), today))
    }

    @Test
    fun aMissedDayEndsIt() {
        assertEquals(1 to 4, Stats.streaks(ago(0, 2, 10, 11, 12, 13), today))
    }

    @Test
    fun nothingDone() {
        assertEquals(0 to 0, Stats.streaks(emptySet(), today))
    }
}
