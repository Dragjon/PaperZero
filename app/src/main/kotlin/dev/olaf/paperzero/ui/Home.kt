package dev.olaf.paperzero.ui

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.SwipeUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.olaf.paperzero.R
import dev.olaf.paperzero.core.data.Pack
import dev.olaf.paperzero.core.data.Progresses
import dev.olaf.paperzero.core.data.Subject
import dev.olaf.paperzero.core.data.SubjectProgress
import dev.olaf.paperzero.core.design.Dock
import dev.olaf.paperzero.core.design.DockAction
import dev.olaf.paperzero.core.design.DockFade
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

/** The subjects and how each is going; Doomscroll starts an endless feed of one's questions. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    pack: Pack,
    progresses: Progresses,
    onSubject: (Subject) -> Unit,
    onMistakes: () -> Unit,
    onScroll: (Subject) -> Unit,
) {
    val progress by progresses.progress.collectAsState(initial = null)
    val subjects = Subject.entries.filter { s -> pack.papers.any { it.subject == s } }
    var choosing by rememberSaveable { mutableStateOf(false) }
    val statusBar = WindowInsets.statusBars.asPaddingValues()
    val navBar = WindowInsets.navigationBars.asPaddingValues()

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.align(Alignment.TopCenter).widthIn(max = 720.dp).fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = statusBar.calculateTopPadding() + 24.dp,
                bottom = navBar.calculateBottomPadding() + 120.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(Modifier.padding(start = 4.dp, bottom = 12.dp)) {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displaySmall)
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.tagline), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            }
            items(subjects) { subject ->
                SubjectCard(subject, progress?.subjects?.get(subject), onClick = { onSubject(subject) })
            }
            val toFix = progress?.mistakes?.size ?: 0
            if (toFix > 0) {
                item { MistakesCard(toFix, onMistakes) }
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
            DockAction(Icons.Rounded.Replay, stringResource(R.string.mistakes), onMistakes)
            PrimaryPill(Icons.Rounded.SwipeUp, stringResource(R.string.doomscroll), { choosing = true })
            DockAction(subjects.firstOrNull()?.icon ?: Icons.Rounded.School, stringResource(R.string.papers), {
                subjects.firstOrNull()?.let(onSubject)
            })
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
                    SheetAction(subject.icon, subjectName(subject), null, onClick = {
                        choosing = false
                        onScroll(subject)
                    })
                }
            }
        }
    }
}

@Composable
private fun SubjectCard(subject: Subject, progress: SubjectProgress?, onClick: () -> Unit) {
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
        interactionSource = interaction,
        modifier = Modifier.fillMaxWidth().pressScale(interaction),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.size(48.dp)) {
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
                        trackColor = androidx.compose.ui.graphics.Color.Transparent,
                        strokeCap = StrokeCap.Round,
                        drawStopIndicator = {},
                    )
                }
            }
        }
    }
}

@Composable
private fun MistakesCard(count: Int, onClick: () -> Unit) {
    SheetAction(
        Icons.Rounded.Replay,
        pluralStringResource(R.plurals.mistakes_to_fix, count, count),
        stringResource(R.string.mistakes_hint),
        onClick = onClick,
    )
}
