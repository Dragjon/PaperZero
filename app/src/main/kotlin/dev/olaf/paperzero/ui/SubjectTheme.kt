package dev.olaf.paperzero.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import dev.olaf.paperzero.core.data.Subject

/**
 * A science's own colours: its accent, the tint of its cards and ground. Biology is leaf green,
 * Chemistry violet, Physics blue; the rest of the app keeps its orange. As there, ink on the accent
 * is dark (each pair is well past AA).
 */
data class SubjectPalette(
    val primary: Color,
    val onPrimary: Color,
    val container: Color,
    val onContainer: Color,
    /** Mixed a little into the grounds, so a science's screens feel its own. */
    val tint: Color,
)

private val Day = mapOf(
    Subject.Biology to SubjectPalette(Color(0xFF45B87A), Color(0xFF06210F), Color(0xFFDDF4E6), Color(0xFF0D3320), Color(0xFF45B87A)),
    Subject.Chemistry to SubjectPalette(Color(0xFFA084F5), Color(0xFF1A0F3D), Color(0xFFEDE7FF), Color(0xFF2A1A5E), Color(0xFF8E6CF2)),
    Subject.Physics to SubjectPalette(Color(0xFF4DA2F5), Color(0xFF06203A), Color(0xFFDDEDFF), Color(0xFF0B2F55), Color(0xFF3D8FE6)),
)

private val Night = mapOf(
    Subject.Biology to SubjectPalette(Color(0xFF6FD49B), Color(0xFF06210F), Color(0xFF183A27), Color(0xFFCDF3DC), Color(0xFF6FD49B)),
    Subject.Chemistry to SubjectPalette(Color(0xFFB9A3FF), Color(0xFF1A0F3D), Color(0xFF2D2450), Color(0xFFE6DEFF), Color(0xFFB9A3FF)),
    Subject.Physics to SubjectPalette(Color(0xFF7DBBFF), Color(0xFF06203A), Color(0xFF1A3150), Color(0xFFD6E8FF), Color(0xFF7DBBFF)),
)

/** [subject]'s palette, for the theme now showing (light or dark). */
@Composable
fun palette(subject: Subject): SubjectPalette {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return (if (dark) Night else Day).getValue(subject)
}

private fun ColorScheme.with(p: SubjectPalette, dark: Boolean): ColorScheme {
    // Grounds take a breath of the science's colour: enough to tell, not enough to tire.
    fun tinted(c: Color, amount: Float) = p.tint.copy(alpha = amount).compositeOver(c)
    val ground = if (dark) 0.05f else 0.04f
    val card = if (dark) 0.08f else 0.06f
    return copy(
        primary = p.primary,
        onPrimary = p.onPrimary,
        primaryContainer = p.container,
        onPrimaryContainer = p.onContainer,
        secondaryContainer = p.container,
        onSecondaryContainer = p.onContainer,
        background = tinted(background, ground),
        surface = tinted(surface, ground),
        surfaceContainerLow = tinted(surfaceContainerLow, card),
        surfaceContainer = tinted(surfaceContainer, card),
        surfaceContainerHigh = tinted(surfaceContainerHigh, card),
        surfaceContainerHighest = tinted(surfaceContainerHighest, card),
    )
}

/**
 * [content] in [subject]'s colours; as it is when there's no one subject. A whole screen ([fill])
 * stands on the science's tinted ground; a part of one (a post, a card) only takes its colours.
 */
@Composable
fun SubjectTheme(subject: Subject?, fill: Boolean = true, content: @Composable () -> Unit) {
    if (subject == null) return content()
    val base = MaterialTheme.colorScheme
    val dark = base.background.luminance() < 0.5f
    val p = palette(subject)
    val scheme = remember(base, p, dark) { base.with(p, dark) }
    MaterialTheme(colorScheme = scheme, typography = MaterialTheme.typography, shapes = MaterialTheme.shapes) {
        CompositionLocalProvider(LocalContentColor provides scheme.onBackground) {
            if (fill) Box(Modifier.fillMaxSize().background(scheme.background)) { content() } else content()
        }
    }
}
