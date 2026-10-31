package dev.olaf.paperzero.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.olaf.paperzero.LocalSettings
import dev.olaf.paperzero.Meme
import dev.olaf.paperzero.Memes
import dev.olaf.paperzero.R
import dev.olaf.paperzero.core.data.CommentEntity
import dev.olaf.paperzero.core.data.Pack
import dev.olaf.paperzero.core.data.Progresses
import dev.olaf.paperzero.core.data.Question
import dev.olaf.paperzero.core.data.Subject
import dev.olaf.paperzero.core.design.LocalPaperExtras
import dev.olaf.paperzero.core.design.ZoomBox
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import kotlin.random.Random

/**
 * What a feed shows: a subject's questions without end (or only its [Of.topic]'s), or a list
 * (liked, commented) from any of them.
 */
sealed interface FeedSource {
    data class Of(val subject: Subject, val topic: Int? = null) : FeedSource
    data class Saved(val title: String, val keys: List<String>, val start: Int) : FeedSource
}

/**
 * Questions as a feed of posts, one under another: each with where it's from, its picture, a
 * heart and comments, and A to D to try it. A subject's feed never ends: once every question has
 * gone by they come round again in a new order, ones not yet tried first.
 */
@Composable
fun FeedScreen(pack: Pack, progresses: Progresses, source: FeedSource, onClose: () -> Unit, onTopic: (Subject, Int) -> Unit) {
    val title = when (source) {
        is FeedSource.Of -> source.topic?.let { pack.topics[source.subject]?.getOrNull(it) } ?: subjectName(source.subject)
        is FeedSource.Saved -> source.title
    }
    var sessionId by remember { mutableLongStateOf(0L) }
    val feed = remember { mutableStateListOf<Question>() }
    val chosen = remember { mutableStateMapOf<Int, Char>() }
    val liked by progresses.liked.collectAsState(initial = emptyList())
    val comments by progresses.comments.collectAsState(initial = emptyMap())
    var zoomed by remember { mutableStateOf<String?>(null) }
    var commenting by remember { mutableStateOf<Question?>(null) }
    var tried by remember { mutableIntStateOf(0) }
    var right by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val list = rememberLazyListState()

    LaunchedEffect(source) {
        sessionId = progresses.startScroll(title)
        when (source) {
            is FeedSource.Of -> feed += progresses.feed(source.subject, progresses.progress.first().tried, source.topic)
            is FeedSource.Saved -> {
                feed += source.keys.mapNotNull { pack.question(it) }
                list.scrollToItem(source.start.coerceIn(0, (feed.size - 1).coerceAtLeast(0)))
            }
        }
    }
    // Near the end of a subject's feed, its questions come round again.
    if (source is FeedSource.Of) {
        LaunchedEffect(list) {
            snapshotFlow { list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }.collect { last ->
                if (feed.isNotEmpty() && last > feed.size - 4) feed += progresses.feed(source.subject, emptySet(), source.topic)
            }
        }
    }

    // Memes between the posts, when they're turned on: about one in [MemeFrequency.every] posts,
    // never on a beat. memeAt: after which post, which meme.
    val settings = LocalSettings.current
    val memes = remember { mutableStateListOf<Meme>() }
    val memeAt = remember { mutableStateMapOf<Int, Int>() }
    var nextMeme by remember { mutableIntStateOf(-1) }
    var memesGone by remember { mutableStateOf(false) }
    if (settings.memes) {
        LaunchedEffect(feed.size, settings.memeFrequency) {
            val every = settings.memeFrequency.every
            fun gap() = Random.nextInt((every + 1) / 2, every * 3 / 2 + 1).coerceAtLeast(2)
            if (nextMeme < 0) nextMeme = gap()
            while (nextMeme < feed.size) {
                memeAt[nextMeme] = memeAt.size
                nextMeme += gap()
            }
        }
        LaunchedEffect(memeAt.size, memes.size) {
            // A few ahead of the feed; if the internet isn't there, the feed goes on without them.
            if (memesGone || memes.size >= memeAt.size + 2) return@LaunchedEffect
            val got = Memes.fetch((source as? FeedSource.Of)?.subject, memes.map { it.link }.toSet())
            if (got.isEmpty()) memesGone = true else memes += got
        }
    }

    // Time studied, for the stats: a second at a time while the feed is open and in use, to the
    // science of the post on screen. Left alone for IDLE_MS (no scrolling, no answer), it stops.
    var touched by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(list) {
        snapshotFlow { list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset }.collect { touched = System.currentTimeMillis() }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var running by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) running = true
            if (e == Lifecycle.Event.ON_PAUSE) running = false
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(running) {
        while (running) {
            delay(1000)
            if (System.currentTimeMillis() - touched > IDLE_MS) continue
            feed.getOrNull(list.firstVisibleItemIndex)?.let { pack.subject(it) }?.let { progresses.study(it, 1000) }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.close)) }
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (tried > 0) {
                Text(
                    stringResource(R.string.scroll_score, right, tried),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 12.dp),
                )
            }
        }
        if (feed.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            LazyColumn(
                state = list,
                modifier = Modifier.fillMaxSize().align(Alignment.CenterHorizontally).widthIn(max = 640.dp),
                contentPadding = PaddingValues(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(feed, key = { i, q -> "$i/${q.key}" }) { i, question ->
                    val subject = pack.paper(question.paperId)?.subject
                    // A saved feed mixes the sciences: each post in its own colours.
                    SubjectTheme(if (source is FeedSource.Saved) subject else null, fill = false) {
                    Column {
                    Post(
                        question = question,
                        source = pack.paper(question.paperId)?.let { progresses.title(it) } ?: "",
                        subject = subject,
                        topics = pack.topicNames(question).zip(question.topics),
                        mine = chosen[i],
                        liked = question.key in liked,
                        comments = comments[question.key].orEmpty(),
                        onZoom = { zoomed = it },
                        onLike = { scope.launch { progresses.setLiked(question.key, question.key !in liked) } },
                        onComments = { commenting = question },
                        onTopic = { topic -> subject?.let { onTopic(it, topic) } },
                        onChoose = { letter ->
                            chosen[i] = letter
                            tried++
                            touched = System.currentTimeMillis()
                            if (letter == question.answer) right++
                            scope.launch { progresses.answer(sessionId, question, letter) }
                        },
                    )
                    if (settings.memes) memeAt[i]?.let { memes.getOrNull(it) }?.let { MemePost(it) }
                    }
                    }
                }
            }
        }
    }

    commenting?.let { q ->
        CommentsSheet(
            comments = comments[q.key].orEmpty(),
            onSend = { text -> scope.launch { progresses.comment(q.key, text) } },
            onDelete = { id -> scope.launch { progresses.deleteComment(id) } },
            onDismiss = { commenting = null },
        )
    }
    zoomed?.let { path -> Zoomed(path) { zoomed = null } }
}

/** Left this long without a scroll or an answer, a feed stops counting the time. */
private const val IDLE_MS = 120_000L

/** One question as a post. */
@Composable
private fun Post(
    question: Question,
    source: String,
    subject: Subject?,
    /** Its topics' names, and their places in its science's list. */
    topics: List<Pair<String, Int>>,
    mine: Char?,
    liked: Boolean,
    comments: List<CommentEntity>,
    onZoom: (String) -> Unit,
    onLike: () -> Unit,
    onComments: () -> Unit,
    onTopic: (Int) -> Unit,
    onChoose: (Char) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val success = LocalPaperExtras.current.success
    val image by rememberAssetImage(question.image)
    val picture = picture()
    Column(Modifier.fillMaxWidth()) {
        // Who posted it: the paper, as an account would be.
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = colors.primaryContainer, contentColor = colors.onPrimaryContainer, modifier = Modifier.size(36.dp)) {
                Box(contentAlignment = Alignment.Center) { subject?.let { Icon(it.icon, contentDescription = null, modifier = Modifier.size(20.dp)) } }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(source, style = MaterialTheme.typography.titleSmall)
                Text(
                    listOfNotNull(subject?.let { subjectName(it) }, stringResource(R.string.question_label, question.number)).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }
        // The question, edge to edge, on white as printed.
        Box(
            Modifier
                .fillMaxWidth()
                .background(picture.ground)
                .clickable { onZoom(question.image) },
        ) {
            val shown = image
            if (shown == null) {
                Box(Modifier.fillMaxWidth().height(260.dp))
            } else {
                Image(
                    shown,
                    contentDescription = null,
                    contentScale = ContentScale.FillWidth,
                    colorFilter = picture.filter,
                    modifier = Modifier.fillMaxWidth().aspectRatio(shown.width.toFloat() / shown.height).padding(6.dp),
                )
            }
        }
        // Heart and comments, as under any post.
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onLike) {
                Icon(
                    if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = stringResource(if (liked) R.string.unlike else R.string.like),
                    tint = if (liked) colors.primary else colors.onSurface,
                )
            }
            IconButton(onClick = onComments) {
                Icon(Icons.Rounded.ChatBubbleOutline, contentDescription = stringResource(R.string.comments))
            }
            Spacer(Modifier.weight(1f))
            val explain = question.explain
            if (mine != null && explain != null) {
                IconButton(onClick = { onZoom(explain) }) {
                    Icon(Icons.Rounded.Lightbulb, contentDescription = stringResource(R.string.working))
                }
            }
        }
        // Try it: A to D, marked at once.
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf('A', 'B', 'C', 'D').forEach { letter ->
                val (fill, ink) = when {
                    mine != null && letter == question.answer -> success to colors.surface
                    mine != null && letter == mine -> colors.error to colors.onError
                    mine != null -> colors.surfaceContainer to colors.onSurfaceVariant.copy(alpha = 0.5f)
                    else -> colors.surfaceContainerHigh to colors.onSurface
                }
                Surface(
                    onClick = { if (mine == null) onChoose(letter) },
                    shape = MaterialTheme.shapes.medium,
                    color = fill,
                    contentColor = ink,
                    modifier = Modifier.weight(1f).height(44.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) { Text(letter.toString(), style = MaterialTheme.typography.titleMedium) }
                }
            }
        }
        // The caption: its topics as tags, how it went, and a comment or two.
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (topics.isNotEmpty()) {
                Row(Modifier.padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    topics.forEach { (name, topic) ->
                        Surface(
                            onClick = { onTopic(topic) },
                            shape = CircleShape,
                            color = colors.primaryContainer,
                            contentColor = colors.onPrimaryContainer,
                        ) {
                            Text(
                                "# $name",
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
            }
            if (mine != null) {
                val ok = mine == question.answer
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (ok) Icons.Rounded.Check else Icons.Rounded.Close, contentDescription = null, tint = if (ok) success else colors.error, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (ok) stringResource(R.string.right) else stringResource(R.string.wrong_answer, question.answer), style = MaterialTheme.typography.bodyMedium)
                }
            }
            comments.lastOrNull()?.let { last ->
                Text(last.text, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
            }
            if (comments.size > 1) {
                Text(
                    pluralStringResource(R.plurals.view_comments, comments.size, comments.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.clickable(onClick = onComments),
                )
            }
        }
    }
}

/** A meme between the questions, as a post: where it's from, the picture, its title. */
@Composable
private fun MemePost(meme: Meme) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = colors.tertiaryContainer, contentColor = colors.onTertiaryContainer, modifier = Modifier.size(36.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.EmojiEmotions, contentDescription = null, modifier = Modifier.size(20.dp)) }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(meme.source, style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.meme_label), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
        }
        Image(
            meme.image,
            contentDescription = meme.title,
            contentScale = ContentScale.FillWidth,
            modifier = Modifier.fillMaxWidth().aspectRatio(meme.image.width.toFloat() / meme.image.height),
        )
        if (meme.title.isNotBlank()) {
            Text(meme.title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        }
    }
}

/** A question's comments, newest at the foot, and a field to add one. They're for you alone. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentsSheet(comments: List<CommentEntity>, onSend: (String) -> Unit, onDelete: (Long) -> Unit, onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    var draft by remember { mutableStateOf("") }
    val format = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surfaceContainer,
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 16.dp)) {
            Text(stringResource(R.string.comments), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 4.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 12.dp)) {
                Icon(Icons.Rounded.Lock, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.comments_private), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            if (comments.isEmpty()) {
                Text(
                    stringResource(R.string.comments_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 12.dp),
                )
            }
            comments.forEach { c ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f).padding(start = 4.dp)) {
                        Text(c.text, style = MaterialTheme.typography.bodyLarge)
                        Text(format.format(Date(c.at)), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    }
                    IconButton(onClick = { onDelete(c.id) }) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = stringResource(R.string.delete_comment), tint = colors.onSurfaceVariant)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                TextField(
                    value = draft,
                    onValueChange = { draft = it },
                    placeholder = { Text(stringResource(R.string.comment_hint)) },
                    shape = MaterialTheme.shapes.large,
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = {
                    onSend(draft)
                    draft = ""
                }, enabled = draft.isNotBlank()) {
                    Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = stringResource(R.string.send), tint = if (draft.isNotBlank()) colors.primary else colors.onSurfaceVariant)
                }
            }
        }
    }
}

/** A picture full screen, to pinch closer. */
@Composable
fun Zoomed(path: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val picture = picture()
        Box(Modifier.fillMaxSize().background(picture.ground)) {
            ZoomBox(Modifier.fillMaxSize()) {
                val image by rememberAssetImage(path)
                image?.let { Image(it, contentDescription = null, contentScale = ContentScale.Fit, colorFilter = picture.filter, modifier = Modifier.fillMaxSize().align(Alignment.Center)) }
            }
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp)) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.close), tint = picture.ink)
            }
        }
    }
}
