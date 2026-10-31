package dev.olaf.paperzero.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.olaf.paperzero.LocalSettings
import dev.olaf.paperzero.R
import dev.olaf.paperzero.core.data.Subject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A picture from the pack, decoded off the main thread ([sample] times smaller, for a tile); null until it's ready. */
@Composable
fun rememberAssetImage(path: String?, sample: Int = 1): State<ImageBitmap?> {
    val assets = LocalContext.current.assets
    return produceState<ImageBitmap?>(null, path, sample) {
        value = path?.let {
            withContext(Dispatchers.IO) {
                runCatching {
                    val options = BitmapFactory.Options().apply { inSampleSize = sample }
                    assets.open(it).use { s -> BitmapFactory.decodeStream(s, null, options)?.asImageBitmap() }
                }.getOrNull()
            }
        }
    }
}

/**
 * How a question's picture is shown: as printed, black on white; or, with dark pictures on in the
 * dark theme, white on black, so a dark screen doesn't flash white with every question.
 */
class Picture(val ground: Color, val ink: Color, val filter: ColorFilter?)

private val Inverted = ColorFilter.colorMatrix(
    ColorMatrix(floatArrayOf(-1f, 0f, 0f, 0f, 255f, 0f, -1f, 0f, 0f, 255f, 0f, 0f, -1f, 0f, 255f, 0f, 0f, 0f, 1f, 0f)),
)

@Composable
fun picture(): Picture {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return if (dark && LocalSettings.current.darkPictures) Picture(Color.Black, Color.White, Inverted) else Picture(Color.White, Color.Black, null)
}

val Subject.icon: ImageVector
    get() = when (this) {
        Subject.Biology -> Icons.Rounded.Eco
        Subject.Chemistry -> Icons.Rounded.Science
        Subject.Physics -> Icons.Rounded.Bolt
    }

/** The bar over a screen: back, its title and a line under it, and anything at the end. */
@Composable
fun TopBar(title: String, subtitle: String?, onBack: () -> Unit, end: @Composable () -> Unit = {}) {
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
        }
        Column(Modifier.weight(1f).padding(start = 4.dp), verticalArrangement = Arrangement.Center) {
            Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
        end()
    }
}

/** "1 h 05 min", "12 min", "45 s". */
fun duration(ms: Long): String {
    val s = ms / 1000
    return when {
        s >= 3600 -> "%d h %02d min".format(s / 3600, s / 60 % 60)
        s >= 60 -> "%d min".format(s / 60)
        else -> "%d s".format(s)
    }
}
