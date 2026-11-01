package dev.olaf.paperzero.core.design

import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.Outline
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/*
 * What makes a skin more than its colours: the shape of its cards and buttons, the type of its
 * headings, how a card is edged, how a section is headed, and what's drawn on the ground behind
 * every screen. Classic leaves everything as the app always was.
 */

/** Each skin's corners, smallest to largest. */
internal fun shapesFor(skin: Skin): Shapes = when (skin) {
    Skin.Classic, Skin.Aurora -> PaperShapes
    // A slate: barely-rounded, like a board's frame.
    Skin.Chalkboard -> stepped(4, 6, 8, 10, 12)
    // Soft and round as beach toys.
    Skin.Summer -> stepped(14, 20, 26, 32, 40)
    // Ruled and square, like lab equipment and graph paper.
    Skin.Lab -> stepped(2, 2, 3, 4, 6)
    // Petal-shaped: two corners full, two barely.
    Skin.Sakura -> Shapes(petal(8), petal(12), petal(16), petal(24), petal(28))
    // Cut corners, a little sharp and spooky.
    // Ice shards: two opposite corners cut sharp, the other two barely.
    Skin.Frost -> Shapes(shard(6), shard(8), shard(12), shard(16), shard(20))
    // Pixel art: corners stepped in blocks.
    Skin.Arcade -> Shapes(PixelShape(2.dp, 1), PixelShape(3.dp, 2), PixelShape(3.dp, 2), PixelShape(4.dp, 2), PixelShape(4.dp, 3))
    Skin.Halloween -> Shapes(CutCornerShape(4.dp), CutCornerShape(6.dp), CutCornerShape(8.dp), CutCornerShape(12.dp), CutCornerShape(14.dp))
}

private fun stepped(vararg r: Int) = Shapes(
    extraSmall = RoundedCornerShape(r[0].dp),
    small = RoundedCornerShape(r[1].dp),
    medium = RoundedCornerShape(r[2].dp),
    large = RoundedCornerShape(r[3].dp),
    extraLarge = RoundedCornerShape(r[4].dp),
)

private fun shard(r: Int): CornerBasedShape = CutCornerShape(topStart = r.dp, topEnd = (r / 5).dp, bottomEnd = r.dp, bottomStart = (r / 5).dp)

/** A rectangle whose corners step in [steps] blocks of [step], as pixel art draws a rounded box. */
class PixelShape(private val step: Dp, private val steps: Int) :
    CornerBasedShape(CornerSize(step * steps), CornerSize(step * steps), CornerSize(step * steps), CornerSize(step * steps)) {

    override fun copy(topStart: CornerSize, topEnd: CornerSize, bottomEnd: CornerSize, bottomStart: CornerSize) = this

    override fun createOutline(size: Size, topStart: Float, topEnd: Float, bottomEnd: Float, bottomStart: Float, layoutDirection: LayoutDirection): Outline {
        val n = steps
        val s = topStart / n
        // The top-left corner, from down the left edge round to along the top.
        val corner = buildList {
            for (k in 0 until n) {
                add(Offset(k * s, (n - k) * s))
                add(Offset((k + 1) * s, (n - k) * s))
            }
            add(Offset(n * s, 0f))
        }
        val w = size.width
        val h = size.height
        val points = corner +
            corner.reversed().map { Offset(w - it.x, it.y) } +
            corner.map { Offset(w - it.x, h - it.y) } +
            corner.reversed().map { Offset(it.x, h - it.y) }
        return Outline.Generic(Path().apply {
            moveTo(points[0].x, points[0].y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
            close()
        })
    }
}

private val Condensed = FontFamily(android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.NORMAL))

private fun petal(r: Int): CornerBasedShape = RoundedCornerShape(topStart = r.dp, topEnd = (r / 4).dp, bottomEnd = r.dp, bottomStart = (r / 4).dp)

/** The shape of the one main action ([PrimaryPill]) in [skin]. */
fun pillShape(skin: Skin): Shape = when (skin) {
    Skin.Classic, Skin.Summer, Skin.Aurora -> CircleShape
    Skin.Chalkboard -> RoundedCornerShape(10.dp)
    Skin.Lab -> RoundedCornerShape(4.dp)
    Skin.Sakura -> RoundedCornerShape(topStart = 28.dp, topEnd = 8.dp, bottomEnd = 28.dp, bottomStart = 8.dp)
    Skin.Frost -> CutCornerShape(topStart = 18.dp, topEnd = 4.dp, bottomEnd = 18.dp, bottomStart = 4.dp)
    Skin.Arcade -> PixelShape(4.dp, 3)
    Skin.Halloween -> CutCornerShape(16.dp)
}

/**
 * Each skin's type. Headings change face; reading text (bodies, small titles) stays Figtree in all
 * of them, so questions and explanations read the same whatever the skin.
 */
internal fun typographyFor(skin: Skin): Typography {
    val t = PaperTypography
    fun Typography.headings(f: (TextStyle) -> TextStyle) = copy(
        displayLarge = f(displayLarge), displayMedium = f(displayMedium), displaySmall = f(displaySmall),
        headlineLarge = f(headlineLarge), headlineMedium = f(headlineMedium), headlineSmall = f(headlineSmall),
        titleLarge = f(titleLarge), titleMedium = f(titleMedium),
    )
    return when (skin) {
        Skin.Classic -> t
        // Written up in chalk, by hand.
        Skin.Chalkboard -> t.headings { it.copy(fontFamily = FontFamily.Cursive, fontWeight = FontWeight.Bold, fontSize = it.fontSize * 1.12f) }
        // Big, heavy and tight, like a beach poster.
        Skin.Summer -> t.headings { it.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp) }
        // Light and widely spaced, like a planetarium's titles.
        Skin.Aurora -> t.headings { it.copy(fontWeight = FontWeight.Normal, letterSpacing = 1.2.sp) }
        // A lab notebook: monospace headings and labels.
        Skin.Lab -> t.headings { it.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp) }.copy(
            labelLarge = t.labelLarge.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
            labelMedium = t.labelMedium.copy(fontFamily = FontFamily.Monospace),
            labelSmall = t.labelSmall.copy(fontFamily = FontFamily.Monospace),
        )
        // A printed, gentle serif.
        Skin.Sakura -> t.headings { it.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium) }
        // A heavy old serif, like a horror film's title card.
        // Tall and narrow, like lettering cut in ice.
        Skin.Frost -> t.headings { it.copy(fontFamily = Condensed, fontWeight = FontWeight.Medium, letterSpacing = 0.8.sp) }
        // A cabinet's marquee: heavy monospace, spread out.
        Skin.Arcade -> t.headings { it.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp) }.copy(
            labelLarge = t.labelLarge.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black),
            labelMedium = t.labelMedium.copy(fontFamily = FontFamily.Monospace),
        )
        Skin.Halloween -> t.headings { it.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Black, letterSpacing = 0.6.sp) }
    }
}

@Composable
private fun isDark() = MaterialTheme.colorScheme.background.luminance() < 0.5f

/**
 * How a card is edged in the skin showing, for a card of [shape]. Put it on the card's own
 * modifier: it draws behind the card and over it.
 */
@Composable
fun Modifier.skinCard(shape: Shape): Modifier {
    val skin = LocalSkin.current
    val colors = MaterialTheme.colorScheme
    val dark = isDark()
    return when (skin) {
        Skin.Classic -> this
        // A chalk outline just inside the edge, drawn in a broken line.
        Skin.Chalkboard -> drawWithContent {
            drawContent()
            val inset = 3.dp.toPx()
            translate(inset, inset) {
                val outline = shape.createOutline(Size(size.width - 2 * inset, size.height - 2 * inset), layoutDirection, this)
                drawOutline(outline, colors.onSurface.copy(alpha = 0.32f), style = Stroke(1.6.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 7f, 4f, 7f))))
            }
        }
        // A sticker: a solid block shadow down and to the right, and a bold edge.
        Skin.Summer -> drawBehind {
            val outline = shape.createOutline(size, layoutDirection, this)
            translate(4.dp.toPx(), 4.dp.toPx()) { drawOutline(outline, if (dark) colors.primary.copy(alpha = 0.45f) else colors.onSurface.copy(alpha = 0.85f)) }
        }.drawWithContent {
            drawContent()
            drawOutline(shape.createOutline(size, layoutDirection, this), if (dark) colors.primary.copy(alpha = 0.6f) else colors.onSurface.copy(alpha = 0.85f), style = Stroke(2.dp.toPx()))
        }
        // Glass: an edge that shimmers from the aurora's green to violet and fades out.
        Skin.Aurora -> drawWithContent {
            drawContent()
            val outline = shape.createOutline(size, layoutDirection, this)
            val brush = Brush.linearGradient(
                0f to colors.primary.copy(alpha = 0.85f),
                0.5f to AuroraViolet.copy(alpha = 0.6f),
                1f to Color.Transparent,
                start = Offset.Zero,
                end = Offset(size.width, size.height),
            )
            drawOutline(outline, brush, style = Stroke(1.4.dp.toPx()))
        }
        // A hairline box with crop marks at its corners, in the action colour.
        Skin.Lab -> drawWithContent {
            drawContent()
            drawOutline(shape.createOutline(size, layoutDirection, this), colors.outline.copy(alpha = 0.6f), style = Stroke(1.dp.toPx()))
            val m = 10.dp.toPx()
            val w = 2.dp.toPx()
            val tick = colors.primary
            listOf(Offset(0f, 0f) to Offset(1f, 1f), Offset(size.width, 0f) to Offset(-1f, 1f), Offset(0f, size.height) to Offset(1f, -1f), Offset(size.width, size.height) to Offset(-1f, -1f)).forEach { (c, d) ->
                drawLine(tick, c, c + Offset(m * d.x, 0f), w, StrokeCap.Square)
                drawLine(tick, c, c + Offset(0f, m * d.y), w, StrokeCap.Square)
            }
        }
        // A second sheet peeking out underneath, and a blossom pinned to the corner.
        Skin.Sakura -> drawBehind {
            val outline = shape.createOutline(size, layoutDirection, this)
            translate(0f, 3.dp.toPx()) { drawOutline(outline, colors.primary.copy(alpha = if (dark) 0.35f else 0.4f)) }
        }.drawWithContent {
            drawContent()
            blossom(Offset(size.width - 14.dp.toPx(), 14.dp.toPx()), 4.dp.toPx(), colors.primary.copy(alpha = 0.85f))
        }
        // Frosted: an icy edge, a sheen across the top, and a crystal grown in the bottom corner.
        Skin.Frost -> drawWithContent {
            drawContent()
            val outline = shape.createOutline(size, layoutDirection, this)
            drawOutline(outline, Brush.verticalGradient(listOf(Color.White.copy(alpha = if (dark) 0.10f else 0.5f), Color.Transparent), endY = size.height * 0.35f))
            drawOutline(outline, colors.primary.copy(alpha = 0.6f), style = Stroke(1.2.dp.toPx()))
            likeBit(Skin.Frost, Offset(14.dp.toPx(), size.height - 14.dp.toPx()), 6.dp.toPx(), 0f, colors.primary.copy(alpha = 0.7f))
        }
        // A cabinet button: a hard block shadow in pixels and a bright edge.
        Skin.Arcade -> drawBehind {
            translate(4.dp.toPx(), 4.dp.toPx()) { drawOutline(shape.createOutline(size, layoutDirection, this), colors.primary.copy(alpha = if (dark) 0.35f else 0.45f)) }
        }.drawWithContent {
            drawContent()
            drawOutline(shape.createOutline(size, layoutDirection, this), colors.primary.copy(alpha = 0.85f), style = Stroke(2.dp.toPx()))
        }
        // A pumpkin-glow edge and a cobweb in the top corner.
        Skin.Halloween -> drawWithContent {
            drawContent()
            drawOutline(shape.createOutline(size, layoutDirection, this), colors.primary.copy(alpha = 0.55f), style = Stroke(1.4.dp.toPx()))
            cobweb(Offset(size.width, 0f), 26.dp.toPx(), mirrored = true, colors.onSurface.copy(alpha = 0.28f))
        }
    }
}

private val AuroraViolet = Color(0xFF9C8CFF)

/** Five petals round a dot. */
private fun DrawScope.blossom(c: Offset, r: Float, color: Color) {
    for (k in 0 until 5) {
        val a = 2 * PI / 5 * k - PI / 2
        drawCircle(color, r * 0.62f, c + Offset((r * cos(a)).toFloat(), (r * sin(a)).toFloat()))
    }
    drawCircle(Color.White.copy(alpha = 0.8f), r * 0.32f, c)
}

/** A web strung across a corner at [c]: spokes out from it and sagging threads between them. */
private fun DrawScope.cobweb(c: Offset, r: Float, mirrored: Boolean, color: Color) {
    val w = 1.dp.toPx()
    val spokes = 4
    val dir = if (mirrored) -1f else 1f
    val ends = (0..spokes).map { k ->
        val a = PI / 2 * k / spokes
        Offset(c.x + dir * (r * cos(a)).toFloat(), c.y + (r * sin(a)).toFloat())
    }
    ends.forEach { drawLine(color, c, it, w) }
    for (ring in 1..3) {
        val f = ring / 3.6f
        val path = Path()
        ends.forEachIndexed { i, e ->
            val p = c + (e - c) * f
            if (i == 0) path.moveTo(p.x, p.y) else {
                val prev = c + (ends[i - 1] - c) * f
                val mid = (prev + p) / 2f
                val sag = c + (mid - c) * 0.88f
                path.quadraticTo(sag.x, sag.y, p.x, p.y)
            }
        }
        drawPath(path, color, style = Stroke(w))
    }
}

/**
 * A section's heading, as the skin heads one: chalk underlined, a sun dot, wide aurora caps, a
 * lab notebook comment, a blossom, a moon. [end] sits at the far side (a "See all", say).
 */
@Composable
fun SkinHeading(title: String, modifier: Modifier = Modifier, end: @Composable () -> Unit = {}) {
    val skin = LocalSkin.current
    val colors = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        when (skin) {
            Skin.Classic -> Text(title, style = type.titleMedium, modifier = Modifier.weight(1f))
            Skin.Chalkboard -> Box(Modifier.weight(1f)) {
                Text(title, style = type.titleLarge, modifier = Modifier.padding(bottom = 6.dp))
                Canvas(Modifier.align(Alignment.BottomStart).size(width = 72.dp, height = 6.dp)) {
                    val p = Path().apply {
                        moveTo(0f, size.height * 0.6f)
                        var x = 0f
                        val step = size.width / 6
                        while (x < size.width) {
                            quadraticTo(x + step / 2, if ((x / step).toInt() % 2 == 0) 0f else size.height, x + step, size.height * 0.6f)
                            x += step
                        }
                    }
                    drawPath(p, colors.primary, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 3f))))
                }
            }
            Skin.Summer -> {
                Box(Modifier.size(12.dp).drawBehind { drawCircle(colors.primary) })
                Spacer(Modifier.width(10.dp))
                Text(title, style = type.titleLarge, modifier = Modifier.weight(1f))
            }
            Skin.Aurora -> {
                Text(title.uppercase(), style = type.labelLarge.copy(letterSpacing = 3.sp, fontWeight = FontWeight.Medium), color = colors.onSurfaceVariant)
                Spacer(Modifier.width(12.dp))
                Box(
                    Modifier.weight(1f).height(1.dp).drawBehind {
                        drawRect(Brush.horizontalGradient(listOf(colors.primary.copy(alpha = 0.7f), AuroraViolet.copy(alpha = 0.4f), Color.Transparent)))
                    },
                )
                Spacer(Modifier.width(8.dp))
            }
            Skin.Lab -> {
                Text("// " + title.lowercase(), style = type.titleMedium, color = colors.onSurface)
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f).height(1.dp).drawBehind { drawRect(colors.outline.copy(alpha = 0.5f)) })
                Spacer(Modifier.width(8.dp))
            }
            Skin.Sakura -> {
                Canvas(Modifier.size(16.dp)) { blossom(center, size.width * 0.32f, colors.primary) }
                Spacer(Modifier.width(8.dp))
                Text(title, style = type.titleLarge.copy(fontStyle = FontStyle.Italic), modifier = Modifier.weight(1f))
            }
            Skin.Frost -> {
                Text("❄", style = type.titleLarge, color = colors.primary)
                Spacer(Modifier.width(8.dp))
                Text(title, style = type.titleLarge, modifier = Modifier.weight(1f))
            }
            Skin.Arcade -> {
                Text("▶", style = type.titleMedium, color = colors.primary)
                Spacer(Modifier.width(8.dp))
                Text(title.uppercase(), style = type.titleMedium)
                Spacer(Modifier.width(10.dp))
                Box(
                    Modifier.weight(1f).height(4.dp).drawBehind {
                        val sq = 4.dp.toPx()
                        var x = 0f
                        while (x < size.width) {
                            drawRect(colors.primary.copy(alpha = 0.5f), Offset(x, 0f), Size(sq, sq))
                            x += sq * 2
                        }
                    },
                )
                Spacer(Modifier.width(8.dp))
            }
            Skin.Halloween -> {
                Text("☾", style = type.titleLarge, color = colors.primary)
                Spacer(Modifier.width(8.dp))
                Text(title, style = type.titleLarge, modifier = Modifier.weight(1f))
            }
        }
        end()
    }
}

/**
 * One bit of what a like bursts into in [skin], at [c], about [r] across, turned [turn] degrees:
 * a snowflake, chalk dust, a spark of sun, a star, a bubble, a petal, a bat.
 */
fun DrawScope.likeBit(skin: Skin, c: Offset, r: Float, turn: Float, color: Color) {
    val w = (r * 0.22f).coerceAtLeast(1.dp.toPx())
    rotate(turn, c) {
        when (skin) {
            Skin.Classic -> for (k in 0 until 3) {
                val a = PI / 3 * k
                val d = Offset((r * cos(a)).toFloat(), (r * sin(a)).toFloat())
                drawLine(color, c - d, c + d, w, StrokeCap.Round)
            }
            Skin.Chalkboard -> {
                drawCircle(color.copy(alpha = color.alpha * 0.8f), r * 0.45f, c)
                drawCircle(color.copy(alpha = color.alpha * 0.5f), r * 0.25f, c + Offset(r * 0.7f, -r * 0.3f))
            }
            Skin.Summer -> {
                drawLine(color, c - Offset(r, 0f), c + Offset(r, 0f), w, StrokeCap.Round)
                drawLine(color, c - Offset(0f, r), c + Offset(0f, r), w, StrokeCap.Round)
                drawCircle(color, r * 0.3f, c)
            }
            Skin.Aurora -> {
                val p = Path().apply {
                    moveTo(c.x, c.y - r)
                    quadraticTo(c.x, c.y, c.x + r, c.y)
                    quadraticTo(c.x, c.y, c.x, c.y + r)
                    quadraticTo(c.x, c.y, c.x - r, c.y)
                    quadraticTo(c.x, c.y, c.x, c.y - r)
                    close()
                }
                drawPath(p, color)
            }
            Skin.Lab -> drawCircle(color, r * 0.7f, c, style = Stroke(w))
            Skin.Sakura -> drawOval(color, topLeft = c - Offset(r, r * 0.55f), size = Size(2 * r, 1.1f * r))
            // A shard of ice: a long thin diamond.
            Skin.Frost -> {
                val p = Path().apply {
                    moveTo(c.x, c.y - r)
                    lineTo(c.x + r * 0.32f, c.y)
                    lineTo(c.x, c.y + r)
                    lineTo(c.x - r * 0.32f, c.y)
                    close()
                }
                drawPath(p, color)
            }
            // An 8-bit sparkle: a plus of pixels.
            Skin.Arcade -> {
                val px = r * 0.4f
                listOf(Offset(0f, 0f), Offset(px, 0f), Offset(-px, 0f), Offset(0f, px), Offset(0f, -px)).forEach { o ->
                    drawRect(color, c + o - Offset(px / 2, px / 2), Size(px, px))
                }
            }
            Skin.Halloween -> bat(c, r * 0.4f, color)
        }
    }
}

/**
 * What's drawn on the ground behind every screen in the skin showing: chalk dust on the board, the
 * sun and the sea, the aurora's curtains, graph paper, blossom, a Halloween night. Behind
 * everything, and faint, so nothing on top has to compete with it.
 */
@Composable
fun SkinBackdrop(modifier: Modifier = Modifier, night: Boolean = false) {
    val skin = LocalSkin.current
    if (skin == Skin.Classic) return
    val colors = MaterialTheme.colorScheme
    // At [night] (a feed, dark in any theme), drawn as on the dark side whatever the theme.
    val dark = night || isDark()
    val ink = if (night) Color.White else colors.onBackground
    val scatter = remember(skin) { Random(skin.ordinal * 31 + 7).let { r -> List(40) { Offset(r.nextFloat(), r.nextFloat()) to r.nextFloat() } } }
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        when (skin) {
            Skin.Classic -> Unit
            Skin.Chalkboard -> {
                // Old chalk dust where things were rubbed out, and the eraser's long sweeps.
                scatter.take(7).forEach { (p, s) ->
                    drawCircle(
                        Brush.radialGradient(listOf(ink.copy(alpha = if (dark) 0.05f else 0.035f), Color.Transparent), center = Offset(p.x * w, p.y * h), radius = (90 + 140 * s).dp.toPx()),
                        radius = (90 + 140 * s).dp.toPx(),
                        center = Offset(p.x * w, p.y * h),
                    )
                }
                scatter.drop(7).take(4).forEach { (p, s) ->
                    val y = p.y * h
                    val path = Path().apply {
                        moveTo(-20f, y)
                        quadraticTo(w * 0.5f, y - (40 + 60 * s).dp.toPx(), w + 20f, y + 20.dp.toPx())
                    }
                    drawPath(path, ink.copy(alpha = if (dark) 0.035f else 0.025f), style = Stroke((26 + 20 * s).dp.toPx(), cap = StrokeCap.Round))
                }
            }
            Skin.Summer -> {
                // The sun's glow over the top corner, and the sea's swell along the bottom.
                val sun = Offset(w + 6.dp.toPx(), -6.dp.toPx())
                drawCircle(Brush.radialGradient(listOf(colors.primary.copy(alpha = if (dark) 0.22f else 0.28f), Color.Transparent), center = sun, radius = w * 0.75f), w * 0.75f, sun)
                drawCircle(colors.primary.copy(alpha = if (dark) 0.25f else 0.35f), 34.dp.toPx(), sun)
                for (i in 0 until 4) {
                    val base = h * (0.80f + i * 0.055f)
                    val amp = 7.dp.toPx()
                    val path = Path().apply {
                        moveTo(0f, base)
                        var x = 0f
                        val step = w / 5
                        while (x < w + step) {
                            quadraticTo(x + step / 4, base - amp, x + step / 2, base)
                            quadraticTo(x + 3 * step / 4, base + amp, x + step, base)
                            x += step
                        }
                    }
                    drawPath(path, SummerSea.copy(alpha = (if (dark) 0.10f else 0.14f) + i * 0.03f), style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
                }
            }
            Skin.Aurora -> {
                // Curtains of light hanging from the top of the sky, and stars behind them.
                if (dark) scatter.forEach { (p, s) -> drawCircle(Color.White.copy(alpha = 0.15f + 0.35f * s), (0.6f + 1.1f * s).dp.toPx(), Offset(p.x * w, p.y * h * 0.75f)) }
                listOf(Triple(0.18f, colors.primary, 0.42f), Triple(0.55f, AuroraViolet, 0.36f), Triple(0.85f, colors.primary, 0.3f)).forEach { (x, c, depth) ->
                    val cx = w * x
                    rotate(-12f, Offset(cx, 0f)) {
                        drawRect(
                            Brush.verticalGradient(listOf(c.copy(alpha = if (dark) 0.22f else 0.16f), Color.Transparent), startY = 0f, endY = h * depth),
                            topLeft = Offset(cx - w * 0.16f, -h * 0.05f),
                            size = Size(w * 0.32f, h * depth),
                        )
                    }
                }
            }
            Skin.Lab -> {
                // Graph paper: a fine grid, every fifth line heavier.
                val step = 16.dp.toPx()
                val fine = colors.primary.copy(alpha = if (dark) 0.06f else 0.08f)
                val bold = colors.primary.copy(alpha = if (dark) 0.11f else 0.15f)
                var i = 0
                var x = 0f
                while (x < w) {
                    drawLine(if (i % 5 == 0) bold else fine, Offset(x, 0f), Offset(x, h), 1f)
                    x += step; i++
                }
                i = 0
                var y = 0f
                while (y < h) {
                    drawLine(if (i % 5 == 0) bold else fine, Offset(0f, y), Offset(w, y), 1f)
                    y += step; i++
                }
            }
            Skin.Sakura -> {
                // A blush at the top, and petals drifting down the page.
                drawRect(Brush.verticalGradient(listOf(colors.primary.copy(alpha = if (dark) 0.12f else 0.14f), Color.Transparent), endY = h * 0.35f))
                scatter.take(18).forEach { (p, s) ->
                    val c = Offset(p.x * w, p.y * h)
                    rotate(360f * s, c) {
                        drawOval(colors.primary.copy(alpha = if (dark) 0.16f else 0.22f), topLeft = c - Offset(5.dp.toPx(), 3.dp.toPx()), size = Size(10.dp.toPx(), 6.dp.toPx()) * (0.7f + 0.6f * s))
                    }
                }
            }
            Skin.Frost -> {
                // Frost grown in from the top corners, snow falling, and a drift along the bottom.
                val ice = (if (dark) ink else colors.primary).copy(alpha = if (dark) 0.16f else 0.28f)
                frostCorner(Offset(0f, 0f), 1f, 110.dp.toPx(), ice)
                frostCorner(Offset(w, 0f), -1f, 90.dp.toPx(), ice)
                scatter.forEach { (p, s) ->
                    drawCircle((if (dark) ink else colors.primary).copy(alpha = (if (dark) 0.18f else 0.25f) + 0.3f * s), (1f + 2.2f * s).dp.toPx(), Offset(p.x * w, p.y * h))
                }
                val drift = Path().apply {
                    moveTo(0f, h)
                    lineTo(0f, h * 0.9f)
                    quadraticTo(w * 0.25f, h * 0.84f, w * 0.5f, h * 0.89f)
                    quadraticTo(w * 0.78f, h * 0.94f, w, h * 0.86f)
                    lineTo(w, h)
                    close()
                }
                drawPath(drift, (if (dark) ink else colors.primary).copy(alpha = if (dark) 0.05f else 0.08f))
            }
            Skin.Arcade -> {
                // A synthwave floor running off to the horizon, pixel stars over it, and the faint
                // scanlines of an old screen over everything.
                val horizon = h * 0.7f
                val grid = colors.primary.copy(alpha = if (dark) 0.22f else 0.3f)
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, colors.primary.copy(alpha = if (dark) 0.12f else 0.1f)), startY = horizon - 60.dp.toPx(), endY = horizon), Offset(0f, horizon - 60.dp.toPx()), Size(w, 60.dp.toPx()))
                var gap = 6.dp.toPx()
                var y = horizon
                while (y < h) {
                    drawLine(grid, Offset(0f, y), Offset(w, y), 1.dp.toPx())
                    y += gap
                    gap *= 1.35f
                }
                for (k in -8..8) {
                    drawLine(grid, Offset(w / 2 + k * w * 0.02f, horizon), Offset(w / 2 + k * w * 0.22f, h), 1.dp.toPx())
                }
                scatter.take(26).forEach { (p, s) ->
                    val sq = (2 + 2 * (s * 2).toInt()).dp.toPx() / 2
                    drawRect(ink.copy(alpha = if (dark) 0.25f + 0.3f * s else 0.12f + 0.12f * s), Offset(p.x * w, p.y * horizon * 0.9f), Size(sq, sq))
                }
                val line = 4.dp.toPx()
                var sy = 0f
                while (sy < h) {
                    drawLine(ink.copy(alpha = if (dark) 0.03f else 0.025f), Offset(0f, sy), Offset(w, sy), 1f)
                    sy += line
                }
            }
            Skin.Halloween -> {
                // A full moon behind the top corner, a cobweb in the other, bats, and a dark edge.
                val moon = Offset(w + 4.dp.toPx(), -4.dp.toPx())
                drawCircle(Brush.radialGradient(listOf(HalloweenMoon.copy(alpha = if (dark) 0.22f else 0.3f), Color.Transparent), center = moon, radius = 140.dp.toPx()), 140.dp.toPx(), moon)
                drawCircle(HalloweenMoon.copy(alpha = if (dark) 0.3f else 0.45f), 30.dp.toPx(), moon)
                cobweb(Offset(0f, 0f), 120.dp.toPx(), mirrored = false, ink.copy(alpha = if (dark) 0.14f else 0.12f))
                scatter.take(5).forEach { (p, s) ->
                    bat(Offset(w * (0.15f + 0.7f * p.x), h * (0.06f + 0.3f * p.y)), (5 + 5 * s).dp.toPx(), ink.copy(alpha = if (dark) 0.22f else 0.18f))
                }
                if (dark) drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.35f)), center = Offset(w / 2, h / 2), radius = h * 0.75f))
            }
        }
    }
}

private val SummerSea = Color(0xFF2FA4C8)
private val HalloweenMoon = Color(0xFFFFD27A)

/** Frost creeping in from a corner at [c]: a few branching ice ferns, [dir] 1 rightwards, -1 leftwards. */
private fun DrawScope.frostCorner(c: Offset, dir: Float, r: Float, color: Color) {
    val w = 1.2.dp.toPx()
    listOf(0.12, 0.42, 0.75, 1.15).forEach { a ->
        val end = c + Offset(dir * (r * cos(a)).toFloat(), (r * sin(a)).toFloat())
        drawLine(color, c, end, w, StrokeCap.Round)
        for (k in 1..4) {
            val f = k / 5f
            val at = c + (end - c) * f
            val len = r * 0.16f * (1f - f * 0.6f)
            for (side in listOf(-1, 1)) {
                val b = a + side * 0.6
                drawLine(color, at, at + Offset(dir * (len * cos(b)).toFloat(), (len * sin(b)).toFloat()), w * 0.8f, StrokeCap.Round)
            }
        }
    }
}

/** A bat in flight: two scalloped wings and a small body. */
private fun DrawScope.bat(c: Offset, s: Float, color: Color) {
    val p = Path().apply {
        moveTo(c.x, c.y)
        quadraticTo(c.x - s * 1.2f, c.y - s * 1.1f, c.x - s * 3f, c.y - s * 0.6f)
        quadraticTo(c.x - s * 2.2f, c.y - s * 0.1f, c.x - s * 2.1f, c.y + s * 0.5f)
        quadraticTo(c.x - s * 1.5f, c.y - s * 0.05f, c.x - s * 1f, c.y + s * 0.45f)
        quadraticTo(c.x - s * 0.6f, c.y + s * 0.05f, c.x, c.y + s * 0.6f)
        quadraticTo(c.x + s * 0.6f, c.y + s * 0.05f, c.x + s * 1f, c.y + s * 0.45f)
        quadraticTo(c.x + s * 1.5f, c.y - s * 0.05f, c.x + s * 2.1f, c.y + s * 0.5f)
        quadraticTo(c.x + s * 2.2f, c.y - s * 0.1f, c.x + s * 3f, c.y - s * 0.6f)
        quadraticTo(c.x + s * 1.2f, c.y - s * 1.1f, c.x, c.y)
        close()
    }
    drawPath(p, color)
}
