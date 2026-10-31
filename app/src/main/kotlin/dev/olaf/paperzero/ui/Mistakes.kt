package dev.olaf.paperzero.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.TextButton
import androidx.compose.ui.draw.clip
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
        Column(Modifier.align(Alignment.TopCenter).widthIn(max = 720.dp).fillMaxWidth().fillMaxSize()) {
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
            // Each mistake, to take one off that was only a slip of the finger.
            if (mistakes.isNotEmpty()) {
                Text(
                    stringResource(R.string.mistakes_each),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp),
                )
                Text(
                    stringResource(R.string.mistakes_each_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
                )
                LazyColumn(
                    Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 16.dp, end = 8.dp, bottom = 120.dp),
                ) {
                    items(mistakes, key = { it }) { key ->
                        val q = pack.question(key) ?: return@items
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Tile(q.image, Modifier.size(56.dp).clip(MaterialTheme.shapes.small))
                            Spacer(Modifier.width(12.dp))
                            Text(
                                pack.paper(q.paperId)?.let { progresses.title(it) + " · Q" + q.number } ?: "",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { scope.launch { progresses.forgetMistake(key) } }) {
                                Text(stringResource(R.string.mistake_remove), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
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
