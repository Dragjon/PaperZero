package dev.olaf.paperzero.ui

import androidx.compose.material.icons.rounded.OutlinedFlag
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import dev.olaf.paperzero.LocalSettings
import dev.olaf.paperzero.R
import dev.olaf.paperzero.core.data.Mode
import dev.olaf.paperzero.core.data.Pack
import dev.olaf.paperzero.core.data.Progresses
import dev.olaf.paperzero.core.data.Question
import dev.olaf.paperzero.core.data.SessionEntity
import dev.olaf.paperzero.core.design.LocalPaperExtras
import dev.olaf.paperzero.core.design.PrimaryPill
import dev.olaf.paperzero.core.design.ZoomBox
import dev.olaf.paperzero.core.design.pressScale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val LETTERS = listOf('A', 'B', 'C', 'D')

/** A whole paper (or a timed set) is given this long in Exam mode, as in the real one. */
private const val EXAM_MS = 60 * 60 * 1000L

/**
 * One question at a time: its picture, and A to D under it. In Practice each answer is marked
 * at once, with the working where there is some; in Exam the paper is marked at the end, against
 * the clock. [review] shows a finished sitting, each answer against the right one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizScreen(
    pack: Pack,
    progresses: Progresses,
    sessionId: Long,
    review: Boolean = false,
    onClose: () -> Unit,
    onFinished: () -> Unit,
) {
    var session by remember { mutableStateOf<SessionEntity?>(null) }
    var questions by remember { mutableStateOf<List<Question>>(emptyList()) }
    val chosen = remember { mutableStateMapOf<String, Char>() }
    var index by rememberSaveable { mutableStateOf(-1) }
    var spent by remember { mutableLongStateOf(0L) }
    var leaving by remember { mutableStateOf(false) }
    var finishing by remember { mutableStateOf(false) }
    var grid by remember { mutableStateOf(false) }
    var zoomed by remember { mutableStateOf<String?>(null) }
    var reporting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(sessionId) {
        val s = progresses.session(sessionId) ?: return@LaunchedEffect onClose()
        questions = s.keys.split(',').mapNotNull { pack.question(it) }
        progresses.answers(sessionId).forEach { (key, a) -> chosen[key] = a.chosen[0] }
        spent = s.spentMs
        session = s
        if (index < 0) index = if (review) 0 else questions.indexOfFirst { it.key !in chosen }.let { if (it < 0) 0 else it }
    }
    val s = session
    if (s == null || questions.isEmpty() || index !in questions.indices) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val exam = s.mode == Mode.Exam.name && !review

    // The time spent answering, kept as it goes: time away from the app doesn't count.
    if (!review) {
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
                spent += 1000
                progresses.addTime(sessionId, 1000)
                questions.getOrNull(index)?.let { pack.subject(it) }?.let { progresses.study(it, 1000) }
                if (exam && spent >= EXAM_MS) {
                    progresses.finish(sessionId)
                    onFinished()
                    break
                }
            }
        }
    }

    fun finish() {
        scope.launch {
            progresses.finish(sessionId)
            onFinished()
        }
    }

    val question = questions[index]
    val mine = chosen[question.key]
    val marked = review || (!exam && mine != null)
    val last = index == questions.lastIndex
    val unanswered = questions.count { it.key !in chosen }

    BackHandler { if (review || chosen.isEmpty()) onClose() else leaving = true }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(start = 4.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { if (review || chosen.isEmpty()) onClose() else leaving = true }) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.close))
            }
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.question_of, index + 1, questions.size), style = MaterialTheme.typography.titleMedium)
                Text(s.title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            // The clock, unless it's turned off (the sitting is timed all the same).
            if (exam && LocalSettings.current.examClock) {
                val left = (EXAM_MS - spent).coerceAtLeast(0) / 1000
                Text(
                    "%d:%02d".format(left / 60, left % 60),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (left < 300) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
            IconButton(onClick = { reporting = true }) { Icon(Icons.Rounded.OutlinedFlag, contentDescription = stringResource(R.string.report_title)) }
            IconButton(onClick = { grid = true }) { Icon(Icons.Rounded.Apps, contentDescription = stringResource(R.string.all_questions)) }
        }

        // The question as printed; tap it to look closer.
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(key(question.key) { rememberScrollState() })
                .padding(horizontal = 12.dp),
        ) {
            QuestionPicture(question.image, Modifier.clickable { zoomed = question.image })
            if (marked) {
                Spacer(Modifier.height(16.dp))
                // In practice, a misclick can be taken back before it counts.
                Verdict(question, mine, onUndo = if (!review && mine != null) {
                    {
                        chosen.remove(question.key)
                        scope.launch { progresses.undo(sessionId, question) }
                    }
                } else null)
                question.explain?.let { path ->
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.working), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
                    QuestionPicture(path, Modifier.clickable { zoomed = path })
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        Column(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.align(Alignment.CenterHorizontally).widthIn(max = 520.dp)) {
                LETTERS.forEach { letter ->
                    val state = when {
                        marked && letter == question.answer -> LetterState.Right
                        marked && letter == mine -> LetterState.Wrong
                        !marked && letter == mine -> LetterState.Chosen
                        marked -> LetterState.Off
                        else -> LetterState.Open
                    }
                    // A splash when it's chosen and right, in practice (not in review).
                    LetterButton(letter, state, Modifier.weight(1f), splash = !review && !exam && mine == letter && letter == question.answer) {
                        if (review || (!exam && mine != null)) return@LetterButton
                        chosen[question.key] = letter
                        scope.launch { progresses.answer(sessionId, question, letter) }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = { index-- }, enabled = index > 0) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.previous))
                }
                Spacer(Modifier.weight(1f))
                when {
                    review && last -> PrimaryPill(Icons.Rounded.Check, stringResource(R.string.done), onFinished)
                    !review && last && (exam || mine != null) -> PrimaryPill(Icons.Rounded.Flag, stringResource(R.string.finish), {
                        if (unanswered > 0) finishing = true else finish()
                    })
                    else -> PrimaryPill(Icons.AutoMirrored.Rounded.ArrowForward, stringResource(R.string.next), { if (!last) index++ })
                }
            }
        }
    }

    if (grid) {
        ModalBottomSheet(
            onDismissRequest = { grid = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            QuestionGrid(questions, chosen, marked = review || !exam, current = index) {
                index = it
                grid = false
            }
        }
    }
    if (finishing) {
        AlertDialog(
            onDismissRequest = { finishing = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            title = { Text(stringResource(R.string.finish_early_title)) },
            text = { Text(pluralStringResource(R.plurals.finish_early_text, unanswered, unanswered)) },
            confirmButton = { TextButton(onClick = { finishing = false; finish() }) { Text(stringResource(R.string.finish)) } },
            dismissButton = { TextButton(onClick = { finishing = false }) { Text(stringResource(R.string.keep_going), color = MaterialTheme.colorScheme.onSurfaceVariant) } },
        )
    }
    if (leaving) {
        AlertDialog(
            onDismissRequest = { leaving = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            title = { Text(stringResource(R.string.leave_title)) },
            text = { Text(stringResource(R.string.leave_text)) },
            confirmButton = { TextButton(onClick = { leaving = false; onClose() }) { Text(stringResource(R.string.leave)) } },
            dismissButton = { TextButton(onClick = { leaving = false }) { Text(stringResource(R.string.keep_going), color = MaterialTheme.colorScheme.onSurfaceVariant) } },
        )
    }
    if (reporting) ReportSheet(question, pack.paper(question.paperId)?.let { progresses.title(it) } ?: s.title) { reporting = false }
    zoomed?.let { path ->
        Dialog(onDismissRequest = { zoomed = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            val picture = picture()
            Box(Modifier.fillMaxSize().background(picture.ground)) {
                ZoomBox(Modifier.fillMaxSize()) {
                    val image by rememberAssetImage(path)
                    image?.let { Image(it, contentDescription = null, contentScale = ContentScale.Fit, colorFilter = picture.filter, modifier = Modifier.fillMaxSize().align(Alignment.Center)) }
                }
                IconButton(onClick = { zoomed = null }, modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp)) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.close), tint = picture.ink)
                }
            }
        }
    }
}

/** A question's (or its working's) picture, on white as printed, as wide as there's room for. */
@Composable
private fun QuestionPicture(path: String, modifier: Modifier = Modifier) {
    val image by rememberAssetImage(path)
    val picture = picture()
    Surface(shape = MaterialTheme.shapes.large, color = picture.ground, modifier = modifier.fillMaxWidth()) {
        val shown = image
        if (shown == null) {
            Box(Modifier.fillMaxWidth().height(200.dp))
        } else {
            Image(
                shown,
                contentDescription = null,
                contentScale = ContentScale.FillWidth,
                colorFilter = picture.filter,
                modifier = Modifier.fillMaxWidth().aspectRatio(shown.width.toFloat() / shown.height).padding(8.dp),
            )
        }
    }
}

/** "Right", or "Not quite: it's C", under a marked question. */
@Composable
private fun Verdict(question: Question, mine: Char?, onUndo: (() -> Unit)? = null) {
    val right = mine == question.answer
    val success = LocalPaperExtras.current.success
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp)) {
        Icon(
            if (right) Icons.Rounded.Check else Icons.Rounded.Close,
            contentDescription = null,
            tint = if (right) success else MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            when {
                mine == null -> stringResource(R.string.not_answered) + " · " + stringResource(R.string.wrong_answer, question.answer)
                right -> stringResource(R.string.right)
                else -> stringResource(R.string.wrong_answer, question.answer)
            },
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (onUndo != null) {
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onUndo) { Text(stringResource(R.string.undo_misclick), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

private enum class LetterState { Open, Chosen, Right, Wrong, Off }

@Composable
private fun LetterButton(letter: Char, state: LetterState, modifier: Modifier = Modifier, splash: Boolean = false, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val success = LocalPaperExtras.current.success
    val interaction = remember { MutableInteractionSource() }
    val (fill, ink) = when (state) {
        LetterState.Open -> colors.surfaceContainerHigh to colors.onSurface
        LetterState.Chosen -> colors.primary to colors.onPrimary
        LetterState.Right -> success to colors.surface
        LetterState.Wrong -> colors.error to colors.onError
        LetterState.Off -> colors.surfaceContainerHigh to colors.onSurfaceVariant.copy(alpha = 0.5f)
    }
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = fill,
        contentColor = ink,
        interactionSource = interaction,
        modifier = modifier.height(64.dp).splash(splash, success).pressScale(interaction),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(letter.toString(), fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Every question as a numbered dot: answered, and (when marked) right or wrong. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuestionGrid(questions: List<Question>, chosen: Map<String, Char>, marked: Boolean, current: Int, onPick: (Int) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val success = LocalPaperExtras.current.success
    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
        Text(stringResource(R.string.all_questions), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 4.dp, bottom = 12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            questions.forEachIndexed { i, q ->
                val mine = chosen[q.key]
                val (fill, ink) = when {
                    mine == null -> colors.surfaceContainerHigh to colors.onSurfaceVariant
                    !marked -> colors.secondaryContainer to colors.onSecondaryContainer
                    mine == q.answer -> success to colors.surface
                    else -> colors.error to colors.onError
                }
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(fill)
                        .then(if (i == current) Modifier.border(2.dp, colors.onSurface, CircleShape) else Modifier)
                        .clickable { onPick(i) },
                    contentAlignment = Alignment.Center,
                ) { Text("${i + 1}", color = ink, style = MaterialTheme.typography.labelLarge) }
            }
        }
    }
}
