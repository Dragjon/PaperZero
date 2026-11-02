package dev.olaf.paperzero.core.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** What the latest answer earned, and why. */
data class Gain(val xp: Int, val lucky: Boolean, val putRight: Boolean)

/**
 * What answering has earned: XP and a level, the run of right answers, and today's goal. All worked
 * out from the answers themselves, so an undo or a reset puts it right with nothing else to keep.
 * XP comes only from answering, and most of it from being right or from fixing a past mistake.
 */
data class Rewards(
    val xp: Int = 0,
    val level: Int = 1,
    /** XP into this level, and how much the level takes. */
    val intoLevel: Int = 0,
    val levelSize: Int = levelSize(1),
    /** Right answers running, up to now; the most ever. */
    val combo: Int = 0,
    val bestCombo: Int = 0,
    /** Answers today, towards [GOAL]. */
    val today: Int = 0,
    val last: Gain? = null,
    /** Records, for the stats and the badges. */
    val answered: Int = 0,
    val luckyDoubles: Int = 0,
    val putRights: Int = 0,
    /** Days the goal was reached, and the most answers in one day. */
    val goalDays: Int = 0,
    val bestDay: Int = 0,
) {
    companion object {
        const val GOAL = 20
        const val RIGHT_XP = 10
        /** For trying, right or wrong. */
        const val TRY_XP = 2
        /** Each right answer running adds this, up to [COMBO_MAX]. */
        const val COMBO_STEP = 2
        const val COMBO_MAX = 10
        /** For getting right a question last got wrong. */
        const val PUT_RIGHT_XP = 15
        /** About one right answer in this many pays double. */
        const val LUCKY_ONE_IN = 8

        /** The XP level [n] takes: each a little longer than the last. */
        fun levelSize(n: Int) = 100 + 50 * (n - 1)

        /** Fixed by the answer itself, so it comes out the same each time the XP is added up. */
        fun lucky(key: String, at: Long) = Math.floorMod(key.hashCode() * 31 + at.hashCode(), LUCKY_ONE_IN) == 0

        /** From every answer, oldest first. An answer changed within a sitting (in an exam) counts once. */
        fun of(answers: List<AnswerEntity>, today: LocalDate, zone: ZoneId): Rewards {
            val counted = LinkedHashMap<Pair<Long, String>, AnswerEntity>()
            for (a in answers) {
                val at = a.sessionId to a.questionKey
                counted.remove(at)
                counted[at] = a
            }
            var xp = 0
            var combo = 0
            var best = 0
            var todays = 0
            var last: Gain? = null
            var luckyDoubles = 0
            var putRights = 0
            val perDay = HashMap<LocalDate, Int>()
            val wrongLast = HashSet<String>()
            for (a in counted.values) {
                val day = Instant.ofEpochMilli(a.at).atZone(zone).toLocalDate()
                perDay[day] = (perDay[day] ?: 0) + 1
                if (day == today) todays++
                var gain = TRY_XP
                var lucky = false
                var putRight = false
                if (a.correct) {
                    gain += RIGHT_XP + minOf(combo * COMBO_STEP, COMBO_MAX)
                    combo++
                    best = maxOf(best, combo)
                    putRight = wrongLast.remove(a.questionKey)
                    if (putRight) {
                        gain += PUT_RIGHT_XP
                        putRights++
                    }
                    lucky = lucky(a.questionKey, a.at)
                    if (lucky) {
                        gain *= 2
                        luckyDoubles++
                    }
                } else {
                    combo = 0
                    wrongLast += a.questionKey
                }
                xp += gain
                last = Gain(gain, lucky, putRight)
            }
            var level = 1
            var into = xp
            while (into >= levelSize(level)) {
                into -= levelSize(level)
                level++
            }
            return Rewards(
                xp, level, into, levelSize(level), combo, best, todays, last,
                answered = counted.size,
                luckyDoubles = luckyDoubles,
                putRights = putRights,
                goalDays = perDay.values.count { it >= GOAL },
                bestDay = perDay.values.maxOrNull() ?: 0,
            )
        }
    }
}
