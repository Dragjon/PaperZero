package dev.olaf.paperzero.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.olaf.paperzero.R
import dev.olaf.paperzero.core.data.Pack
import dev.olaf.paperzero.core.data.Progresses
import dev.olaf.paperzero.core.data.Subject
import dev.olaf.paperzero.core.design.Dock
import dev.olaf.paperzero.core.design.PrimaryPill
import dev.olaf.paperzero.core.design.SheetAction
import kotlinx.coroutines.launch

/** What's been got wrong and not yet put right, by subject, to practise again. */
@Composable
fun MistakesScreen(pack: Pack, progresses: Progresses, onBack: () -> Unit, onStart: (Long) -> Unit) {
    val progress by progresses.progress.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    val mistakes = progress?.mistakes.orEmpty()
    val bySubject = Subject.entries.associateWith { s -> mistakes.filter { pack.paper(it.substringBeforeLast('/'))?.subject == s } }
        .filterValues { it.isNotEmpty() }
    val title = stringResource(R.string.mistakes)

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.align(Alignment.TopCenter).widthIn(max = 720.dp).fillMaxWidth()) {
            TopBar(title, null, onBack)
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(if (mistakes.isEmpty()) R.string.mistakes_none else R.string.mistakes_hint),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
                )
                bySubject.forEach { (subject, keys) ->
                    val name = subjectName(subject)
                    SheetAction(subject.icon, name, pluralStringResource(R.plurals.mistakes_to_fix, keys.size, keys.size), onClick = {
                        scope.launch { onStart(progresses.startMistakes(keys, "$title · $name")) }
                    })
                }
            }
        }
        if (mistakes.isNotEmpty()) {
            Dock(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp).widthIn(max = 480.dp).fillMaxWidth()) {
                PrimaryPill(Icons.Rounded.Replay, stringResource(R.string.practise_mistakes), {
                    scope.launch { onStart(progresses.startMistakes(mistakes, title)) }
                })
            }
        }
    }
}
