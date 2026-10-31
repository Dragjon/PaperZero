package dev.olaf.paperzero

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import dev.olaf.paperzero.core.data.Subject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.random.Random

/** A science meme for a feed: its picture, its title, and where it's from. */
class Meme(val title: String, val image: ImageBitmap, val source: String, val link: String)

/**
 * Science memes from the internet, only when they're turned on in the settings, from two open
 * places that need no key:
 *  - science subreddits, each science's own and science at large, through meme-api.com (which only
 *    has a subreddit's few dozen hot posts);
 *  - Lemmy's Science Memes community (on mander.xyz, read through lemmy.world), thousands of posts,
 *    a page of its all-time best at a time.
 * Posts marked NSFW or spoiler are never shown. Nothing is kept: posts are gathered as a feed needs
 * them, and pictures fetched a few at a time.
 */
object Memes {
    private const val REDDIT = "https://meme-api.com/gimme"
    private const val LEMMY = "https://lemmy.world/api/v3/post/list"
    private const val LEMMY_COMMUNITY = "science_memes@mander.xyz"
    /** Pages of it to choose from (50 posts a page; it has more, these are its best). */
    private const val LEMMY_PAGES = 90
    /** Pictures fetched at a time. */
    private const val AT_ONCE = 4
    /** Wider than a phone needs: bigger pictures are decoded smaller. */
    private const val MAX_WIDTH = 1080
    private const val MAX_BYTES = 6 shl 20
    private const val TIMEOUT_MS = 10_000
    private val PICTURE = listOf(".jpg", ".jpeg", ".png", ".webp")

    private val general = listOf("sciencememes", "ScienceHumor", "nerdymemes")
    private fun subreddits(subject: Subject?) = when (subject) {
        Subject.Biology -> listOf("biologymemes", "MicrobiologyMemes", "AnatomyMemes", "Biochemistrymemes", "BioMemes")
        Subject.Chemistry -> listOf("chemistrymemes", "Biochemistrymemes")
        Subject.Physics -> listOf("physicsmemes", "astronomymemes", "spacememes", "physicsjokes", "engineeringmemes")
        null -> listOf("geologymemes", "MicrobiologyMemes", "astronomymemes")
    } + general

    private class Post(val title: String, val url: String, val source: String, val link: String)

    /** Posts gathered and not yet shown, by subject; and the sources already drawn on. */
    private val pool = HashMap<Subject?, ArrayDeque<Post>>()
    private val used = HashMap<Subject?, MutableSet<String>>()
    private val lock = Mutex()

    /**
     * A few of [subject]'s memes (or science's at large), none in [seen]; empty only when no source
     * answers (offline). Draws on fresh sources as the ones it has run out.
     */
    suspend fun fetch(subject: Subject?, seen: Set<String>): List<Meme> = withContext(Dispatchers.IO) {
        val posts = lock.withLock {
            val queue = pool.getOrPut(subject) { ArrayDeque() }
            var tries = 0
            while (queue.count { it.link !in seen } < AT_ONCE && tries < 4) {
                tries++
                val more = gather(subject) ?: continue
                queue += more.filter { p -> p.link !in seen && queue.none { it.link == p.link } }.shuffled()
            }
            buildList { while (size < AT_ONCE && queue.isNotEmpty()) queue.removeFirst().takeIf { it.link !in seen }?.let { add(it) } }
        }
        coroutineScope {
            posts.map { p -> async { runCatching { picture(get(p.url))?.let { Meme(p.title, it, p.source, p.link) } }.getOrNull() } }
                .awaitAll()
                .filterNotNull()
        }
    }

    /** Posts from one source not yet drawn on: a subreddit, or a page of Lemmy's; null if it fails. */
    private fun gather(subject: Subject?): List<Post>? {
        val drawn = used.getOrPut(subject) { mutableSetOf() }
        val subs = subreddits(subject).filter { "r/$it" !in drawn }
        // A subreddit not yet drawn on, or a page of Lemmy's thousands, about half and half, so
        // one source never fills the feed.
        val page = (1..LEMMY_PAGES).map { "lemmy/$it" }.filter { it !in drawn }.randomOrNull()
        val source = (if (page != null && (subs.isEmpty() || Random.nextBoolean())) page else subs.randomOrNull()?.let { "r/$it" })
            ?: run { drawn.clear(); return emptyList() }
        drawn += source
        return runCatching {
            if (source.startsWith("r/")) reddit(String(get("$REDDIT/${source.removePrefix("r/")}/50")))
            else lemmy(String(get("$LEMMY?community_name=${URLEncoder.encode(LEMMY_COMMUNITY, "UTF-8")}&sort=TopAll&limit=50&page=${source.removePrefix("lemmy/")}")))
        }.getOrNull()
    }

    private fun isPicture(url: String) = url.lowercase().substringBefore('?').let { u -> PICTURE.any { u.endsWith(it) } }

    private fun reddit(json: String): List<Post> {
        val memes = JSONObject(json).optJSONArray("memes") ?: return emptyList()
        return (0 until memes.length()).map { memes.getJSONObject(it) }
            .filter { !it.optBoolean("nsfw", true) && !it.optBoolean("spoiler", true) }
            .map { Post(it.optString("title"), it.optString("url"), "r/${it.optString("subreddit")}", it.optString("postLink")) }
            .filter { it.link.isNotEmpty() && isPicture(it.url) }
    }

    private fun lemmy(json: String): List<Post> {
        val posts = JSONObject(json).optJSONArray("posts") ?: return emptyList()
        return (0 until posts.length()).map { posts.getJSONObject(it) }
            .filter { v ->
                val post = v.optJSONObject("post")
                post != null && !post.optBoolean("nsfw", true) && v.optJSONObject("community")?.optBoolean("nsfw", true) == false
            }
            .map { v ->
                val post = v.getJSONObject("post")
                Post(post.optString("name"), post.optString("url"), "Lemmy · Science Memes", post.optString("ap_id"))
            }
            .filter { it.link.isNotEmpty() && isPicture(it.url) }
    }

    private fun get(url: String): ByteArray {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = TIMEOUT_MS
        c.readTimeout = TIMEOUT_MS
        c.setRequestProperty("User-Agent", "PaperZero (Android)")
        try {
            check(c.responseCode == 200) { "HTTP ${c.responseCode}" }
            return c.inputStream.use { s ->
                val out = java.io.ByteArrayOutputStream()
                val buf = ByteArray(16 shl 10)
                while (true) {
                    val n = s.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    check(out.size() <= MAX_BYTES) { "too big" }
                }
                out.toByteArray()
            }
        } finally {
            c.disconnect()
        }
    }

    private fun picture(bytes: ByteArray): ImageBitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= MAX_WIDTH) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
    }
}
