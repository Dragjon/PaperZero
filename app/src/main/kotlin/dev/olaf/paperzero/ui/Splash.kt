package dev.olaf.paperzero.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import dev.olaf.paperzero.core.design.rememberReducedMotion
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * A splash round a button the moment it turns out [right]: a ring spreading from its edge and a
 * burst of droplets flung out round it, in [color], fading as they go. Drawn outside the button,
 * over whatever's beside it. Nothing when motion is turned down on the phone.
 */
fun Modifier.splash(right: Boolean, color: Color): Modifier = composed {
    val still = rememberReducedMotion()
    val t = remember { Animatable(1f) }
    // Only when it turns right while on screen: not again on coming back to it.
    val was = remember { booleanArrayOf(right) }
    LaunchedEffect(right) {
        val now = right && !was[0]
        was[0] = right
        if (now && !still) {
            t.snapTo(0f)
            t.animateTo(1f, tween(SPLASH_MS, easing = FastOutSlowInEasing))
        }
    }
    drawWithContent {
        val p = t.value
        if (p > 0f && p < 1f) {
            val c = center
            // Out from the button's edge: as far again as it is tall, not as wide.
            val edge = Offset(size.width / 2, size.height / 2)
            val out = size.height * 1.1f * p
            val fade = if (p < 0.55f) 1f else (1f - p) / 0.45f
            // The ring: the button's own shape, grown.
            drawRoundRect(
                color.copy(alpha = 0.8f * fade),
                topLeft = Offset(-out, -out),
                size = androidx.compose.ui.geometry.Size(size.width + 2 * out, size.height + 2 * out),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2 + out),
                style = Stroke(width = 3.5f * density * (1f - 0.6f * p)),
            )
            // Droplets flung out from round its edge.
            for (i in 0 until DROPS) {
                val a = (i.toFloat() / DROPS) * 2 * PI.toFloat() + (if (i % 2 == 0) 0f else 0.22f)
                val reach = (if (i % 2 == 0) 1.5f else 1.1f) * size.height * p
                val x = c.x + cos(a) * (edge.x + reach)
                val y = c.y + sin(a) * (edge.y + reach)
                val r = density * (if (i % 2 == 0) 4.5f else 3f) * (1f - 0.5f * p)
                drawCircle(color.copy(alpha = fade), r, Offset(x, y))
            }
        }
        drawContent()
    }
}

private const val SPLASH_MS = 650
private const val DROPS = 16
