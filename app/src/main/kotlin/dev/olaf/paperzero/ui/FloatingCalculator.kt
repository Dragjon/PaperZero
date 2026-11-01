package dev.olaf.paperzero.ui

import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.olaf.paperzero.R
import kotlin.math.roundToInt

/**
 * A small calculator floating over the question, out of the way: drag it by its top anywhere on
 * the screen, shrink it to a bubble (tap the bubble to bring it back), or close it. It's a little
 * see-through, so what's under it still shows. What's typed and the last answer stay while it's
 * shrunk or the question changes. Put it last in a full-screen Box, over what it floats on; only
 * the calculator itself takes touches.
 */
@Composable
fun FloatingCalculator(onClose: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    var ans by rememberSaveable { mutableStateOf(0.0) }
    var shown by rememberSaveable { mutableStateOf<String?>(null) }
    var small by rememberSaveable { mutableStateOf(false) }
    var sci by rememberSaveable { mutableStateOf(false) }
    var x by rememberSaveable { mutableFloatStateOf(Float.NaN) }
    var y by rememberSaveable { mutableFloatStateOf(Float.NaN) }
    val density = LocalDensity.current
    val scientific = stringResource(R.string.calc_scientific)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val maxX = constraints.maxWidth.toFloat()
        val maxY = constraints.maxHeight.toFloat()
        val width = with(density) { (if (small) BUBBLE else WIDTH).toPx() }
        // At first: at the bottom right, just above the answers, clear of the question; placed
        // once its height is known.
        var height by remember { mutableIntStateOf(0) }
        LaunchedEffect(maxX, maxY, height) {
            if (x.isNaN() && height > 0) {
                x = maxX - width - with(density) { 12.dp.toPx() }
                y = (maxY - height - with(density) { 150.dp.toPx() }).coerceAtLeast(0f)
            }
        }
        fun clamp(w: Float, h: Float) {
            x = x.coerceAtMost(maxX - w).coerceAtLeast(0f)
            y = y.coerceAtMost(maxY - h).coerceAtLeast(0f)
        }
        val drag = Modifier.pointerInput(small) {
            detectDragGestures { change, d ->
                change.consume()
                x += d.x
                y += d.y
                clamp(size.width.toFloat(), with(density) { 48.dp.toPx() })
            }
        }
        val at = Modifier.offset { IntOffset(if (x.isNaN()) 0 else x.roundToInt(), if (y.isNaN()) 0 else y.roundToInt()) }
        val colors = MaterialTheme.colorScheme

        if (small) {
            Surface(
                onClick = { small = false },
                shape = CircleShape,
                color = colors.primary,
                contentColor = colors.onPrimary,
                modifier = at.then(drag).size(BUBBLE).shadow(6.dp, CircleShape),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Calculate, contentDescription = stringResource(R.string.calc_open))
                }
            }
            return@BoxWithConstraints
        }

        Surface(
            shape = MaterialTheme.shapes.large,
            color = colors.surfaceContainerHigh.copy(alpha = 0.94f),
            contentColor = colors.onSurface,
            modifier = at
                .width(WIDTH)
                .onSizeChanged { height = it.height }
                .shadow(10.dp, MaterialTheme.shapes.large)
                // Out of sight until it's placed, so it doesn't flash at the top first.
                .alpha(if (x.isNaN()) 0f else 1f),
        ) {
            Column(Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp)) {
                // The top: drag it by here; fx shows the scientific keys; shrink; close.
                Row(drag.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.DragIndicator, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.weight(1f))
                    // fx: the scientific keys, shown or tucked away.
                    Surface(
                        onClick = { sci = !sci },
                        shape = CircleShape,
                        color = if (sci) colors.secondaryContainer else Color.Transparent,
                        contentColor = if (sci) colors.onSecondaryContainer else colors.onSurfaceVariant,
                        modifier = Modifier.semantics { contentDescription = scientific },
                    ) {
                        Text("fx", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                    }
                    IconButton(onClick = { small = true }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Rounded.Remove, contentDescription = stringResource(R.string.calc_shrink), modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.close), modifier = Modifier.size(18.dp))
                    }
                }
                // What's typed, and what it comes to (as you go, faint; after =, firm).
                val live = shown ?: Calc.evaluate(text, ans)?.let { Calc.format(it) }
                Surface(shape = MaterialTheme.shapes.small, color = colors.surfaceContainerLowest.copy(alpha = 0.9f), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                        Text(
                            text.ifEmpty { " " },
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            textAlign = TextAlign.End,
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState(), reverseScrolling = true),
                        )
                        Text(
                            live ?: " ",
                            style = MaterialTheme.typography.titleLarge,
                            color = if (shown != null) colors.onSurface else colors.onSurfaceVariant.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.End,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                fun type(s: String) {
                    // After =, a number starts afresh; an operator carries on from the answer.
                    if (shown != null) {
                        text = if (s.first().isDigit() || s == "." || s == "(" || s == "π" || s.endsWith("(") || s == "√") "" else "Ans"
                        shown = null
                    }
                    text += s
                }
                val rows = buildList {
                    if (sci) {
                        add(listOf("sin(", "cos(", "tan(", "√"))
                        add(listOf("log(", "ln(", "x²", "^"))
                    }
                    add(listOf("C", "⌫", "(", ")"))
                    add(listOf("7", "8", "9", "÷"))
                    add(listOf("4", "5", "6", "×"))
                    add(listOf("1", "2", "3", "−"))
                    add(listOf("0", ".", "×10ˣ", "+"))
                    add(listOf("π", "Ans", "="))
                }
                Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    rows.forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            row.forEach { k ->
                                Key(
                                    label = if (k.length > 1 && k.endsWith("(")) k.dropLast(1) else k,
                                    modifier = Modifier.weight(if (k == "=") 2f else 1f),
                                    accent = k == "=",
                                    quiet = k in OPERATORS,
                                ) {
                                    when (k) {
                                        "C" -> { text = ""; shown = null }
                                        "⌫" -> { if (shown != null) shown = null else text = backspace(text) }
                                        "x²" -> type("²")
                                        "×10ˣ" -> type("×10^")
                                        "=" -> Calc.evaluate(text, ans)?.let { v ->
                                            ans = v
                                            shown = Calc.format(v)
                                            text = shown!!
                                        }
                                        else -> type(k)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        LaunchedEffect(sci, maxX, maxY) { clamp(width, with(density) { 120.dp.toPx() }) }
    }
}

private val WIDTH = 216.dp
private val BUBBLE = 52.dp
private val OPERATORS = setOf("÷", "×", "−", "+", "^", "C", "⌫", "(", ")")

/** [text] less what was typed last: a whole function name, "×10^" or "Ans" at once, else a character. */
private fun backspace(text: String): String {
    val whole = listOf("sin(", "cos(", "tan(", "log(", "ln(", "×10^", "Ans")
    return whole.firstOrNull { text.endsWith(it) }?.let { text.dropLast(it.length) } ?: text.dropLast(1)
}

@Composable
private fun Key(label: String, modifier: Modifier, accent: Boolean = false, quiet: Boolean = false, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = when {
            accent -> colors.primary
            quiet -> colors.secondaryContainer.copy(alpha = 0.8f)
            else -> colors.surfaceContainerLowest.copy(alpha = 0.8f)
        },
        contentColor = when {
            accent -> colors.onPrimary
            quiet -> colors.onSecondaryContainer
            else -> colors.onSurface
        },
        modifier = modifier.height(34.dp).semantics { contentDescription = spoken(label) },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.titleSmall, maxLines = 1)
        }
    }
}

/** What TalkBack says for a key that's a symbol. */
private fun spoken(label: String) = when (label) {
    "⌫" -> "Delete"
    "C" -> "Clear"
    "−" -> "Minus"
    "×" -> "Times"
    "÷" -> "Divide"
    "^" -> "To the power of"
    "√" -> "Square root"
    "x²" -> "Squared"
    "×10ˣ" -> "Times ten to the power"
    "π" -> "Pi"
    else -> label
}
