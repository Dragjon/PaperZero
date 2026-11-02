package dev.olaf.paperzero.core.data

enum class BadgeKind { Answers, Combo, Streak, Goals, PutRight, Lucky, Level, BestDay, Topics, Biology, Chemistry, Physics }

/** A badge: what it's for, the number that earns it, and how far along that is. */
data class Badge(val kind: BadgeKind, val need: Int, val value: Int) {
    val unlocked: Boolean get() = value >= need
    val progress: Float get() = (value.toFloat() / need).coerceAtMost(1f)
}

/**
 * The badges, each kind in rising tiers. All of them come from answering and from getting better,
 * none from the hour of the day or from time merely spent, and an undo or a reset takes them back.
 */
object Badges {
    /** A topic is mastered with this many answers, this share of them right. */
    const val MASTERED_ANSWERS = 20
    const val MASTERED_PERCENT = 80

    private val SCIENCE = listOf(25, 50, 100)
    private val SCIENCES = mapOf(BadgeKind.Biology to Subject.Biology, BadgeKind.Chemistry to Subject.Chemistry, BadgeKind.Physics to Subject.Physics)

    private val TIERS = mapOf(
        BadgeKind.Answers to listOf(10, 100, 500, 1000, 5000),
        BadgeKind.Combo to listOf(5, 10, 20, 40),
        BadgeKind.Streak to listOf(3, 7, 30, 100),
        BadgeKind.Goals to listOf(1, 7, 30),
        BadgeKind.PutRight to listOf(5, 25, 100),
        BadgeKind.Lucky to listOf(5, 25, 100),
        BadgeKind.Level to listOf(5, 10, 25),
        BadgeKind.BestDay to listOf(50, 100),
        BadgeKind.Topics to listOf(1, 5, 15),
        BadgeKind.Biology to SCIENCE,
        BadgeKind.Chemistry to SCIENCE,
        BadgeKind.Physics to SCIENCE,
    )

    fun of(r: Rewards, s: Stats): List<Badge> {
        val mastered = s.topics.count { it.tally.answered >= MASTERED_ANSWERS && (it.tally.percent ?: 0) >= MASTERED_PERCENT }
        fun tried(subject: Subject?) = s.subjects.firstOrNull { it.subject == subject }?.let { it.tried * 100 / it.questions.coerceAtLeast(1) } ?: 0
        fun value(kind: BadgeKind) = when (kind) {
            BadgeKind.Answers -> r.answered
            BadgeKind.Combo -> r.bestCombo
            BadgeKind.Streak -> s.longestStreak
            BadgeKind.Goals -> r.goalDays
            BadgeKind.PutRight -> r.putRights
            BadgeKind.Lucky -> r.luckyDoubles
            BadgeKind.Level -> r.level
            BadgeKind.BestDay -> r.bestDay
            BadgeKind.Topics -> mastered
            BadgeKind.Biology, BadgeKind.Chemistry, BadgeKind.Physics -> tried(SCIENCES[kind])
        }
        // A science the pack doesn't have can't be earned, so it isn't offered.
        val present = s.subjects.map { it.subject }.toSet()
        return BadgeKind.entries
            .filter { kind -> SCIENCES[kind].let { it == null || it in present } }
            .flatMap { kind -> TIERS.getValue(kind).map { need -> Badge(kind, need, value(kind)) } }
    }

    /** The one nearest to being earned, once something has been done towards it. */
    fun next(badges: List<Badge>): Badge? = badges.filter { !it.unlocked && it.value > 0 }.maxByOrNull { it.progress }
}
