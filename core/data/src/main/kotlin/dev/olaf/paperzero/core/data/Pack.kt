package dev.olaf.paperzero.core.data

import android.content.res.AssetManager
import org.json.JSONObject

enum class Subject { Biology, Chemistry, Physics }

/** One paper of the pack: an O-Level year or a school's prelim, its Paper 1 (multiple choice). */
data class PaperInfo(
    val id: String,
    val subject: Subject,
    /** "O-Level", or the school. */
    val source: String,
    val year: Int?,
    val count: Int,
) {
    val isOLevel: Boolean get() = source == "O-Level"
}

/** A question: its picture as printed, the right letter, and a worked explanation when there's one. */
data class Question(
    val paperId: String,
    val number: Int,
    val answer: Char,
    /** Asset path of the question's picture. */
    val image: String,
    /** Asset path of a picture of its worked answer, if the paper's key has them. */
    val explain: String?,
    /**
     * The topics it covers, best first, as places in its science's [Pack.topics]: found by the
     * words it shares with each chapter's notes, so now and then a neighbouring chapter's.
     */
    val topics: List<Int> = emptyList(),
) {
    val key: String get() = "$paperId/$number"
}

/**
 * The questions bundled with the app (assets/pack): an index of papers, each paper's questions
 * and answers, and their pictures. The pack is private (the papers belong to their examiners and
 * schools) and is built by tools/extract; a build without it has no papers.
 */
class Pack(private val assets: AssetManager) {

    val papers: List<PaperInfo> by lazy {
        val json = runCatching { assets.open("$ROOT/index.json").bufferedReader().use { it.readText() } }.getOrNull()
            ?: return@lazy emptyList()
        val list = JSONObject(json).getJSONArray("papers")
        (0 until list.length()).map { i ->
            val p = list.getJSONObject(i)
            PaperInfo(
                id = p.getString("id"),
                subject = Subject.valueOf(p.getString("subject")),
                source = p.getString("source"),
                year = if (p.isNull("year")) null else p.getInt("year"),
                count = p.getInt("count"),
            )
        }
    }

    /** Each science's topics, the chapters of its notes in their order. */
    val topics: Map<Subject, List<String>> by lazy {
        val json = runCatching { assets.open("$ROOT/index.json").bufferedReader().use { it.readText() } }.getOrNull()
            ?: return@lazy emptyMap()
        val all = JSONObject(json).optJSONObject("topics") ?: return@lazy emptyMap()
        Subject.entries.associateWith { s ->
            all.optJSONArray(s.name)?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()
        }
    }

    /** [question]'s topics by name. */
    fun topicNames(question: Question): List<String> {
        val names = paper(question.paperId)?.let { topics[it.subject] }.orEmpty()
        return question.topics.mapNotNull { names.getOrNull(it) }
    }

    private val cache = HashMap<String, List<Question>>()

    fun paper(id: String): PaperInfo? = papers.firstOrNull { it.id == id }

    @Synchronized
    fun questions(paperId: String): List<Question> = cache.getOrPut(paperId) {
        val json = assets.open("$ROOT/$paperId/paper.json").bufferedReader().use { it.readText() }
        val list = JSONObject(json).getJSONArray("questions")
        (0 until list.length()).map { i ->
            val q = list.getJSONObject(i)
            Question(
                paperId = paperId,
                number = q.getInt("n"),
                answer = q.getString("answer")[0],
                image = "$ROOT/$paperId/${q.getString("image")}",
                explain = q.optString("explain").takeIf { it.isNotEmpty() }?.let { "$ROOT/$paperId/$it" },
                topics = q.optJSONArray("topics")?.let { a -> (0 until a.length()).map { a.getInt(it) } }.orEmpty(),
            )
        }
    }

    /** The question "paper/number", if the pack has it. */
    fun question(key: String): Question? {
        val paperId = key.substringBeforeLast('/')
        val n = key.substringAfterLast('/').toIntOrNull() ?: return null
        if (paper(paperId) == null) return null
        return questions(paperId).firstOrNull { it.number == n }
    }

    fun questions(subject: Subject): List<Question> = papers.filter { it.subject == subject }.flatMap { questions(it.id) }

    fun subject(question: Question): Subject? = paper(question.paperId)?.subject

    private companion object {
        const val ROOT = "pack"
    }
}
