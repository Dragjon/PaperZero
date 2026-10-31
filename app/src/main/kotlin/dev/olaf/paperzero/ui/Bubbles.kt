package dev.olaf.paperzero.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import dev.olaf.paperzero.core.design.rememberReducedMotion
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/** A bubble of a story's ring: where round it, how far out (a share of the ring), how big. */
private class Bubble(val angle: Float, val reach: Float, val size: Float, val drift: Float)

/** [count] bubbles of a ring, the same each time for the same [seed]: bigger and smaller, never even. */
private fun bubbles(count: Int, seed: Int): List<Bubble> {
    val r = Random(seed)
    return List(count) { i ->
        Bubble(
            angle = (i + r.nextFloat() * 0.6f) / count * 2 * PI.toFloat(),
            reach = 0.92f + r.nextFloat() * 0.16f,
            size = 0.35f + r.nextFloat() * 0.65f,
            drift = 0.8f + r.nextFloat() * 0.4f,
        )
    }
}

/**
 * A story's ring, made of bubbles of different sizes in [color], slowly turning round [radius]
 * (unless motion is turned down on the phone). [biggest]: the largest bubble's radius.
 */
@Composable
fun BubbleRing(color: Color, radius: Dp, biggest: Dp, seed: Int, modifier: Modifier = Modifier) {
    val ring = remember(seed) { bubbles(RING_BUBBLES, seed) }
    val still = rememberReducedMotion()
    val turn by rememberInfiniteTransition(label = "ring").animateFloat(
        initialValue = 0f,
        targetValue = if (still) 0f else 2 * PI.toFloat(),
        animationSpec = infiniteRepeatable(tween(RING_TURN_MS, easing = LinearEasing), RepeatMode.Restart),
        label = "turn",
    )
    Canvas(modifier) {
        val c = center
        val r = radius.toPx()
        ring.forEach { b ->
            // All turning together, so the ring stays even.
            val a = b.angle + turn
            // Each bubble breathes in and out of the ring a little as it goes round.
            val out = r * (b.reach + 0.04f * sin(a * 3))
            drawCircle(color, biggest.toPx() * b.size, Offset(c.x + cos(a) * out, c.y + sin(a) * out))
        }
    }
}

/**
 * The way into a feed from its story: the ring's bubbles burst outwards in a spiral, growing as
 * they go, while a dark hole opens from the story until it fills the screen, the feed's black.
 * [from]: the story's centre, in this overlay's space. Calls [onDone] when the screen is covered.
 */
@Composable
fun BubbleSpiral(from: Offset, color: Color, seed: Int, onDone: () -> Unit) {
    val still = rememberReducedMotion()
    val t = remember { Animatable(0f) }
    val swarm = remember(seed) { bubbles(SPIRAL_BUBBLES, seed) }
    LaunchedEffect(Unit) {
        if (!still) t.animateTo(1f, tween(SPIRAL_MS, easing = FastOutSlowInEasing))
        onDone()
    }
    Canvas(Modifier.fillMaxSize()) { spiral(from, color, swarm, t.value) }
}

private fun DrawScope.spiral(from: Offset, color: Color, swarm: List<Bubble>, p: Float) {
    // Far enough to cover every corner from where it starts.
    val far = max(
        max(hypot(from.x, from.y), hypot(size.width - from.x, from.y)),
        max(hypot(from.x, size.height - from.y), hypot(size.width - from.x, size.height - from.y)),
    )
    val ring = 38f * density
    // The hole: slow to start, then quick, black at its heart.
    val hole = far * 1.05f * p * p
    drawCircle(Color.Black, hole, from)
    swarm.forEach { b ->
        val a = b.angle + p * SPIN * b.drift
        val dist = ring * b.reach + (far * 1.1f) * p * b.drift * 0.8f
        val r = (6f * density) * b.size * (1f + p * 5f)
        val at = Offset(from.x + cos(a) * dist, from.y + sin(a) * dist)
        // Lit in the science's colour, darkening as they near the edge of the hole.
        drawCircle(lerp(color, Color.Black, (p * 0.7f).coerceIn(0f, 0.7f)), r, at, alpha = (1f - p * 0.6f).coerceIn(0f, 1f))
    }
}

private const val RING_BUBBLES = 9
private const val RING_TURN_MS = 45_000
private const val SPIRAL_BUBBLES = 46
private const val SPIRAL_MS = 280
/** How far round the bubbles spiral on their way out (radians). */
private const val SPIN = 2.4f
