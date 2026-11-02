package dev.olaf.paperzero.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BadgesTest {
    private fun stats(longest: Int = 0, topics: List<TopicStats> = emptyList(), subjects: List<SubjectStats> = emptyList()) =
        Stats(0, emptyList(), Tally(), Tally(), 0, longest, 0, subjects, topics)

    private fun find(badges: List<Badge>, kind: BadgeKind, need: Int) = badges.first { it.kind == kind && it.need == need }

    @Test
    fun tiersUnlockAsTheNumberPasses() {
        val b = Badges.of(Rewards(bestCombo = 12), stats())
        assertTrue(find(b, BadgeKind.Combo, 5).unlocked)
        assertTrue(find(b, BadgeKind.Combo, 10).unlocked)
        assertFalse(find(b, BadgeKind.Combo, 20).unlocked)
        assertEquals(0.6f, find(b, BadgeKind.Combo, 20).progress, 0.001f)
    }

    @Test
    fun aTopicNeedsEnoughAnswersAndEnoughRight() {
        val topic = { n: Int, right: Int -> TopicStats(Subject.Biology, n, "t", Tally(n, right), 0, 0) }
        val b = Badges.of(Rewards(), stats(topics = listOf(topic(20, 16), topic(19, 19), topic(40, 20))))
        assertEquals(1, find(b, BadgeKind.Topics, 1).value)
    }

    @Test
    fun aScienceCountsTheShareTried() {
        val b = Badges.of(Rewards(), stats(subjects = listOf(SubjectStats(Subject.Physics, Tally(), 0, 30, 100))))
        assertTrue(find(b, BadgeKind.Physics, 25).unlocked)
        assertFalse(find(b, BadgeKind.Physics, 50).unlocked)
    }

    @Test
    fun aScienceNotInThePackIsNotOffered() {
        val b = Badges.of(Rewards(), stats(subjects = listOf(SubjectStats(Subject.Physics, Tally(), 0, 0, 100))))
        assertTrue(b.none { it.kind == BadgeKind.Biology })
        assertTrue(b.any { it.kind == BadgeKind.Answers })
    }

    @Test
    fun nextIsTheNearestStarted() {
        val b = Badges.of(Rewards(answered = 9, bestCombo = 1), stats())
        assertEquals(BadgeKind.Answers to 10, Badges.next(b)!!.let { it.kind to it.need })
    }

    @Test
    fun nothingStartedHasNoNext() {
        assertNull(Badges.next(Badges.of(Rewards(level = 0), stats())))
    }
}
