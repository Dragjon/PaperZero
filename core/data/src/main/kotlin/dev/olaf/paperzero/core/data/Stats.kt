package dev.olaf.paperzero.core.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Answers given and how many were right. */
data class Tally(val answered: Int = 0, val right: Int = 0) {
    /** Right out of answered, 0 to 100; null before any answer. */
    val percent: Int? get() = if (answered == 0) null else right * 100 / answered

    operator fun plus(correct: Boolean) = Tally(answered + 1, right + if (correct) 1 else 0)
}

/** How a topic is going: its answers, and how many of its questions have been tried of how many. */
data class TopicStats(val subject: Subject, val topic: Int, val name: String, val tally: Tally, val tried: Int, val questions: Int)

data class SubjectStats(val subject: Subject, val tally: Tally, val studiedMs: Long, val tried: Int, val questions: Int)

data class Stats(
    val studiedMs: Long,
    /** Time spent each of the last [Stats.DAYS] days, oldest first; the last is today. */
    val days: List<Pair<LocalDate, Long>>,
    val tally: Tally,
    /** Answers in the last seven days. */
    val week: Tally,
    /** Days running, up to today (or yesterday, if today hasn't started), with something done. */
    val streak: Int,
    val longestStreak: Int,
    /** Questions answered wrong once and since put right ([Progresses.FIXED_AFTER] times running). */
    val fixed: Int,
    val subjects: List<SubjectStats>,
    val topics: List<TopicStats>,
) {
    companion object {
        const val DAYS = 7
        /** A topic needs this many answers before it's called strong or weak. */
        const val ENOUGH = 5

        /**
         * Days running with something done, up to [today] (or yesterday, if nothing yet today), and
         * the most ever.
         */
        fun streaks(active: Set<LocalDate>, today: LocalDate): Pair<Int, Int> {
            var streak = 0
            var day = if (today in active) today else today.minusDays(1)
            while (day in active) {
                streak++
                day = day.minusDays(1)
            }
            var longest = 0
            var run = 0
            var last: LocalDate? = null
            for (d in active.sorted()) {
                run = if (last != null && d == last.plusDays(1)) run + 1 else 1
                longest = maxOf(longest, run)
                last = d
            }
            return streak to longest
        }

        /**
         * Stats from every answer (oldest first) and every day's time spent, as of [today]; [zone]
         * is the phone's, which days are counted in.
         */
        fun of(pack: Pack, answers: List<AnswerEntity>, study: List<StudyEntity>, today: LocalDate, zone: ZoneId): Stats {
            val subjectOf = pack.papers.associate { it.id to it.subject }
            fun subject(key: String) = subjectOf[key.substringBeforeLast('/')]

            val bySubject = HashMap<Subject, Tally>()
            val byTopic = HashMap<Pair<Subject, Int>, Tally>()
            val triedTopic = HashMap<Pair<Subject, Int>, MutableSet<String>>()
            var tally = Tally()
            var week = Tally()
            val weekStart = today.minusDays(6)
            val active = HashSet<LocalDate>()
            for (a in answers) {
                val s = subject(a.questionKey) ?: continue
                tally += a.correct
                bySubject[s] = (bySubject[s] ?: Tally()) + a.correct
                val day = Instant.ofEpochMilli(a.at).atZone(zone).toLocalDate()
                active += day
                if (!day.isBefore(weekStart)) week += a.correct
                pack.question(a.questionKey)?.topics?.forEach { t ->
                    byTopic[s to t] = (byTopic[s to t] ?: Tally()) + a.correct
                    triedTopic.getOrPut(s to t) { HashSet() } += a.questionKey
                }
            }
            val spent = HashMap<LocalDate, Long>()
            val spentBySubject = HashMap<Subject, Long>()
            for (d in study) {
                val day = runCatching { LocalDate.parse(d.day) }.getOrNull() ?: continue
                spent[day] = (spent[day] ?: 0) + d.ms
                runCatching { Subject.valueOf(d.subject) }.getOrNull()?.let { spentBySubject[it] = (spentBySubject[it] ?: 0) + d.ms }
                if (d.ms >= 60_000) active += day
            }

            val tried = answers.map { it.questionKey }.toSet()
            val subjects = Subject.entries.filter { s -> pack.papers.any { it.subject == s } }.map { s ->
                val all = pack.questions(s)
                SubjectStats(s, bySubject[s] ?: Tally(), spentBySubject[s] ?: 0, all.count { it.key in tried }, all.size)
            }
            val topics = subjects.flatMap { ss ->
                val names = pack.topics[ss.subject].orEmpty()
                val counts = IntArray(names.size)
                pack.questions(ss.subject).forEach { q -> q.topics.forEach { if (it in counts.indices) counts[it]++ } }
                names.mapIndexed { i, name ->
                    TopicStats(ss.subject, i, name, byTopic[ss.subject to i] ?: Tally(), triedTopic[ss.subject to i]?.size ?: 0, counts[i])
                }
            }

            val (streak, longest) = streaks(active, today)
            val fixedNow = answers.filter { !it.correct }.map { it.questionKey }.toSet() - Progresses.mistakes(answers).toSet()
            return Stats(
                studiedMs = spent.values.sum(),
                days = (DAYS - 1 downTo 0).map { today.minusDays(it.toLong()) }.map { it to (spent[it] ?: 0) },
                tally = tally,
                week = week,
                streak = streak,
                longestStreak = longest,
                fixed = fixedNow.size,
                subjects = subjects,
                topics = topics,
            )
        }
    }
}
