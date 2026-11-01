package dev.olaf.paperzero.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import dev.olaf.paperzero.core.design.LocalSkin
import dev.olaf.paperzero.core.design.likeBit
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** A like, where it was tapped on the page; [seed] tells one apart from the next and scatters its bits. */
class Burst(val at: Offset, val seed: Int)

/**
 * A like, as it lands: a heart pops in where it was tapped (overshooting, tipped a little), a ring
 * spreads from it, and the skin's own bits fly out and fall away (snowflakes in the classic look,
 * petals in Sakura, bats at Halloween…); then the heart floats up and fades. [onDone] when it has.
 */
@Composable
fun LikeBurst(burst: Burst, onDone: () -> Unit) {
    val skin = LocalSkin.current
    val primary = MaterialTheme.colorScheme.primary
    val heart = rememberVectorPainter(Icons.Rounded.Favorite)
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        t.animateTo(1f, tween(BURST_MS, easing = LinearEasing))
        onDone()
    }
    val random = remember { Random(burst.seed) }
    val tilt = remember { random.nextFloat() * 30f - 15f }
    val bits = remember {
        List(BITS) { i ->
            Bit(
                angle = 2 * PI * i / BITS + random.nextDouble() * 0.5,
                reach = 0.55f + 0.45f * random.nextFloat(),
                size = 0.7f + 0.6f * random.nextFloat(),
                spin = random.nextFloat() * 360f - 180f,
            )
        }
    }
    Canvas(Modifier.fillMaxSize()) {
        val p = t.value
        val c = burst.at
        fun out(x: Float) = 1f - (1f - x) * (1f - x)

        // The ring, quickly out and gone.
        val ring = (p / 0.45f).coerceAtMost(1f)
        if (ring < 1f) {
            drawCircle(
                primary.copy(alpha = 0.7f * (1f - ring)),
                radius = (28 + 84 * out(ring)).dp.toPx(),
                center = c,
                style = Stroke((3f * (1f - ring) + 0.5f).dp.toPx()),
            )
        }
        // The bits: thrown out from the heart, then falling a little as they fade.
        val fly = (p / 0.85f).coerceAtMost(1f)
        if (fly < 1f) {
            bits.forEachIndexed { i, b ->
                val d = out(fly) * b.reach * 120.dp.toPx()
                val at = c + Offset((d * cos(b.angle)).toFloat(), (d * sin(b.angle)).toFloat() + 36.dp.toPx() * fly * fly)
                val color = (if (i % 2 == 0) primary else Color.White).copy(alpha = 1f - fly * fly)
                likeBit(skin, at, 7.dp.toPx() * b.size * (1f - 0.35f * fly), b.spin * fly, color)
            }
        }
        // The heart: pops past full size and settles, then rises and fades.
        val pop = when {
            p < 0.16f -> 1.28f * out(p / 0.16f)
            p < 0.3f -> 1.28f - 0.28f * ((p - 0.16f) / 0.14f)
            else -> 1f
        }
        val rise = if (p > 0.55f) (p - 0.55f) / 0.45f * 56.dp.toPx() else 0f
        val fade = if (p < 0.6f) 1f else 1f - (p - 0.6f) / 0.4f
        val side = HEART.toPx()
        translate(c.x - side / 2, c.y - side / 2 - rise) {
            rotate(tilt, Offset(side / 2, side / 2)) {
                scale(pop, Offset(side / 2, side / 2)) {
                    // A soft shadow under it, so it reads on a white question as on the dark ground.
                    translate(0f, 3.dp.toPx()) {
                        with(heart) { draw(Size(side, side), alpha = 0.3f * fade, colorFilter = ColorFilter.tint(Color.Black)) }
                    }
                    with(heart) { draw(Size(side, side), alpha = fade, colorFilter = ColorFilter.tint(primary)) }
                }
            }
        }
    }
}

private class Bit(val angle: Double, val reach: Float, val size: Float, val spin: Float)

private const val BURST_MS = 900
private const val BITS = 10
private val HEART = 104.dp
