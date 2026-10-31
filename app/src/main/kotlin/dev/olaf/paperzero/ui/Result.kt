package dev.olaf.paperzero.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.olaf.paperzero.R
import dev.olaf.paperzero.core.data.Pack
import dev.olaf.paperzero.core.data.Progresses
import dev.olaf.paperzero.core.data.SessionEntity
import dev.olaf.paperzero.core.design.Dock
import dev.olaf.paperzero.core.design.DockAction
import dev.olaf.paperzero.core.design.PrimaryPill
import dev.olaf.paperzero.core.design.SheetAction
import kotlinx.coroutines.launch

/** How a sitting went: right out of all, how long it took, and what to do with the wrong ones. */
@Composable
fun ResultScreen(pack: Pack, progresses: Progresses, sessionId: Long, onDone: () -> Unit, onReview: () -> Unit, onStart: (Long) -> Unit) {
    var session by remember { mutableStateOf<SessionEntity?>(null) }
    var wrong by remember { mutableStateOf<List<String>>(emptyList()) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(sessionId) {
        val s = progresses.session(sessionId) ?: return@LaunchedEffect onDone()
        val given = progresses.answers(sessionId)
        wrong = s.keys.split(',').filter { it.isNotEmpty() && given[it]?.correct != true }
        session = s
    }
    val s = session
    if (s == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.align(Alignment.TopCenter).widthIn(max = 560.dp).fillMaxWidth().statusBarsPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(s.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 32.dp))
            Spacer(Modifier.height(24.dp))
            Text("${s.score}", fontSize = 96.sp, style = MaterialTheme.typography.displayLarge)
            Text(stringResource(R.string.result_of, s.total), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.result_time, s.score * 100 / s.total.coerceAtLeast(1), duration(s.spentMs)),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(40.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SheetAction(Icons.Rounded.Visibility, stringResource(R.string.review), null, onClick = onReview)
                if (wrong.isNotEmpty()) {
                    SheetAction(Icons.Rounded.Replay, stringResource(R.string.practise_wrong), null, onClick = {
                        scope.launch { onStart(progresses.startMistakes(wrong, s.title)) }
                    })
                }
            }
        }
        Dock(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp).widthIn(max = 480.dp).fillMaxWidth()) {
            PrimaryPill(Icons.Rounded.Check, stringResource(R.string.done), onDone)
        }
    }
}
