package dev.olaf.paperzero.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.olaf.paperzero.R
import dev.olaf.paperzero.core.data.Pack
import dev.olaf.paperzero.core.data.Progresses
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/** Liked, or commented on. */
enum class SavedTab { Liked, Commented }

fun Progresses.keys(tab: SavedTab): Flow<List<String>> = if (tab == SavedTab.Liked) liked else commented

/** The questions liked or commented on, as a grid of their tops, to open in a feed. */
@Composable
fun SavedScreen(pack: Pack, progresses: Progresses, start: SavedTab, onBack: () -> Unit, onOpen: (SavedTab, Int) -> Unit) {
    var tab by rememberSaveable { mutableStateOf(start) }
    val keys by progresses.keys(tab).collectAsState(initial = null)
    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.saved), null, onBack)
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(stringResource(R.string.liked), tab == SavedTab.Liked) { tab = SavedTab.Liked }
            Chip(stringResource(R.string.commented), tab == SavedTab.Commented) { tab = SavedTab.Commented }
        }
        val list = keys?.filter { pack.question(it) != null }
        when {
            list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            list.isEmpty() -> Text(
                stringResource(if (tab == SavedTab.Liked) R.string.liked_none else R.string.commented_none),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(20.dp),
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize().align(Alignment.CenterHorizontally).widthIn(max = 720.dp).padding(top = 12.dp),
                contentPadding = PaddingValues(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                itemsIndexed(list, key = { _, k -> k }) { i, key ->
                    Tile(pack.question(key)!!.image, Modifier.clickable { onOpen(tab, i) })
                }
            }
        }
    }
}

/** A question's top, square, as a grid's tile. */
@Composable
fun Tile(path: String, modifier: Modifier = Modifier) {
    val image by rememberAssetImage(path, sample = 2)
    Box(modifier.fillMaxWidth().aspectRatio(1f).background(Color.White)) {
        image?.let {
            Image(it, contentDescription = null, contentScale = ContentScale.Crop, alignment = Alignment.TopStart, modifier = Modifier.fillMaxSize().padding(4.dp))
        }
    }
}

/** The feed of what's in [tab], from its [start]th: the list as it was on opening, so unliking one doesn't move the feed. */
@Composable
fun SavedFeedScreen(pack: Pack, progresses: Progresses, tab: SavedTab, start: Int, onClose: () -> Unit) {
    val keys by produceState<List<String>?>(null, tab) { value = progresses.keys(tab).first().filter { pack.question(it) != null } }
    val title = stringResource(if (tab == SavedTab.Liked) R.string.liked else R.string.commented)
    keys?.let { FeedScreen(pack, progresses, FeedSource.Saved(title, it, start), onClose) }
        ?: Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}
