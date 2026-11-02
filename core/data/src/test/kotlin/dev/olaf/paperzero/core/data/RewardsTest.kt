package dev.olaf.paperzero.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class RewardsTest {
    private val today = LocalDate.of(2026, 11, 2)
    private val noon = today.atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
    private var clock = noon

    /** An answer at the next moment that isn't a lucky one, unless asked for. */
    private fun answer(key: String, right: Boolean, session: Long = 1, lucky: Boolean = false): AnswerEntity {
        do clock++ while (Rewards.lucky(key, clock) != lucky)
        return AnswerEntity(sessionId = session, questionKey = key, chosen = "A", correct = right, at = clock)
    }

    private fun of(vararg a: AnswerEntity) = Rewards.of(a.toList(), today, ZoneOffset.UTC)

    @Test
    fun nothingYet() {
        val r = of()
        assertEquals(0, r.xp)
        assertEquals(1, r.level)
        assertEquals(null, r.last)
    }

    @Test
    fun aRightAnswerPaysMoreThanAWrongOne() {
        assertEquals(12, of(answer("p/1", true)).xp)
        assertEquals(2, of(answer("p/1", false)).xp)
    }

    @Test
    fun aRunBuildsAndAWrongAnswerEndsIt() {
        val r = of(answer("p/1", true), answer("p/2", true), answer("p/3", true), answer("p/4", false))
        assertEquals(12 + 14 + 16 + 2, r.xp)
        assertEquals(0, r.combo)
        assertEquals(3, r.bestCombo)
    }

    @Test
    fun theRunsBonusHasACeiling() {
        val long = Array(10) { answer("p/$it", true) }
        assertEquals(2 + 10 + Rewards.COMBO_MAX, of(*long).last!!.xp)
    }

    @Test
    fun puttingAMistakeRightPaysOnce() {
        val r = of(answer("p/1", false, session = 1), answer("p/1", true, session = 2))
        assertTrue(r.last!!.putRight)
        assertEquals(2 + 12 + Rewards.PUT_RIGHT_XP, r.xp)
        val again = of(answer("p/2", false, session = 1), answer("p/2", true, session = 2), answer("p/2", true, session = 3))
        assertFalse(again.last!!.putRight)
    }

    @Test
    fun luckyDoubles() {
        val r = of(answer("p/1", true, lucky = true))
        assertTrue(r.last!!.lucky)
        assertEquals(24, r.xp)
    }

    @Test
    fun aWrongAnswerIsNeverLucky() {
        val r = of(answer("p/1", false, lucky = true))
        assertFalse(r.last!!.lucky)
        assertEquals(Rewards.TRY_XP, r.xp)
    }

    @Test
    fun anAnswerChangedInASittingCountsOnce() {
        val changed = of(answer("p/1", false, session = 7), answer("p/1", true, session = 7))
        assertEquals(12, changed.xp)
        assertEquals(1, changed.today)
    }

    @Test
    fun levelsTakeLonger() {
        // 60 tries at 2 XP each is 120; the first level takes 100.
        val tries = Array(60) { answer("p/$it", false) }
        val r = of(*tries)
        assertEquals(120, r.xp)
        assertEquals(2, r.level)
        assertEquals(20, r.intoLevel)
        assertEquals(150, r.levelSize)
    }

    @Test
    fun onlyTodayCountsTowardsTheGoal() {
        val yesterday = AnswerEntity(sessionId = 1, questionKey = "p/1", chosen = "A", correct = true, at = noon - 86_400_000)
        assertEquals(1, of(yesterday, answer("p/2", true)).today)
    }
}
