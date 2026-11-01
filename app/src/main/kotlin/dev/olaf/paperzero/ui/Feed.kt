package dev.olaf.paperzero.ui

import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.rounded.OutlinedFlag
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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

/** A page of the feed: a question (the [index]th of the feed's), or a meme between them. */
private sealed interface Page {
    val key: String

    data class Ask(val index: Int, val question: Question) : Page {
        override val key get() = "q$index/${question.key}"
    }

    data class Fun(val number: Int, val meme: Meme) : Page {
        override val key get() = "m$number"
    }
}

/** The feed's ink: white on its black, as a phone's reels are. */
private val Ink = Color.White
private val InkSoft = Color.White.copy(alpha = 0.72f)
private val Glass = Color.White.copy(alpha = 0.14f)

/**
 * Questions as a feed, one to a screen, swiped up for the next, on black: the question's picture,
 * where it's from and its topics, a rail of heart, comments, working and share, and A to D to try
 * it. Double-tap a question to like it. A subject's feed never ends: once every question has gone
 * by they come round again in a new order, ones not yet tried first.
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
    var reporting by remember { mutableStateOf<Pair<Question, String>?>(null) }
    var tried by remember { mutableIntStateOf(0) }
    var right by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Memes between the questions, when they're turned on: about one in [MemeFrequency.every],
    // never on a beat. memeAt: after which question, which meme; shown: those of them in the feed,
    // a meme only ever put in ahead of the question on screen, so the feed never shifts under you.
    val settings = LocalSettings.current
    val memes = remember { mutableStateListOf<Meme>() }
    val memeAt = remember { mutableStateMapOf<Int, Int>() }
    val shown = remember { mutableStateMapOf<Int, Int>() }
    var nextMeme by remember { mutableIntStateOf(-1) }
    var misses by remember { mutableIntStateOf(0) }

    val pages by remember {
        derivedStateOf {
            buildList {
                feed.forEachIndexed { i, q ->
                    add(Page.Ask(i, q))
                    if (settings.memes) shown[i]?.let { k -> memes.getOrNull(k)?.let { add(Page.Fun(k, it)) } }
                }
            }
        }
    }
    val pager = rememberPagerState { pages.size }

    LaunchedEffect(source) {
        sessionId = progresses.startScroll(title)
        when (source) {
            is FeedSource.Of -> feed += progresses.feed(source.subject, progresses.progress.first().tried, source.topic)
            is FeedSource.Saved -> {
                feed += source.keys.mapNotNull { pack.question(it) }
                pager.scrollToPage(source.start.coerceIn(0, (feed.size - 1).coerceAtLeast(0)))
            }
        }
    }
    // Near the end of a subject's feed, its questions come round again.
    if (source is FeedSource.Of) {
        LaunchedEffect(pager) {
            snapshotFlow { pager.currentPage }.collect { page ->
                if (feed.isNotEmpty() && page > pages.size - 4) feed += progresses.feed(source.subject, emptySet(), source.topic)
            }
        }
    }
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
        // Where the feed is, in questions: memes are fetched only for the slots a little ahead of it.
        val here by remember {
            derivedStateOf {
                when (val page = pages.getOrNull(pager.currentPage)) {
                    is Page.Ask -> page.index
                    is Page.Fun -> shown.entries.firstOrNull { it.value == page.number }?.key ?: 0
                    null -> 0
                }
            }
        }
        LaunchedEffect(memeAt.size, memes.size, misses, here) {
            // Each meme, as it comes, to the next free slot ahead (one passed while it loaded is
            // skipped, not the meme).
            var free = shown.size
            memeAt.keys.sorted().forEach { at -> if (at > here && at !in shown && free < memes.size) shown[at] = free++ }
            val wanted = shown.size + memeAt.keys.count { it > here && it <= here + MEME_AHEAD && it !in shown } + 1
            if (memes.size >= wanted) return@LaunchedEffect
            // Not there (offline, or blocked here): try again a little later, then less often; the
            // feed goes on without them meanwhile.
            if (misses > 0) delay((MEME_RETRY_MS shl (misses - 1).coerceAtMost(2)).coerceAtMost(MEME_RETRY_MAX_MS))
            val got = Memes.fetch((source as? FeedSource.Of)?.subject, memes.map { it.link }.toSet())
            if (got.isEmpty()) misses++ else { misses = 0; memes += got }
        }
    }

    // Time studied, for the stats: a second at a time while the feed is open and in use, to the
    // science of the question on screen. Left alone for IDLE_MS (no swipe, no answer), it stops.
    var touched by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(pager) {
        snapshotFlow { pager.currentPage to pager.currentPageOffsetFraction }.collect { touched = System.currentTimeMillis() }
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
    // How long this sitting has been scrolling, shown at the top: every second the feed is on
    // screen, idle or not, so it's honest about the time gone.
    var scrolled by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(running) {
        while (running) {
            delay(1000)
            scrolled++
            if (System.currentTimeMillis() - touched > IDLE_MS) continue
            (pages.getOrNull(pager.currentPage) as? Page.Ask)?.let { pack.subject(it.question) }?.let { progresses.study(it, 1000) }
        }
    }

    CompositionLocalProvider(LocalContentColor provides Ink) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            if (pages.isEmpty()) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else {
                VerticalPager(
                    state = pager,
                    key = { pages[it].key },
                    beyondViewportPageCount = 1,
                    // A short swipe is enough for the next, as with reels: a quarter of the way.
                    flingBehavior = PagerDefaults.flingBehavior(state = pager, snapPositionalThreshold = 0.25f),
                    modifier = Modifier.fillMaxSize(),
                ) { p ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                        when (val page = pages[p]) {
                            is Page.Ask -> {
                                val question = page.question
                                val subject = pack.paper(question.paperId)?.subject
                                val from = pack.paper(question.paperId)?.let { progresses.title(it) } ?: ""
                                // A saved feed mixes the sciences: each question in its own colours.
                                SubjectTheme(if (source is FeedSource.Saved) subject else null, fill = false) {
                                    QuestionPage(
                                        question = question,
                                        source = from,
                                        subject = subject,
                                        topics = pack.topicNames(question).zip(question.topics),
                                        mine = chosen[page.index],
                                        liked = question.key in liked,
                                        comments = comments[question.key].orEmpty(),
                                        onZoom = { zoomed = it },
                                        onLike = { like -> scope.launch { progresses.setLiked(question.key, like) } },
                                        onComments = { commenting = question },
                                        onShare = { scope.launch { shareQuestion(context, question, from) } },
                                        onReport = { reporting = question to from },
                                        onTopic = { topic -> subject?.let { onTopic(it, topic) } },
                                        onChoose = { letter ->
                                            chosen[page.index] = letter
                                            tried++
                                            touched = System.currentTimeMillis()
                                            if (letter == question.answer) right++
                                            scope.launch { progresses.answer(sessionId, question, letter) }
                                        },
                                        onUndo = {
                                            chosen.remove(page.index)?.let { was ->
                                                tried--
                                                if (was == question.answer) right--
                                                scope.launch { progresses.undo(sessionId, question) }
                                            }
                                        },
                                    )
                                }
                            }
                            is Page.Fun -> MemePage(page.meme, onShare = { shareMeme(context, page.meme) })
                        }
                    }
                }
            }
            // Memes turned on but not loading: say so, rather than leave it a mystery.
            if (settings.memes && misses >= MEME_TELL_AFTER) {
                Surface(
                    shape = CircleShape,
                    color = Glass,
                    contentColor = Ink,
                    modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 60.dp, start = 24.dp, end = 24.dp),
                ) {
                    Text(stringResource(R.string.memes_unreachable), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
                }
            }
            // Over it all, faded into the picture: close, the feed's name, how it's going.
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)))
                    .statusBarsPadding()
                    .padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.close)) }
                Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                ScrollTimer(scrolled)
                if (tried > 0) {
                    Text(stringResource(R.string.scroll_score, right, tried), style = MaterialTheme.typography.titleMedium, color = InkSoft, modifier = Modifier.padding(end = 12.dp))
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
    reporting?.let { (q, from) -> ReportSheet(q, from) { reporting = null } }
    zoomed?.let { path -> Zoomed(path) { zoomed = null } }
}

/** Memes are fetched for the slots up to this many questions ahead of the one on screen. */
private const val MEME_AHEAD = 15
/** After memes fail to load, the wait before trying again: doubling, up to the most. */
private const val MEME_RETRY_MS = 10_000L
private const val MEME_RETRY_MAX_MS = 60_000L
/** Failed this many times running, the feed says so (once memes are turned on). */
private const val MEME_TELL_AFTER = 2

/** Left this long without a swipe or an answer, a feed stops counting the time. */
private const val IDLE_MS = 120_000L

/** Room left over the picture for the bar faded over the feed's top. */
private val TOP_ROOM = 56.dp

/** One question, a screen to itself. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuestionPage(
    question: Question,
    source: String,
    subject: Subject?,
    /** Its topics' names, and their places in its science's list. */
    topics: List<Pair<String, Int>>,
    mine: Char?,
    liked: Boolean,
    comments: List<CommentEntity>,
    onZoom: (String) -> Unit,
    onLike: (Boolean) -> Unit,
    onComments: () -> Unit,
    onShare: () -> Unit,
    /** Something's wrong with it: the answer, the picture, the topic. */
    onReport: () -> Unit,
    onTopic: (Int) -> Unit,
    onChoose: (Char) -> Unit,
    /** Takes back the answer given: a misclick, not to count. */
    onUndo: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val success = LocalPaperExtras.current.success
    val image by rememberAssetImage(question.image)
    val picture = picture()
    // A double tap likes it (never unlikes), with a heart that blooms and fades.
    val heart = remember { Animatable(0f) }
    var hearts by remember { mutableIntStateOf(0) }
    LaunchedEffect(hearts) {
        if (hearts == 0) return@LaunchedEffect
        heart.snapTo(0f)
        heart.animateTo(1f, tween(700))
    }

    Column(Modifier.fillMaxSize().widthIn(max = 640.dp).statusBarsPadding().navigationBarsPadding().padding(top = TOP_ROOM)) {
        // The question, as printed, as big as fits; tap to look closer.
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(question.key, liked) {
                    detectTapGestures(
                        onTap = { onZoom(question.image) },
                        onDoubleTap = {
                            if (!liked) onLike(true)
                            hearts++
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            val shown = image
            if (shown != null) {
                Image(
                    shown,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    colorFilter = picture.filter,
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .background(picture.ground, MaterialTheme.shapes.medium)
                        .padding(8.dp),
                )
            }
            val t = heart.value
            if (t > 0f && t < 1f) {
                Icon(
                    Icons.Rounded.Favorite,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(120.dp).graphicsLayer {
                        val grow = (t * 4).coerceAtMost(1f)
                        scaleX = 0.5f + 0.6f * grow
                        scaleY = 0.5f + 0.6f * grow
                        alpha = if (t < 0.6f) 1f else (1f - t) / 0.4f
                    },
                )
            }
        }
        // Who posted it and its tags, beside the rail of actions, as a reel has them.
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = CircleShape, color = colors.primaryContainer, contentColor = colors.onPrimaryContainer, modifier = Modifier.size(36.dp)) {
                        Box(contentAlignment = Alignment.Center) { subject?.let { Icon(it.icon, contentDescription = null, modifier = Modifier.size(20.dp)) } }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(source, style = MaterialTheme.typography.titleSmall)
                        Text(
                            listOfNotNull(subject?.let { subjectName(it) }, stringResource(R.string.question_label, question.number)).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = InkSoft,
                        )
                    }
                }
                if (topics.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        topics.forEach { (name, topic) ->
                            Surface(onClick = { onTopic(topic) }, shape = CircleShape, color = Glass, contentColor = Ink) {
                                Text("# $name", style = MaterialTheme.typography.labelLarge, maxLines = 1, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
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
                        Spacer(Modifier.width(10.dp))
                        Text(
                            stringResource(R.string.undo_misclick),
                            style = MaterialTheme.typography.labelLarge,
                            color = InkSoft,
                            modifier = Modifier.clip(CircleShape).clickable(onClick = onUndo).padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
                comments.lastOrNull()?.let { last ->
                    Text(last.text, style = MaterialTheme.typography.bodyMedium, color = InkSoft, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.clickable(onClick = onComments))
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                RailAction(
                    if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    stringResource(if (liked) R.string.unlike else R.string.like),
                    stringResource(R.string.like),
                    tint = if (liked) colors.primary else Ink,
                ) { onLike(!liked) }
                RailAction(Icons.Rounded.ChatBubble, stringResource(R.string.comments), if (comments.isEmpty()) stringResource(R.string.comment) else "${comments.size}", onClick = onComments)
                val explain = question.explain
                if (mine != null && explain != null) {
                    RailAction(Icons.Rounded.Lightbulb, stringResource(R.string.working), stringResource(R.string.working_short)) { onZoom(explain) }
                }
                RailAction(Icons.Rounded.Share, stringResource(R.string.share), stringResource(R.string.share), onClick = onShare)
                RailAction(Icons.Rounded.OutlinedFlag, stringResource(R.string.report_title), stringResource(R.string.report), onClick = onReport)
            }
        }
        // Try it: A to D, marked at once.
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf('A', 'B', 'C', 'D').forEach { letter ->
                val (fill, ink) = when {
                    mine != null && letter == question.answer -> success to Color.Black
                    mine != null && letter == mine -> colors.error to colors.onError
                    mine != null -> Glass to InkSoft.copy(alpha = 0.4f)
                    else -> Glass to Ink
                }
                Surface(
                    onClick = { if (mine == null) onChoose(letter) },
                    shape = MaterialTheme.shapes.medium,
                    color = fill,
                    contentColor = ink,
                    // A splash round it, if it's the one chosen and it's right.
                    modifier = Modifier.weight(1f).height(48.dp).splash(mine == letter && letter == question.answer, success),
                ) {
                    Box(contentAlignment = Alignment.Center) { Text(letter.toString(), style = MaterialTheme.typography.titleMedium) }
                }
            }
        }
    }
}

/** An action on the feed's rail: an icon over a word or a count. */
@Composable
private fun RailAction(icon: ImageVector, description: String, label: String, tint: Color = Ink, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.widthIn(min = 64.dp).clickable(onClick = onClick).padding(vertical = 6.dp),
    ) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(30.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = Ink)
    }
}

/** A meme between the questions, a screen to itself: the picture, its title, where it's from. */
@Composable
private fun MemePage(meme: Meme, onShare: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().widthIn(max = 640.dp).statusBarsPadding().navigationBarsPadding().padding(top = TOP_ROOM)) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            // As wide as the screen (as tall as it, if it's tall), however small the picture came.
            Image(meme.image, contentDescription = meme.title, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        }
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 24.dp), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = CircleShape, color = colors.tertiaryContainer, contentColor = colors.onTertiaryContainer, modifier = Modifier.size(36.dp)) {
                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.EmojiEmotions, contentDescription = null, modifier = Modifier.size(20.dp)) }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(meme.source, style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(R.string.meme_label), style = MaterialTheme.typography.bodySmall, color = InkSoft)
                    }
                }
                if (meme.title.isNotBlank()) Text(meme.title, style = MaterialTheme.typography.bodyMedium)
            }
            RailAction(Icons.Rounded.Share, stringResource(R.string.share), stringResource(R.string.share), onClick = onShare)
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

/** How long this sitting has been scrolling: a small glass pill, "4:05", or "1:04:05" past the hour. */
@Composable
private fun ScrollTimer(seconds: Int) {
    val h = seconds / 3600
    val m = seconds / 60 % 60
    val sec = seconds % 60
    val text = if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
    val spoken = stringResource(R.string.scroll_timer, duration(seconds * 1000L))
    Surface(
        shape = CircleShape,
        color = Glass,
        contentColor = Ink,
        modifier = Modifier.padding(end = 8.dp).clearAndSetSemantics { contentDescription = spoken },
    ) {
        Row(Modifier.padding(start = 8.dp, end = 10.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Timer, contentDescription = null, tint = InkSoft, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(text, style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"))
        }
    }
}
