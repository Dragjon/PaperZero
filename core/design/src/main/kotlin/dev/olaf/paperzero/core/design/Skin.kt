package dev.olaf.paperzero.core.design

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * How the whole app is dressed: its grounds, its one action colour, and what chalk Olaf has on.
 * Classic is the app as it always was (and the only one the Background setting applies to). Each
 * skin has a light and a dark side, so it follows the Theme setting like the rest of the app.
 */
enum class Skin { Classic, Chalkboard, Summer, Aurora, Lab, Sakura, Halloween, Frost, Arcade }

/** The skin showing, for anything drawn to match it (chalk Olaf). */
val LocalSkin = staticCompositionLocalOf { Skin.Classic }

/**
 * One side of a skin. As in the classic theme, ink on the action colour is dark, and only
 * pressable things wear it.
 */
private class SkinColors(
    val primary: Long,
    val onPrimary: Long,
    val container: Long,
    val onContainer: Long,
    /** What the screens stand on. */
    val ground: Long,
    val ink: Long,
    /** Secondary text and icons. */
    val inkSoft: Long,
)

private val Day = mapOf(
    // A sage board, with green chalk for actions.
    Skin.Chalkboard to SkinColors(0xFF5FB487, 0xFF06210F, 0xFFD8EEDF, 0xFF0D3320, 0xFFEEF3EC, 0xFF1C2A21, 0xFF55675B),
    // Sand, and the sun.
    Skin.Summer to SkinColors(0xFFFFB020, 0xFF2A1A00, 0xFFFFEBC2, 0xFF3D2700, 0xFFFFF8EC, 0xFF2A2116, 0xFF6E5F4B),
    // A pale polar sky and the aurora's green.
    Skin.Aurora to SkinColors(0xFF3FCBA4, 0xFF04261C, 0xFFD6F6EC, 0xFF063B2C, 0xFFF4F6FD, 0xFF1B2133, 0xFF5C6580),
    // Clean bench white, and a cyan like copper sulfate.
    Skin.Lab to SkinColors(0xFF3EC1D3, 0xFF032A30, 0xFFD5F2F6, 0xFF06363D, 0xFFF6F9FA, 0xFF1A2326, 0xFF5A6970),
    // Blossom on white.
    Skin.Sakura to SkinColors(0xFFFF8FB1, 0xFF3A0A1C, 0xFFFFE0EA, 0xFF4A1028, 0xFFFFF6F8, 0xFF2E1F24, 0xFF765E66),
    // A harvest afternoon: parchment and pumpkin, with plum ink.
    Skin.Halloween to SkinColors(0xFFF5841F, 0xFF2A1200, 0xFFFFE1C4, 0xFF3D1C00, 0xFFF8F0E6, 0xFF2A1A2E, 0xFF6D5A70),
    // Fresh snow under a winter sky, and glacier blue.
    Skin.Frost to SkinColors(0xFF6EC6F0, 0xFF062433, 0xFFD7EFFB, 0xFF07344A, 0xFFF2F8FD, 0xFF18303E, 0xFF557080),
    // An arcade's daytime cabinet art: pale violet, with the screen's green.
    Skin.Arcade to SkinColors(0xFF2BD67B, 0xFF04210F, 0xFFE6E0FF, 0xFF22145C, 0xFFF4F2FF, 0xFF1B1638, 0xFF5D5885),
)

private val Night = mapOf(
    // A real blackboard: green-black, chalk-white writing, yellow chalk for actions.
    Skin.Chalkboard to SkinColors(0xFFF5D76E, 0xFF2B2306, 0xFF2C3B31, 0xFFF7EBC0, 0xFF18241D, 0xFFEEF0E6, 0xFFA9B5AA),
    // A warm night by the sea.
    Skin.Summer to SkinColors(0xFFFFC14D, 0xFF2A1A00, 0xFF3A3220, 0xFFFFE7B5, 0xFF10202A, 0xFFF2EEE4, 0xFFA9B3B6),
    // The polar night, lit green.
    Skin.Aurora to SkinColors(0xFF5EE6C0, 0xFF04261C, 0xFF1A3340, 0xFFC9FFF0, 0xFF0E1424, 0xFFEAF0FF, 0xFFA3ADC7),
    Skin.Lab to SkinColors(0xFF6ED8E6, 0xFF032A30, 0xFF1C3337, 0xFFCFF6FB, 0xFF121A1C, 0xFFECF4F5, 0xFF9FB0B4),
    Skin.Sakura to SkinColors(0xFFFFA3C0, 0xFF3A0A1C, 0xFF3E2530, 0xFFFFDCE7, 0xFF1E1519, 0xFFF7ECEF, 0xFFBCA6AD),
    // Halloween night: purple-black, a glowing pumpkin for actions.
    Skin.Halloween to SkinColors(0xFFFF8A1F, 0xFF2A1200, 0xFF3A2140, 0xFFFFDDBF, 0xFF140E1A, 0xFFF3E9F7, 0xFFB9A6C4),
    // A polar night, the ice lit blue.
    Skin.Frost to SkinColors(0xFF8AD8FF, 0xFF062433, 0xFF163548, 0xFFD3F1FF, 0xFF0B1A26, 0xFFEAF6FF, 0xFF9DB6C6),
    // A dark arcade: deep violet and a glowing green screen.
    Skin.Arcade to SkinColors(0xFF39FF88, 0xFF04210F, 0xFF1E1A3D, 0xFFC9FFE0, 0xFF0D0B1E, 0xFFF1EEFF, 0xFFA9A2D6),
)

/**
 * [this] scheme dressed in [skin]: its action colour, its ground, and cards a step off the ground
 * (lighter on dark, whiter then a shade darker on light), so cards and dock keep their places.
 */
internal fun ColorScheme.dressed(skin: Skin, dark: Boolean): ColorScheme {
    val c = (if (dark) Night else Day)[skin] ?: return this
    val ground = Color(c.ground)
    val ink = Color(c.ink)
    val soft = Color(c.inkSoft)
    fun up(amount: Float) = lerp(ground, if (dark) Color.White else ink, amount)
    fun white(amount: Float) = lerp(ground, Color.White, amount)
    return copy(
        primary = Color(c.primary),
        onPrimary = Color(c.onPrimary),
        primaryContainer = Color(c.container),
        onPrimaryContainer = Color(c.onContainer),
        secondary = soft,
        secondaryContainer = Color(c.container),
        onSecondaryContainer = Color(c.onContainer),
        inversePrimary = Color(c.primary),
        background = ground,
        onBackground = ink,
        surface = ground,
        onSurface = ink,
        surfaceDim = ground,
        surfaceVariant = if (dark) up(0.08f) else up(0.04f),
        onSurfaceVariant = soft,
        surfaceContainerLowest = if (dark) lerp(ground, Color.Black, 0.25f) else white(1f),
        surfaceContainerLow = if (dark) up(0.03f) else white(0.5f),
        surfaceContainer = if (dark) up(0.05f) else white(0.8f),
        surfaceContainerHigh = if (dark) up(0.08f) else up(0.04f),
        surfaceContainerHighest = if (dark) up(0.11f) else up(0.07f),
        surfaceBright = if (dark) up(0.14f) else white(1f),
        outline = lerp(ground, soft, 0.8f),
        outlineVariant = if (dark) up(0.14f) else up(0.09f),
        inverseSurface = ink,
        inverseOnSurface = ground,
    )
}

/** The dock, a step above the cards, for [skin]; null keeps the classic one. */
internal fun dockFor(skin: Skin, dark: Boolean): Pair<Color, Color>? {
    val c = (if (dark) Night else Day)[skin] ?: return null
    val ground = Color(c.ground)
    val ink = Color(c.ink)
    return if (dark) lerp(ground, Color.White, 0.13f) to ink.copy(alpha = 0.14f) else Color.White to lerp(ground, ink, 0.12f)
}

/**
 * What a feed stands on in [skin]: the feed is dark in any theme, so it's the skin's night ground;
 * black for the classic look, as a reel's is.
 */
fun feedGround(skin: Skin): Color = Night[skin]?.let { Color(it.ground) } ?: Color.Black

/** Chalk Olaf's inks on [skin]: his chalk, carrot, twigs, and the skin's own colour for what he has on. */
class OlafInks(val chalk: Color, val carrot: Color, val twig: Color, val accent: Color)

fun olafInks(skin: Skin, dark: Boolean): OlafInks {
    val carrot = if (dark) Color(0xFFE59A73) else Color(0xFFD9774E)
    val twig = if (dark) Color(0xFFB89478) else Color(0xFF8A6448)
    val c = (if (dark) Night else Day)[skin]
        ?: return OlafInks(if (dark) Color(0xFFEDE6DC) else Color(0xFF5E544C), carrot, twig, if (dark) Color(0xFFFF8A63) else Color(0xFFE8673D))
    // Chalk a touch softer than the text; the accent as the skin's action colour, deepened on
    // light grounds so a pale colour still reads as a line.
    val chalk = lerp(Color(c.ink), Color(c.ground), if (dark) 0.06f else 0.25f)
    val accent = if (dark) Color(c.primary) else lerp(Color(c.primary), Color(c.ink), 0.2f)
    return OlafInks(chalk, carrot, twig, accent)
}
