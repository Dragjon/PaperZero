package dev.olaf.paperzero.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.olaf.paperzero.R
import dev.olaf.paperzero.core.design.ChalkOlaf

/** One of Olaf's lines from Frozen, picked at random and kept while the screen is around. */
@Composable
fun rememberOlafQuote(): String {
    val quotes = stringArrayResource(R.array.olaf_quotes)
    val index = rememberSaveable { quotes.indices.random() }
    return quotes[index.coerceIn(quotes.indices)]
}

/**
 * What shows while the app starts: chalk Olaf at his desk, bobbing, over one of his lines, a
 * different one each launch.
 */
@Composable
fun StartupScreen() {
    val colors = MaterialTheme.colorScheme
    val quote = rememberOlafQuote()
    // Rises from where the system splash left him, so the two meet without a jump.
    val bob by rememberInfiniteTransition(label = "bob").animateFloat(
        initialValue = 0f,
        targetValue = -8f,
        animationSpec = infiniteRepeatable(tween(1000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bob",
    )
    BoxWithConstraints(Modifier.fillMaxSize().background(colors.background)) {
        // Dead center and this size: the same as the drawing on the system splash.
        ChalkOlaf(
            Modifier
                .align(Alignment.Center)
                .size(width = OLAF_W, height = OLAF_H)
                .graphicsLayer { translationY = bob.dp.toPx() },
        )
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = maxHeight / 2 + OLAF_H / 2 + 24.dp, start = 32.dp, end = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "“$quote”",
                style = MaterialTheme.typography.titleMedium.copy(fontStyle = FontStyle.Italic),
                color = colors.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 320.dp).semantics { heading() },
            )
            Spacer(Modifier.height(8.dp))
            Text("— Olaf", style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
        }
    }
}

/** Fits the system splash's icon circle (160dp across) with only the floor's ends cut off. */
val OLAF_W = 132.dp
val OLAF_H = 110.dp
