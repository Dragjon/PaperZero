package dev.olaf.paperzero

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import dev.olaf.paperzero.core.data.Subject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** A science meme for a feed: its picture, its title, and where it's from. */
class Meme(val title: String, val image: ImageBitmap, val source: String, val link: String)

/**
 * Science memes from the internet, only when they're turned on in the settings: the posts of a few
 * science subreddits, through meme-api.com (an open service that needs no key). Posts marked NSFW
 * or spoiler are never shown. Nothing is kept: they're fetched as a feed needs them.
 */
object Memes {
    private const val API = "https://meme-api.com/gimme"
    /** The most fetched at once (the service's own limit is 50). */
    private const val BATCH = 12
    /** Wider than a phone needs: bigger pictures are decoded smaller. */
    private const val MAX_WIDTH = 1080
    private const val MAX_BYTES = 6 shl 20
    private const val TIMEOUT_MS = 10_000

    private fun subreddits(subject: Subject?) = when (subject) {
        Subject.Biology -> listOf("biologymemes", "sciencememes")
        Subject.Chemistry -> listOf("chemistrymemes", "sciencememes")
        Subject.Physics -> listOf("physicsmemes", "sciencememes")
        null -> listOf("sciencememes")
    }

    /** Some of [subject]'s memes (or science's at large), not ones in [seen]; empty when offline. */
    suspend fun fetch(subject: Subject?, seen: Set<String>): List<Meme> = withContext(Dispatchers.IO) {
        val posts = subreddits(subject).flatMap { sub ->
            runCatching { posts(String(get("$API/$sub/$BATCH"))) }.getOrDefault(emptyList())
        }.filter { it.link !in seen }.distinctBy { it.link }.shuffled()
        coroutineScope {
            posts.map { p -> async { runCatching { picture(get(p.url))?.let { Meme(p.title, it, "r/${p.subreddit}", p.link) } }.getOrNull() } }
                .awaitAll()
                .filterNotNull()
        }
    }

    private class Post(val title: String, val url: String, val subreddit: String, val link: String)

    private fun posts(json: String): List<Post> {
        val memes = JSONObject(json).optJSONArray("memes") ?: return emptyList()
        return (0 until memes.length()).map { memes.getJSONObject(it) }
            .filter { !it.optBoolean("nsfw", true) && !it.optBoolean("spoiler", true) }
            .map { Post(it.optString("title"), it.optString("url"), it.optString("subreddit"), it.optString("postLink")) }
            .filter { p -> p.link.isNotEmpty() && listOf(".jpg", ".jpeg", ".png", ".webp").any { p.url.lowercase().endsWith(it) } }
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
