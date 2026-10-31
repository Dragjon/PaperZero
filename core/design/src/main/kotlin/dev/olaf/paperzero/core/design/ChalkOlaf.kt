package dev.olaf.paperzero.core.design

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.luminance
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * The snowman, seen from the side, sat on a stool at a table and writing in an open book, a pile
 * of books beside it: drawn in chalk, as Oscanner's is, every line a little wobbly and laid down
 * in a few faint broken passes. Chalk-white on the dark theme, pencil-gray on the light one; the
 * carrot and twigs keep a muted color of their own. Decorative: it says nothing the text beside it
 * doesn't.
 */
@Composable
fun ChalkOlaf(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val dark = colors.background.luminance() < 0.5f
    val chalk = if (dark) Color(0xFFEDE6DC) else Color(0xFF5E544C)
    val carrot = if (dark) Color(0xFFE59A73) else Color(0xFFD9774E)
    val twig = if (dark) Color(0xFFB89478) else Color(0xFF8A6448)
    val strokes = remember { studyingStrokes() }
    Canvas(modifier) {
        // Drawn in a 240 × 200 box, scaled to fit and centered.
        val scale = min(size.width / BOX_W, size.height / BOX_H)
        translate((size.width - BOX_W * scale) / 2, (size.height - BOX_H * scale) / 2) {
            withTransform({ scale(scale, scale, Offset.Zero) }) {
                strokes.forEach { stroke ->
                    val color = when (stroke.ink) {
                        Ink.Chalk -> chalk
                        Ink.Carrot -> carrot
                        Ink.Twig -> twig
                    }
                    chalkLine(stroke.path, color, stroke.width, stroke.seed)
                }
            }
        }
    }
}

/** Its width over its height, to size it by. */
const val CHALK_OLAF_RATIO = 240f / 200f

private const val BOX_W = 240f
private const val BOX_H = 200f

private enum class Ink { Chalk, Carrot, Twig }

private class ChalkStroke(val path: Path, val width: Float, val ink: Ink, val seed: Int)

/** One line in chalk: a broken main pass and two fainter, slightly offset ones beside it. */
private fun DrawScope.chalkLine(path: Path, color: Color, width: Float, seed: Int) {
    val passes = listOf(
        Triple(Offset.Zero, 1f, 0.8f),
        Triple(Offset(0.7f, -0.5f), 0.55f, 0.35f),
        Triple(Offset(-0.6f, 0.6f), 0.4f, 0.3f),
    )
    passes.forEachIndexed { i, (offset, widthShare, alpha) ->
        translate(offset.x, offset.y) {
            drawPath(
                path,
                color.copy(alpha = alpha),
                style = Stroke(
                    width = width * widthShare,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                    // Little gaps along the line, where chalk skips over the board's grain.
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f + i * 2f, 1.6f + i * 0.6f), (seed * 3 + i * 5).toFloat()),
                ),
            )
        }
    }
}

/** A closed, slightly lumpy loop through points around a center: drawn by hand, not a compass. */
private fun wobblyLoop(cx: Float, cy: Float, rx: Float, ry: Float, seed: Int, start: Double = 0.0, sweep: Double = 2 * PI): Path {
    val random = Random(seed)
    val closed = sweep >= 2 * PI
    val n = 12
    val points = (0..n).map { i ->
        val a = start + sweep * i / n
        val wobble = 1f + (random.nextFloat() - 0.5f) * 0.06f
        Offset(cx + (rx * wobble * cos(a)).toFloat(), cy + (ry * wobble * sin(a)).toFloat())
    }.let { if (closed) it.dropLast(1) else it }
    return smooth(points, closed)
}

/** Catmull–Rom through [points], as cubic Béziers: a curve with no corners. */
private fun smooth(points: List<Offset>, closed: Boolean): Path {
    val path = Path()
    val n = points.size
    fun at(i: Int) = if (closed) points[(i + n) % n] else points[i.coerceIn(0, n - 1)]
    path.moveTo(points[0].x, points[0].y)
    val segments = if (closed) n else n - 1
    for (i in 0 until segments) {
        val p0 = at(i - 1)
        val p1 = at(i)
        val p2 = at(i + 1)
        val p3 = at(i + 2)
        path.cubicTo(
            p1.x + (p2.x - p0.x) / 6, p1.y + (p2.y - p0.y) / 6,
            p2.x - (p3.x - p1.x) / 6, p2.y - (p3.y - p1.y) / 6,
            p2.x, p2.y,
        )
    }
    if (closed) path.close()
    return path
}

private fun curve(vararg xy: Float): Path = smooth(xy.toList().chunked(2).map { Offset(it[0], it[1]) }, closed = false)

private fun studyingStrokes(): List<ChalkStroke> {
    val s = mutableListOf<ChalkStroke>()
    var seed = 1
    fun add(path: Path, width: Float, ink: Ink = Ink.Chalk) = s.add(ChalkStroke(path, width, ink, seed++))

    // The stool.
    add(curve(46f, 158f, 76f, 156f, 106f, 158f), 3f)
    add(curve(54f, 159f, 52f, 176f, 49f, 192f), 2.6f)
    add(curve(98f, 159f, 100f, 176f, 103f, 192f), 2.6f)
    add(curve(52f, 178f, 76f, 177f, 101f, 178f), 2f)
    // Body, sat on the stool, and head leaning in towards the book.
    add(wobblyLoop(76f, 124f, 35f, 33f, 11), 3.2f)
    add(wobblyLoop(102f, 68f, 27f, 26f, 12), 3.2f)
    // Twig hair: three on top, swept back a little, one with a fork.
    add(curve(95f, 43f, 90f, 33f, 83f, 24f), 2.6f, Ink.Twig)
    add(curve(103f, 42f, 103f, 31f, 106f, 20f), 2.6f, Ink.Twig)
    add(curve(111f, 44f, 117f, 35f, 124f, 29f), 2.6f, Ink.Twig)
    add(curve(119f, 35f, 125f, 36f, 129f, 33f), 2f, Ink.Twig)
    // One eye (we see his side), looking down at the page, and a brow raised in thought.
    add(wobblyLoop(116f, 65f, 3f, 3.6f, 21), 3.4f)
    add(curve(109f, 55f, 115f, 52f, 122f, 55f), 2f)
    // Carrot nose, pointing down at the book, with two little lines across it.
    add(curve(125f, 71f, 133f, 77f, 140f, 84f), 3f, Ink.Carrot)
    add(curve(123f, 79f, 131f, 82f, 140f, 84f), 3f, Ink.Carrot)
    add(curve(130f, 75f, 128f, 80f), 1.6f, Ink.Carrot)
    add(curve(135f, 78f, 134f, 82f), 1.6f, Ink.Carrot)
    // A small, concentrating smile.
    add(wobblyLoop(114f, 82f, 8f, 5f, 31, start = PI * 0.2, sweep = PI * 0.45), 2.2f)
    // Buttons down his front.
    add(wobblyLoop(106f, 108f, 2.4f, 2.4f, 41), 3f)
    add(wobblyLoop(109f, 124f, 2.4f, 2.4f, 42), 3f)
    add(wobblyLoop(107f, 140f, 2.4f, 2.4f, 43), 3f)
    // Arm reaching to the table; its twig fingers hold a pencil to the page.
    add(curve(96f, 104f, 122f, 98f, 147f, 99f), 2.6f, Ink.Twig)
    add(curve(140f, 98f, 145f, 93f, 149f, 91f), 2f, Ink.Twig)
    add(curve(141f, 100f, 146f, 103f, 150f, 104f), 2f, Ink.Twig)
    add(curve(148f, 90f, 158f, 108f), 3.4f)
    add(curve(158f, 108f, 160f, 112f), 1.8f)
    // The table: its top with some thickness, and two legs.
    add(curve(128f, 115f, 230f, 115f), 3f)
    add(curve(128f, 115f, 128f, 120f), 2.2f)
    add(curve(128f, 120f, 230f, 120f), 2.2f)
    add(curve(230f, 115f, 230f, 120f), 2.2f)
    add(curve(136f, 120f, 135f, 158f, 136f, 192f), 2.6f)
    add(curve(222f, 120f, 223f, 158f, 222f, 192f), 2.6f)
    // The open book: its cover flat on the table, two pages bowed up from the spine, some writing.
    add(curve(144f, 114f, 206f, 114f), 2f)
    add(curve(146f, 113f, 158f, 106f, 175f, 110f), 2.4f)
    add(curve(175f, 110f, 190f, 105f, 204f, 113f), 2.4f)
    add(curve(175f, 110f, 175f, 114f), 1.8f)
    add(curve(180f, 109f, 188f, 107f, 196f, 109f), 1.4f)
    add(curve(182f, 112f, 190f, 110f, 199f, 112f), 1.4f)
    add(curve(162f, 108f, 168f, 108f, 172f, 110f), 1.4f)
    // A pile of books at the back of the table, their spines facing us, each with a label.
    listOf(
        floatArrayOf(208f, 229f, 114f, 106f),
        floatArrayOf(210f, 227f, 106f, 98f),
        floatArrayOf(207f, 228f, 98f, 91f),
    ).forEach { (x0, x1, y0, y1) ->
        add(curve(x0, y0, x1, y0), 2.2f)
        add(curve(x0, y1, x1, y1), 2.2f)
        add(curve(x0, y0, x0, y1), 2.2f)
        add(curve(x1, y0, x1, y1), 2.2f)
        add(curve(x0 + 4, (y0 + y1) / 2, x0 + 10, (y0 + y1) / 2), 1.4f)
    }
    // Snowflakes.
    listOf(Offset(30f, 40f), Offset(160f, 30f), Offset(200f, 60f), Offset(22f, 118f)).forEachIndexed { i, c ->
        val r = if (i % 2 == 0) 5f else 4f
        for (k in 0 until 3) {
            val a = PI / 3 * k + 0.3 * i
            add(curve(c.x - (r * cos(a)).toFloat(), c.y - (r * sin(a)).toFloat(), c.x + (r * cos(a)).toFloat(), c.y + (r * sin(a)).toFloat()), 1.4f)
        }
    }
    // The floor.
    add(curve(26f, 193f, 90f, 190f, 150f, 193f, 210f, 190f, 236f, 193f), 2.2f)
    return s
}
