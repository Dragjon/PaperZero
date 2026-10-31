package dev.olaf.paperzero.core.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.ZoneId
import kotlin.random.Random

/** How a subject is going: questions tried at least once, and of those, right the last time. */
data class SubjectProgress(val subject: Subject, val total: Int, val tried: Int, val right: Int, val toFix: Int)

/** A paper's best finished sitting, as right out of total. */
data class Best(val score: Int, val total: Int)

data class Progress(
    val subjects: Map<Subject, SubjectProgress>,
    val best: Map<String, Best>,
    /** Questions to practise again, newest mistake first. */
    val mistakes: List<String>,
    /** Every question answered at least once. */
    val tried: Set<String>,
)

/**
 * What's been answered and what to do next, from the answers kept on the phone. Nothing leaves
 * it: there's no account and no network.
 */
class Progresses(private val pack: Pack, private val dao: PaperDao, private val now: () -> Long = System::currentTimeMillis) {

    val progress: Flow<Progress> = combine(dao.allAnswers(), dao.finished()) { answers, sessions ->
        val mistakes = mistakes(answers)
        val last = answers.associate { it.questionKey to it.correct }
        val subjects = Subject.entries.associateWith { subject ->
            val papers = pack.papers.filter { it.subject == subject }
            val ids = papers.map { it.id }.toSet()
            val mine = last.filterKeys { it.substringBeforeLast('/') in ids }
            SubjectProgress(
                subject = subject,
                total = papers.sumOf { it.count },
                tried = mine.size,
                right = mine.count { it.value },
                toFix = mistakes.count { it.substringBeforeLast('/') in ids },
            )
        }
        val best = sessions.filter { it.paperId != null && it.total > 0 }
            .groupBy { it.paperId!! }
            .mapValues { (_, s) -> s.maxBy { it.score }.let { Best(it.score, it.total) } }
        Progress(subjects, best, mistakes, last.keys)
    }

    /** A whole paper, in [mode]; an unfinished sitting of it in that mode is carried on instead. */
    suspend fun startPaper(paperId: String, mode: Mode): Long {
        dao.unfinished(paperId)?.let { if (it.mode == mode.name) return it.id }
        val paper = pack.paper(paperId) ?: error("No paper $paperId")
        val keys = pack.questions(paperId).map { it.key }
        return dao.insert(SessionEntity(mode = mode.name, paperId = paperId, title = title(paper), keys = keys.joinToString(","), startedAt = now()))
    }

    /** Whether a timed set called [title] was started and not finished. */
    suspend fun hasUnfinishedTimed(title: String): Boolean = dao.unfinishedTimed(title) != null

    /**
     * A timed set of [subject]: [TIMED_QUESTIONS] questions from across its papers, sat like a
     * paper in Exam mode; an unfinished one ([title]) is carried on instead.
     */
    suspend fun startTimed(subject: Subject, title: String, random: Random = Random.Default): Long {
        dao.unfinishedTimed(title)?.let { return it.id }
        val keys = pack.questions(subject).shuffled(random).take(TIMED_QUESTIONS).map { it.key }
        return dao.insert(SessionEntity(mode = Mode.Exam.name, paperId = null, title = title, keys = keys.joinToString(","), startedAt = now()))
    }

    /**
     * A feed of questions to scroll through: answers given on the way are kept like any others.
     * The questions themselves are picked as it goes ([feed]).
     */
    suspend fun startScroll(title: String): Long =
        dao.insert(SessionEntity(mode = Mode.Practice.name, paperId = null, title = title, keys = "", startedAt = now()))

    /** [subject]'s questions (on [topic], if one) in a fresh order: ones not yet tried first. */
    fun feed(subject: Subject, tried: Set<String>, topic: Int? = null, random: Random = Random.Default): List<Question> {
        val all = pack.questions(subject).filter { topic == null || topic in it.topics }
        return all.filter { it.key !in tried }.shuffled(random) + all.filter { it.key in tried }.shuffled(random)
    }

    /** The mistakes to fix (of [subject], or all), up to [MISTAKES_AT_ONCE] at a time. */
    suspend fun startMistakes(keys: List<String>, title: String): Long =
        dao.insert(SessionEntity(mode = Mode.Practice.name, paperId = null, title = title, keys = keys.take(MISTAKES_AT_ONCE).joinToString(","), startedAt = now()))

    suspend fun session(id: Long): SessionEntity? = dao.session(id)

    suspend fun answers(sessionId: Long): Map<String, AnswerEntity> = dao.answers(sessionId).associateBy { it.questionKey }

    /** Keeps [chosen] as the answer to [question] in [sessionId]; true if it's right. */
    suspend fun answer(sessionId: Long, question: Question, chosen: Char): Boolean {
        val right = chosen == question.answer
        dao.insert(AnswerEntity(sessionId = sessionId, questionKey = question.key, chosen = chosen.toString(), correct = right, at = now()))
        return right
    }

    suspend fun addTime(sessionId: Long, ms: Long) = dao.addTime(sessionId, ms)

    /** Counts [ms] more spent on [subject]'s questions today. */
    suspend fun study(subject: Subject, ms: Long) =
        dao.study(Instant.ofEpochMilli(now()).atZone(ZoneId.systemDefault()).toLocalDate().toString(), subject.name, ms)

    /** Time spent, accuracy by science and topic, streaks: see [Stats]. */
    val stats: Flow<Stats> = combine(dao.allAnswers(), dao.study()) { answers, study ->
        val zone = ZoneId.systemDefault()
        Stats.of(pack, answers, study, Instant.ofEpochMilli(now()).atZone(zone).toLocalDate(), zone)
    }

    /** Marks the sitting: right answers out of its questions. */
    suspend fun finish(sessionId: Long): Best {
        val session = dao.session(sessionId) ?: return Best(0, 0)
        val keys = session.keys.split(',').filter { it.isNotEmpty() }
        val given = answers(sessionId)
        val score = keys.count { given[it]?.correct == true }
        dao.finish(sessionId, now(), score, keys.size)
        return Best(score, keys.size)
    }

    /** Liked questions' keys, the newest like first. */
    val liked: Flow<List<String>> = dao.likes().map { list -> list.map { it.questionKey } }

    /** Comments, by question, oldest first; and the questions commented on, the latest first. */
    val comments: Flow<Map<String, List<CommentEntity>>> = dao.comments().map { list -> list.groupBy { it.questionKey } }

    val commented: Flow<List<String>> = dao.comments().map { list -> list.sortedByDescending { it.at }.map { it.questionKey }.distinct() }

    suspend fun setLiked(key: String, liked: Boolean) = if (liked) dao.like(LikeEntity(key, now())) else dao.unlike(key)

    suspend fun comment(key: String, text: String) {
        if (text.isNotBlank()) dao.insert(CommentEntity(questionKey = key, text = text.trim(), at = now()))
    }

    suspend fun deleteComment(id: Long) = dao.deleteComment(id)

    /** Forgets every answer, sitting and second studied; likes and comments stay. */
    suspend fun resetProgress() = dao.resetProgress()

    /** Forgets every like and comment. */
    suspend fun deleteSaved() = dao.deleteSaved()

    /** Throws away a sitting that was started but not wanted. */
    suspend fun discard(sessionId: Long) {
        dao.deleteAnswers(sessionId)
        dao.deleteSession(sessionId)
    }

    fun title(paper: PaperInfo) = listOfNotNull(paper.source, paper.year?.toString()).joinToString(" ")

    companion object {
        const val MISTAKES_AT_ONCE = 20
        /** A timed set is as long as a Paper 1. */
        const val TIMED_QUESTIONS = 40
        /** Right this many times running after a mistake, a question is fixed. */
        const val FIXED_AFTER = 2

        /**
         * Questions still to fix, newest mistake first: answered wrong, and not right
         * [FIXED_AFTER] times running since. [answers] oldest first.
         */
        fun mistakes(answers: List<AnswerEntity>): List<String> {
            val streak = HashMap<String, Int>()
            val wrongAt = HashMap<String, Long>()
            for (a in answers) {
                if (a.correct) {
                    if (a.questionKey in wrongAt) streak[a.questionKey] = (streak[a.questionKey] ?: 0) + 1
                } else {
                    wrongAt[a.questionKey] = a.at
                    streak[a.questionKey] = 0
                }
            }
            return wrongAt.filterKeys { (streak[it] ?: 0) < FIXED_AFTER }.entries.sortedByDescending { it.value }.map { it.key }
        }
    }
}
