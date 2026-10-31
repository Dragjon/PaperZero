package dev.olaf.paperzero.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.olaf.paperzero.R
import dev.olaf.paperzero.core.data.Mode
import dev.olaf.paperzero.core.data.Pack
import dev.olaf.paperzero.core.data.PaperInfo
import dev.olaf.paperzero.core.data.Progresses
import dev.olaf.paperzero.core.data.Subject
import dev.olaf.paperzero.core.design.SheetAction
import dev.olaf.paperzero.core.design.pressScale
import kotlinx.coroutines.launch

private enum class Source { All, OLevel, Schools }

/**
 * A subject's papers: the O-Level years, newest first, then the schools' prelims. In the colours
 * of the subject chosen, which change with it.
 */
@Composable
fun PapersScreen(pack: Pack, progresses: Progresses, start: Subject, onBack: () -> Unit, onStart: (Long) -> Unit) {
    var subject by rememberSaveable { mutableStateOf(start) }
    SubjectTheme(subject) {
        Papers(pack, progresses, subject, onSubject = { subject = it }, onBack, onStart)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Papers(pack: Pack, progresses: Progresses, subject: Subject, onSubject: (Subject) -> Unit, onBack: () -> Unit, onStart: (Long) -> Unit) {
    var source by rememberSaveable { mutableStateOf(Source.All) }
    var picked by remember { mutableStateOf<PaperInfo?>(null) }
    val progress by progresses.progress.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    val subjects = Subject.entries.filter { s -> pack.papers.any { it.subject == s } }
    val papers = pack.papers
        .filter { it.subject == subject }
        .filter { source == Source.All || (source == Source.OLevel) == it.isOLevel }
        .sortedWith(compareBy<PaperInfo> { !it.isOLevel }.thenByDescending { it.year ?: 0 }.thenBy { it.source })

    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.papers), null, onBack)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            subjects.forEach { s -> Chip(subjectName(s), s == subject) { onSubject(s) } }
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Chip(stringResource(R.string.filter_all), source == Source.All, subtle = true) { source = Source.All }
            Chip(stringResource(R.string.filter_olevel), source == Source.OLevel, subtle = true) { source = Source.OLevel }
            Chip(stringResource(R.string.filter_schools), source == Source.Schools, subtle = true) { source = Source.Schools }
        }
        LazyColumn(
            modifier = Modifier.align(Alignment.CenterHorizontally).widthIn(max = 720.dp).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "timed") {
                val title = stringResource(R.string.timed_set_title, subjectName(subject))
                val unfinished by produceState(false, title, progress) { value = progresses.hasUnfinishedTimed(title) }
                TimedSetCard(pack.papers.count { it.subject == subject }, unfinished) {
                    scope.launch { onStart(progresses.startTimed(subject, title)) }
                }
            }
            items(papers, key = { it.id }) { paper ->
                PaperCard(paper, progresses.title(paper), progress?.best?.get(paper.id)) { picked = paper }
            }
        }
    }

    picked?.let { paper ->
        ModalBottomSheet(
            onDismissRequest = { picked = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "${subjectName(paper.subject)} · ${progresses.title(paper)}",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
                )
                fun go(mode: Mode) {
                    picked = null
                    scope.launch { onStart(progresses.startPaper(paper.id, mode)) }
                }
                SheetAction(Icons.Rounded.EditNote, stringResource(R.string.mode_practice), stringResource(R.string.mode_practice_description), onClick = { go(Mode.Practice) })
                SheetAction(Icons.Rounded.Timer, stringResource(R.string.mode_exam), stringResource(R.string.mode_exam_description), onClick = { go(Mode.Exam) })
            }
        }
    }
}

/** A choice among a few: lit in the action color when chosen (or, [subtle], a quieter tone). */
@Composable
fun Chip(label: String, selected: Boolean, subtle: Boolean = false, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = when {
            selected && !subtle -> colors.primary
            selected -> colors.secondaryContainer
            else -> colors.surfaceContainer
        },
        contentColor = when {
            selected && !subtle -> colors.onPrimary
            selected -> colors.onSecondaryContainer
            else -> colors.onSurfaceVariant
        },
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp))
    }
}

@Composable
private fun PaperCard(paper: PaperInfo, title: String, best: dev.olaf.paperzero.core.data.Best?, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
        interactionSource = interaction,
        modifier = Modifier.fillMaxWidth().pressScale(interaction),
    ) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    if (best != null) stringResource(R.string.paper_best, paper.count, best.score, best.total) else stringResource(R.string.paper_line, paper.count),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (best != null) {
                Spacer(Modifier.width(12.dp))
                Text("${best.score * 100 / best.total.coerceAtLeast(1)}%", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
