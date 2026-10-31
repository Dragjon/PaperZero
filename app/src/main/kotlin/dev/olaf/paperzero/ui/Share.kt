package dev.olaf.paperzero.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import androidx.core.content.FileProvider
import dev.olaf.paperzero.Meme
import dev.olaf.paperzero.R
import dev.olaf.paperzero.core.data.Question
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Shares a question as a picture, with a line saying where it's from (not its answer: let them
 * try), to whatever app is chosen. The picture is written to the cache for the moment.
 */
suspend fun shareQuestion(context: Context, question: Question, source: String) {
    val file = withContext(Dispatchers.IO) {
        runCatching {
            val image = context.assets.open(question.image).use { BitmapFactory.decodeStream(it) } ?: return@runCatching null
            // On white, as printed: a PNG of it, with no transparent edge to go black elsewhere.
            val out = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
            Canvas(out).apply {
                drawColor(Color.WHITE)
                drawBitmap(image, 0f, 0f, null)
            }
            val dir = File(context.cacheDir, "share").apply { mkdirs() }
            File(dir, "question.png").also { f -> f.outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        }.getOrNull()
    } ?: return
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.share", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, context.getString(R.string.share_question_text, source, question.number))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, context.getString(R.string.share)))
}

/** Shares a meme's post: its title and link. */
fun shareMeme(context: Context, meme: Meme) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, listOf(meme.title, meme.link).filter { it.isNotBlank() }.joinToString("\n"))
    }
    context.startActivity(Intent.createChooser(send, context.getString(R.string.share)))
}
