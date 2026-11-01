package dev.olaf.paperzero.ui

import dev.olaf.paperzero.core.design.skinCard
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.olaf.paperzero.R
import dev.olaf.paperzero.core.design.PrimaryPill
import dev.olaf.paperzero.core.design.pressScale
import dev.olaf.paperzero.core.design.rememberReducedMotion
import kotlin.math.sin

/**
 * A timed set of the subject, as something to make: a fanned stack of papers, what you'll get,
 * and a button. Making one shuffles the stack for a moment ("Picking 40 questions from 25
 * papers…") before it starts; an unfinished set is carried on at once instead.
 */
@Composable
fun TimedSetCard(papers: Int, unfinished: Boolean, onStart: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val still = rememberReducedMotion()
    var shuffling by remember { mutableStateOf(false) }
    val shuffle = remember { Animatable(0f) }
    LaunchedEffect(shuffling) {
        if (!shuffling) return@LaunchedEffect
        if (!still) shuffle.animateTo(1f, tween(SHUFFLE_MS, easing = LinearEasing))
        onStart()
        shuffling = false
        shuffle.snapTo(0f)
    }
    val go = { if (!shuffling) { if (unfinished) onStart() else shuffling = true } }
    val interaction = remember { MutableInteractionSource() }
    Surface(
        onClick = go,
        shape = MaterialTheme.shapes.extraLarge,
        color = colors.secondaryContainer,
        contentColor = colors.onSecondaryContainer,
        interactionSource = interaction,
        modifier = Modifier.fillMaxWidth().pressScale(interaction).skinCard(MaterialTheme.shapes.extraLarge),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Stack(Color(0xFFF7F3EE), colors.primary, shuffle.value, Modifier.size(width = 64.dp, height = 72.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.timed_set), style = MaterialTheme.typography.titleMedium)
                AnimatedContent(shuffling, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "line") { busy ->
                    Text(
                        if (busy) pluralStringResource(R.plurals.timed_set_picking, papers, papers)
                        else stringResource(if (unfinished) R.string.timed_set_unfinished else R.string.timed_set_line),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSecondaryContainer.copy(alpha = 0.8f),
                    )
                }
                Spacer(Modifier.height(10.dp))
                PrimaryPill(
                    if (unfinished) Icons.AutoMirrored.Rounded.ArrowForward else Icons.Rounded.Shuffle,
                    stringResource(if (unfinished) R.string.timed_set_continue else R.string.timed_set_new),
                    go,
                )
            }
        }
    }
}

/** A fan of papers; [t] from 0 to 1 shuffles them, the top one flicking to the back and round again. */
@Composable
private fun Stack(paper: Color, ink: Color, t: Float, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width * 0.62f
        val h = size.height * 0.78f
        val centre = Offset(size.width / 2, size.height / 2)
        val cards = 4
        for (i in 0 until cards) {
            // Each card's turn in the shuffle: lifted out to the side and slipped back under.
            val phase = ((t * SHUFFLES - i * 0.25f) % 1f + 1f) % 1f
            val lift = if (t > 0f) sin(phase * Math.PI).toFloat() else 0f
            val angle = (i - 1.5f) * 7f + lift * 18f * (if (i % 2 == 0) 1 else -1)
            val dx = lift * size.width * 0.28f * (if (i % 2 == 0) 1 else -1)
            rotate(angle, pivot = centre) {
                val topLeft = Offset(centre.x - w / 2 + dx, centre.y - h / 2)
                drawRoundRect(Color.Black.copy(alpha = 0.18f), topLeft + Offset(1.5f, 2.5f), Size(w, h), CornerRadius(6f, 6f))
                drawRoundRect(paper, topLeft, Size(w, h), CornerRadius(6f, 6f))
                // Lines of a question, and a ring for an answer chosen.
                for (k in 0 until 4) {
                    val y = topLeft.y + h * (0.22f + k * 0.15f)
                    drawLine(ink.copy(alpha = 0.35f), Offset(topLeft.x + w * 0.16f, y), Offset(topLeft.x + w * (if (k == 3) 0.55f else 0.84f), y), strokeWidth = 2.2f)
                }
                drawCircle(ink, w * 0.09f, Offset(topLeft.x + w * 0.74f, topLeft.y + h * 0.8f))
            }
        }
    }
}

private const val SHUFFLE_MS = 700
/** Times round the stack in one shuffle. */
private const val SHUFFLES = 2f
