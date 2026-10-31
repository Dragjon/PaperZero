package dev.olaf.paperzero.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.SwipeUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.olaf.paperzero.R
import dev.olaf.paperzero.core.data.Pack
import dev.olaf.paperzero.core.data.Progresses
import dev.olaf.paperzero.core.data.Subject
import dev.olaf.paperzero.core.data.SubjectProgress
import dev.olaf.paperzero.core.design.Dock
import dev.olaf.paperzero.core.design.DockAction
import dev.olaf.paperzero.core.design.DockFade
import dev.olaf.paperzero.core.design.ChalkOlaf
import dev.olaf.paperzero.core.design.LocalPaperExtras
import dev.olaf.paperzero.core.design.PrimaryPill
import dev.olaf.paperzero.core.design.SheetAction
import dev.olaf.paperzero.core.design.pressScale

@Composable
fun subjectName(subject: Subject) = stringResource(
    when (subject) {
        Subject.Biology -> R.string.subject_biology
        Subject.Chemistry -> R.string.subject_chemistry
        Subject.Physics -> R.string.subject_physics
    },
)

/**
 * Home: each subject's feed as a story to tap into, what you've liked and commented on to find
 * again, and the papers by subject with how each is going.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    pack: Pack,
    progresses: Progresses,
    onSubject: (Subject) -> Unit,
    onMistakes: () -> Unit,
    onScroll: (Subject) -> Unit,
    onSaved: (SavedTab) -> Unit,
    onOpenSaved: (SavedTab, Int) -> Unit,
    onStats: () -> Unit,
) {
    val progress by progresses.progress.collectAsState(initial = null)
    val stats by progresses.stats.collectAsState(initial = null)
    val liked by progresses.liked.collectAsState(initial = emptyList())
    val commented by progresses.commented.collectAsState(initial = emptyList())
    val comments by progresses.comments.collectAsState(initial = emptyMap())
    val subjects = Subject.entries.filter { s -> pack.papers.any { it.subject == s } }
    var choosing by rememberSaveable { mutableStateOf(false) }
    val statusBar = WindowInsets.statusBars.asPaddingValues()
    val navBar = WindowInsets.navigationBars.asPaddingValues()
    val likedHere = liked.filter { pack.question(it) != null }
    val commentedHere = commented.filter { pack.question(it) != null }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.align(Alignment.TopCenter).widthIn(max = 720.dp).fillMaxSize(),
            contentPadding = PaddingValues(top = statusBar.calculateTopPadding() + 20.dp, bottom = navBar.calculateBottomPadding() + 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 4.dp)) {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displaySmall)
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.tagline), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    ChalkOlaf(Modifier.fillMaxWidth().height(170.dp))
                }
            }
            if (subjects.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.no_pack),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                    )
                }
            } else {
                // Stories: a subject's feed, a tap away.
                item {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        subjects.forEach { s -> SubjectTheme(s, fill = false) { Story(s) { onScroll(s) } } }
                    }
                }
                item { Box(Modifier.padding(horizontal = 16.dp)) { StatsPeek(stats, onStats) } }
                item { SectionHead(stringResource(R.string.liked), if (likedHere.isNotEmpty()) { { onSaved(SavedTab.Liked) } } else null) }
                item {
                    if (likedHere.isEmpty()) {
                        Hint(stringResource(R.string.liked_none))
                    } else {
                        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            likedHere.take(3).forEachIndexed { i, key ->
                                Tile(pack.question(key)!!.image, Modifier.weight(1f).clip(MaterialTheme.shapes.small).clickable { onOpenSaved(SavedTab.Liked, i) })
                            }
                            repeat(3 - likedHere.take(3).size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
                item { SectionHead(stringResource(R.string.commented), if (commentedHere.isNotEmpty()) { { onSaved(SavedTab.Commented) } } else null) }
                if (commentedHere.isEmpty()) {
                    item { Hint(stringResource(R.string.commented_none)) }
                }
                items(commentedHere.take(3).withIndex().toList(), key = { "c/${it.value}" }) { (i, key) ->
                    val q = pack.question(key)!!
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onOpenSaved(SavedTab.Commented, i) }
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Tile(q.image, Modifier.size(56.dp).clip(MaterialTheme.shapes.small))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                pack.paper(q.paperId)?.let { progresses.title(it) + " · Q" + q.number } ?: "",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                comments[key]?.lastOrNull()?.text ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                item { SectionHead(stringResource(R.string.papers), null) }
                items(subjects) { subject ->
                    Box(Modifier.padding(horizontal = 16.dp)) {
                        SubjectTheme(subject, fill = false) { SubjectCard(subject, progress?.subjects?.get(subject), onClick = { onSubject(subject) }) }
                    }
                }
                val toFix = progress?.mistakes?.size ?: 0
                if (toFix > 0) {
                    item {
                        Box(Modifier.padding(horizontal = 16.dp)) {
                            SheetAction(Icons.Rounded.Replay, pluralStringResource(R.plurals.mistakes_to_fix, toFix, toFix), stringResource(R.string.mistakes_hint), onClick = onMistakes)
                        }
                    }
                }
            }
        }

        DockFade(Modifier.align(Alignment.BottomCenter))
        Dock(
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp)
                .widthIn(max = 480.dp)
                .fillMaxWidth(),
        ) {
            DockAction(Icons.Rounded.Bookmarks, stringResource(R.string.saved), { onSaved(SavedTab.Liked) })
            PrimaryPill(Icons.Rounded.SwipeUp, stringResource(R.string.doomscroll), { choosing = true })
            DockAction(Icons.Rounded.Replay, stringResource(R.string.mistakes), onMistakes)
        }
    }

    if (choosing) {
        ModalBottomSheet(
            onDismissRequest = { choosing = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.doomscroll), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 4.dp))
                Text(stringResource(R.string.doomscroll_description), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
                subjects.forEach { subject ->
                    SubjectTheme(subject, fill = false) {
                        SheetAction(subject.icon, subjectName(subject), null, onClick = {
                            choosing = false
                            onScroll(subject)
                        })
                    }
                }
            }
        }
    }
}

/** A subject as a story: a ringed circle and its name. */
@Composable
private fun Story(subject: Subject, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick)) {
        Box(
            Modifier
                .size(76.dp)
                .border(3.dp, colors.primary, CircleShape)
                .padding(6.dp)
                .clip(CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Surface(shape = CircleShape, color = colors.primaryContainer, contentColor = colors.onPrimaryContainer, modifier = Modifier.fillMaxSize()) {
                Box(contentAlignment = Alignment.Center) { Icon(subject.icon, contentDescription = null, modifier = Modifier.size(30.dp)) }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(subjectName(subject), style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun SectionHead(title: String, onSeeAll: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (onSeeAll != null) TextButton(onClick = onSeeAll) { Text(stringResource(R.string.see_all)) }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp))
}

@Composable
private fun SubjectCard(subject: Subject, progress: SubjectProgress?, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
        interactionSource = interaction,
        modifier = Modifier.fillMaxWidth().pressScale(interaction),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(48.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) { Icon(subject.icon, contentDescription = null) }
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(subjectName(subject), style = MaterialTheme.typography.titleLarge)
                    val p = progress
                    Text(
                        when {
                            p == null -> " "
                            p.tried == 0 -> stringResource(R.string.subject_line_new, p.total)
                            else -> stringResource(R.string.subject_line, p.total, p.tried, p.right * 100 / p.tried)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            val p = progress
            if (p != null && p.tried > 0) {
                Spacer(Modifier.height(16.dp))
                // How much of the subject has been tried; the right answers are the green part.
                Box {
                    LinearProgressIndicator(
                        progress = { p.tried.toFloat() / p.total.coerceAtLeast(1) },
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                        color = MaterialTheme.colorScheme.outline,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        strokeCap = StrokeCap.Round,
                        drawStopIndicator = {},
                    )
                    LinearProgressIndicator(
                        progress = { p.right.toFloat() / p.total.coerceAtLeast(1) },
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                        color = LocalPaperExtras.current.success,
                        trackColor = Color.Transparent,
                        strokeCap = StrokeCap.Round,
                        drawStopIndicator = {},
                    )
                }
            }
        }
    }
}
