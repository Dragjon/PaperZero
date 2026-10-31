package dev.olaf.paperzero.core.data

import org.junit.Assert.assertEquals
import org.junit.Test

class MistakesTest {

    private var clock = 0L
    private fun a(key: String, right: Boolean) = AnswerEntity(sessionId = 1, questionKey = key, chosen = "A", correct = right, at = ++clock)

    @Test
    fun aMistakeStaysUntilRightTwiceRunning() {
        val answers = listOf(
            a("p/1", false), a("p/1", true),
            a("p/2", false), a("p/2", true), a("p/2", true),
            a("p/3", true),
            a("p/4", false), a("p/4", true), a("p/4", false), a("p/4", true),
        )
        // Newest mistake first; p/2 is fixed, p/3 was never wrong.
        assertEquals(listOf("p/4", "p/1"), Progresses.mistakes(answers))
    }
}
