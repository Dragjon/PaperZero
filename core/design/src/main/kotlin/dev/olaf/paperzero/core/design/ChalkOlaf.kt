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
 * carrot and twigs keep a muted color of their own. Each [Skin] dresses him for it (a mortarboard
 * at the blackboard, sunglasses in summer, a scarf under the aurora, goggles in the lab, a blossom
 * in spring) in the skin's own colour, and changes what drifts around him. Decorative: it says
 * nothing the text beside it doesn't.
 */
@Composable
fun ChalkOlaf(modifier: Modifier = Modifier, skin: Skin = LocalSkin.current) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val inks = olafInks(skin, dark)
    val strokes = remember(skin) { studyingStrokes(skin) }
    Canvas(modifier) {
        // Drawn in a 240 × 200 box, scaled to fit and centered.
        val scale = min(size.width / BOX_W, size.height / BOX_H)
        translate((size.width - BOX_W * scale) / 2, (size.height - BOX_H * scale) / 2) {
            withTransform({ scale(scale, scale, Offset.Zero) }) {
                strokes.forEach { stroke ->
                    val color = when (stroke.ink) {
                        Ink.Chalk -> inks.chalk
                        Ink.Carrot -> inks.carrot
                        Ink.Twig -> inks.twig
                        Ink.Accent -> inks.accent
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

private enum class Ink { Chalk, Carrot, Twig, Accent }

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

private fun studyingStrokes(skin: Skin): List<ChalkStroke> {
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
    // Twig hair: three on top, swept back a little, one with a fork. Under a mortarboard, only
    // the one that pokes out behind.
    if (skin == Skin.Chalkboard) {
        add(curve(86f, 50f, 79f, 45f, 72f, 43f), 2.6f, Ink.Twig)
    } else {
        add(curve(95f, 43f, 90f, 33f, 83f, 24f), 2.6f, Ink.Twig)
        add(curve(103f, 42f, 103f, 31f, 106f, 20f), 2.6f, Ink.Twig)
        add(curve(111f, 44f, 117f, 35f, 124f, 29f), 2.6f, Ink.Twig)
        add(curve(119f, 35f, 125f, 36f, 129f, 33f), 2f, Ink.Twig)
    }
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
    // A pile of books at the back of the table, their spines facing us, each with a label. In the
    // lab, a conical flask stands there instead.
    if (skin != Skin.Lab) listOf(
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
    dressed(skin) { path, width, ink -> add(path, width, ink) }
    // The floor.
    add(curve(26f, 193f, 90f, 190f, 150f, 193f, 210f, 190f, 236f, 193f), 2.2f)
    return s
}

/** What [skin] puts on him, and what drifts in the air around him. */
private fun dressed(skin: Skin, add: (Path, Float, Ink) -> Unit) {
    fun chalk(path: Path, width: Float) = add(path, width, Ink.Chalk)
    fun accent(path: Path, width: Float) = add(path, width, Ink.Accent)
    fun snowflakes(at: List<Offset>) = at.forEachIndexed { i, c ->
        val r = if (i % 2 == 0) 5f else 4f
        for (k in 0 until 3) {
            val a = PI / 3 * k + 0.3 * i
            chalk(curve(c.x - (r * cos(a)).toFloat(), c.y - (r * sin(a)).toFloat(), c.x + (r * cos(a)).toFloat(), c.y + (r * sin(a)).toFloat()), 1.4f)
        }
    }
    /** A small four-pointed twinkle. */
    fun star(x: Float, y: Float, r: Float) {
        chalk(curve(x - r, y, x + r, y), 1.3f)
        chalk(curve(x, y - r, x, y + r), 1.3f)
    }
    when (skin) {
        Skin.Classic -> snowflakes(listOf(Offset(30f, 40f), Offset(160f, 30f), Offset(200f, 60f), Offset(22f, 118f)))
        Skin.Chalkboard -> {
            // A mortarboard: its square top seen from a little above (a diamond), the cap under it
            // hugging his head, and the tassel hanging off the front corner.
            accent(curve(76f, 36f, 106f, 27f), 2.8f)
            accent(curve(106f, 27f, 136f, 35f), 2.8f)
            accent(curve(136f, 35f, 106f, 45f), 2.8f)
            accent(curve(106f, 45f, 76f, 36f), 2.8f)
            accent(curve(86f, 40f, 85f, 47f, 88f, 50f), 2.4f)
            accent(curve(124f, 41f, 125f, 47f, 122f, 50f), 2.4f)
            accent(curve(88f, 50f, 105f, 55f, 122f, 50f), 2.4f)
            accent(wobblyLoop(106f, 36f, 1.6f, 1.2f, 51), 2.2f)
            accent(curve(106f, 36f, 122f, 38f, 134f, 37f), 1.5f)
            accent(curve(134f, 37f, 135f, 46f, 134f, 54f), 1.8f)
            accent(wobblyLoop(134f, 56f, 1.8f, 2.6f, 52), 2.2f)
            // The board behind him: an atom, a wave, a sum and a tick.
            chalk(wobblyLoop(32f, 42f, 15f, 5f, 53), 1.4f)
            chalk(wobblyLoop(32f, 42f, 5f, 15f, 54), 1.4f)
            chalk(wobblyLoop(32f, 42f, 2f, 2f, 55), 2.4f)
            chalk(curve(150f, 34f, 158f, 26f, 166f, 34f, 174f, 42f, 182f, 34f, 190f, 26f, 198f, 34f), 1.4f)
            chalk(curve(18f, 112f, 26f, 112f), 1.4f)
            chalk(curve(22f, 108f, 22f, 116f), 1.4f)
            chalk(curve(30f, 110f, 38f, 110f), 1.4f)
            chalk(curve(30f, 114f, 38f, 114f), 1.4f)
            chalk(curve(205f, 58f, 211f, 66f, 219f, 50f), 1.4f)
        }
        Skin.Summer -> {
            // Sunglasses: a lens over his eye, hatched dark, and the arm back over his head.
            accent(wobblyLoop(117f, 65f, 7.5f, 6f, 61), 2.6f)
            chalk(curve(112f, 62f, 120f, 68f), 1.2f)
            chalk(curve(114f, 60f, 122f, 66f), 1.2f)
            accent(curve(110f, 63f, 100f, 61f, 90f, 62f), 2.2f)
            // The sun, where the snow would be; a drip, and a little puddle under the stool.
            accent(wobblyLoop(34f, 38f, 10f, 10f, 62), 2.6f)
            for (k in 0 until 8) {
                val a = PI / 4 * k + 0.2
                accent(curve(34f + (15 * cos(a)).toFloat(), 38f + (15 * sin(a)).toFloat(), 34f + (21 * cos(a)).toFloat(), 38f + (21 * sin(a)).toFloat()), 1.8f)
            }
            chalk(curve(80f, 56f, 77f, 62f, 79f, 66f, 82f, 62f, 80f, 56f), 1.6f)
            chalk(wobblyLoop(76f, 194f, 26f, 2.4f, 63), 1.6f)
            star(190f, 40f, 3f)
        }
        Skin.Aurora -> {
            // A scarf round his neck, its tail blown back.
            accent(curve(80f, 89f, 94f, 95f, 114f, 91f), 3f)
            accent(curve(81f, 95f, 95f, 101f, 114f, 97f), 3f)
            accent(curve(84f, 96f, 74f, 108f, 68f, 122f), 2.6f)
            accent(curve(90f, 99f, 81f, 111f, 76f, 125f), 2.6f)
            accent(curve(68f, 122f, 72f, 124f, 76f, 125f), 2f)
            // The aurora, in two loose curtains across the sky; a crescent moon, and stars.
            accent(curve(8f, 28f, 50f, 14f, 90f, 22f, 140f, 10f, 190f, 20f, 234f, 8f), 1.6f)
            accent(curve(14f, 38f, 60f, 26f, 104f, 32f), 1.2f)
            chalk(wobblyLoop(206f, 48f, 10f, 10f, 71, start = PI * 0.55, sweep = PI * 1.1), 2.2f)
            chalk(wobblyLoop(210f, 46f, 7f, 8f, 72, start = PI * 0.6, sweep = PI * 1.0), 1.6f)
            star(30f, 66f, 3.4f)
            star(160f, 44f, 2.8f)
            star(22f, 120f, 3f)
            star(180f, 70f, 2.4f)
        }
        Skin.Lab -> {
            // Safety goggles: the strap round his head and a lens over his eye.
            accent(curve(78f, 62f, 96f, 58f, 109f, 61f), 2.2f)
            accent(wobblyLoop(117f, 64f, 8f, 6.5f, 81), 2.6f)
            // The flask on the table, some liquid in it, bubbling.
            chalk(curve(206f, 114f, 230f, 114f), 2.2f)
            chalk(curve(206f, 114f, 214f, 97f), 2.2f)
            chalk(curve(230f, 114f, 222f, 97f), 2.2f)
            chalk(curve(214f, 97f, 214f, 87f), 2.2f)
            chalk(curve(222f, 97f, 222f, 87f), 2.2f)
            chalk(curve(212f, 87f, 224f, 87f), 2f)
            accent(curve(209f, 108f, 218f, 106f, 227f, 108f), 2f)
            accent(wobblyLoop(218f, 80f, 2f, 2f, 82), 1.8f)
            accent(wobblyLoop(221f, 72f, 2.6f, 2.6f, 83), 1.8f)
            accent(wobblyLoop(216f, 63f, 3.2f, 3.2f, 84), 1.8f)
            // Molecules drifting where the snow would: two atoms and a bond.
            listOf(Offset(30f, 40f), Offset(160f, 30f), Offset(22f, 118f)).forEachIndexed { i, c ->
                chalk(wobblyLoop(c.x - 5f, c.y, 3f, 3f, 85 + i), 1.6f)
                chalk(wobblyLoop(c.x + 5f, c.y - 3f, 2.2f, 2.2f, 88 + i), 1.6f)
                chalk(curve(c.x - 2f, c.y - 1f, c.x + 3f, c.y - 2.4f), 1.2f)
            }
        }
        Skin.Sakura -> {
            // A blossom tucked into his twigs: five petals round a center.
            for (k in 0 until 5) {
                val a = 2 * PI / 5 * k - PI / 2
                accent(wobblyLoop(88f + (4.6 * cos(a)).toFloat(), 36f + (4.6 * sin(a)).toFloat(), 3f, 3f, 91 + k), 2f)
            }
            chalk(wobblyLoop(88f, 36f, 1.2f, 1.2f, 96), 1.6f)
            // Petals falling, one landed on the pile of books.
            listOf(Offset(30f, 40f), Offset(160f, 30f), Offset(200f, 60f), Offset(22f, 118f), Offset(58f, 70f), Offset(218f, 88f)).forEachIndexed { i, c ->
                accent(curve(c.x - 3f, c.y, c.x, c.y - 2.5f + (i % 2), c.x + 3f, c.y, c.x, c.y + 2f, c.x - 3f, c.y), 1.8f)
            }
        }
    }
}
