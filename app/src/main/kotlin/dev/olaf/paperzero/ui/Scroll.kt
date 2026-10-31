package dev.olaf.paperzero.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.olaf.paperzero.R
import dev.olaf.paperzero.core.data.Pack
import dev.olaf.paperzero.core.data.Progresses
import dev.olaf.paperzero.core.data.Question
import dev.olaf.paperzero.core.data.Subject
import dev.olaf.paperzero.core.design.LocalPaperExtras
import dev.olaf.paperzero.core.design.ZoomBox
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Doomscroll: a subject's questions one a screen, without end. Swipe up for the next; try one by
 * tapping its letter, and it's marked there and then, with its working when there is some.
 * Once every question has gone by, they come round again in a new order.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ScrollScreen(pack: Pack, progresses: Progresses, subject: Subject, onClose: () -> Unit) {
    val title = subjectName(subject)
    var sessionId by remember { mutableLongStateOf(0L) }
    val feed = remember { mutableStateListOf<Question>() }
    val chosen = remember { mutableStateMapOf<Int, Char>() }
    var zoomed by remember { mutableStateOf<String?>(null) }
    var tried by remember { mutableIntStateOf(0) }
    var right by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(subject) {
        sessionId = progresses.startScroll(subject, title)
        // Questions not yet tried come first; after a full round, any order.
        feed += progresses.feed(subject, progresses.progress.first().tried)
    }
    if (feed.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val pager = rememberPagerState { Int.MAX_VALUE }
    // Near the end of the feed, the questions come round again in a new order.
    LaunchedEffect(pager) {
        snapshotFlow { pager.currentPage }.collect { page ->
            if (page > feed.size - 5) feed += progresses.feed(subject, emptySet())
        }
    }

    Box(Modifier.fillMaxSize()) {
        VerticalPager(pager, modifier = Modifier.fillMaxSize(), beyondViewportPageCount = 1, key = { it }) { page ->
            if (page >= feed.size) return@VerticalPager
            val question = feed[page]
            ScrollPage(
                question = question,
                mine = chosen[page],
                source = pack.paper(question.paperId)?.let { progresses.title(it) + " · Q" + question.number } ?: "",
                onZoom = { zoomed = it },
                onChoose = { letter ->
                    chosen[page] = letter
                    tried++
                    if (letter == question.answer) right++
                    scope.launch { progresses.answer(sessionId, question, letter) }
                },
            )
        }
        // Over the feed: close, the subject, and how it's going this time.
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.close)) }
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (tried > 0) {
                Text(
                    stringResource(R.string.scroll_score, right, tried),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 12.dp),
                )
            }
        }
    }

    zoomed?.let { path ->
        Dialog(onDismissRequest = { zoomed = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().background(Color.White)) {
                ZoomBox(Modifier.fillMaxSize()) {
                    val image by rememberAssetImage(path)
                    image?.let { Image(it, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().align(Alignment.Center)) }
                }
                IconButton(onClick = { zoomed = null }, modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp)) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.close), tint = Color.Black)
                }
            }
        }
    }
}

/** One screen of the feed: where the question is from, its picture fitted in, and A to D. */
@Composable
private fun ScrollPage(question: Question, mine: Char?, source: String, onZoom: (String) -> Unit, onChoose: (Char) -> Unit) {
    val image by rememberAssetImage(question.image)
    val success = LocalPaperExtras.current.success
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(top = 56.dp, start = 12.dp, end = 12.dp, bottom = 12.dp),
    ) {
        Text(source, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
        Surface(
            shape = MaterialTheme.shapes.large,
            color = Color.White,
            modifier = Modifier.weight(1f).fillMaxWidth().clickable { onZoom(question.image) },
        ) {
            image?.let { Image(it, contentDescription = null, contentScale = ContentScale.Fit, alignment = Alignment.TopCenter, modifier = Modifier.fillMaxSize().padding(8.dp)) }
        }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().height(40.dp), contentAlignment = Alignment.CenterStart) {
            if (mine == null) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.align(Alignment.Center)) {
                    Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    Text(stringResource(R.string.scroll_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                val ok = mine == question.answer
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Icon(if (ok) Icons.Rounded.Check else Icons.Rounded.Close, contentDescription = null, tint = if (ok) success else MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (ok) stringResource(R.string.right) else stringResource(R.string.wrong_answer, question.answer),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    question.explain?.let { path ->
                        TextButton(onClick = { onZoom(path) }) {
                            Icon(Icons.Rounded.Lightbulb, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.working))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.align(Alignment.CenterHorizontally).widthIn(max = 520.dp)) {
            listOf('A', 'B', 'C', 'D').forEach { letter ->
                val colors = MaterialTheme.colorScheme
                val (fill, ink) = when {
                    mine != null && letter == question.answer -> success to colors.surface
                    mine != null && letter == mine -> colors.error to colors.onError
                    mine != null -> colors.surfaceContainer to colors.onSurfaceVariant.copy(alpha = 0.5f)
                    else -> colors.surfaceContainerHigh to colors.onSurface
                }
                Surface(
                    onClick = { if (mine == null) onChoose(letter) },
                    shape = MaterialTheme.shapes.large,
                    color = fill,
                    contentColor = ink,
                    modifier = Modifier.weight(1f).height(60.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) { Text(letter.toString(), style = MaterialTheme.typography.headlineSmall) }
                }
            }
        }
    }
}
