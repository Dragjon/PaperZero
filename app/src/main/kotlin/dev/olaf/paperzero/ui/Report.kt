package dev.olaf.paperzero.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.olaf.paperzero.R
import dev.olaf.paperzero.core.data.Question

/** What can be wrong with a question, as someone doing it would see it. */
enum class Problem(val label: Int) {
    Answer(R.string.report_answer),
    Cut(R.string.report_cut),
    Marks(R.string.report_marks),
    Topic(R.string.report_topic),
    Other(R.string.report_other),
}

/**
 * Reporting a question that's wrong: what's wrong, a note if they like, then a GitHub issue opens
 * with it all filled in (which paper, which question, the answer the app has, the app's version),
 * to send from there. There's no server: nothing leaves the phone until they send it themselves.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportSheet(question: Question, source: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var problem by rememberSaveable { mutableStateOf<Problem?>(null) }
    var note by rememberSaveable { mutableStateOf("") }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
        ) {
            Text(stringResource(R.string.report_title), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.report_which, source, question.number),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Column(Modifier.selectableGroup()) {
                Problem.entries.forEach { p ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = problem == p, role = Role.RadioButton) { problem = p }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = problem == p, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(p.label), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                note,
                { note = it.take(NOTE_MAX) },
                label = { Text(stringResource(if (problem == Problem.Other) R.string.report_note_needed else R.string.report_note)) },
                placeholder = { Text(stringResource(R.string.report_note_hint)) },
                minLines = 2,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.report_how), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            val ready = problem != null && (problem != Problem.Other || note.isNotBlank())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(
                    enabled = ready,
                    onClick = {
                        openReport(context, question, source, problem!!, note.trim())
                        onDismiss()
                    },
                ) { Text(stringResource(R.string.report_send)) }
            }
        }
    }
}

private const val NOTE_MAX = 1000

/** Where reports go: a new issue on PaperZero's repository, filled in. */
private const val ISSUES = "https://github.com/Dragjon/PaperZero/issues/new"

private fun openReport(context: Context, question: Question, source: String, problem: Problem, note: String) {
    val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
    val what = context.getString(problem.label)
    val body = buildString {
        appendLine("**Question:** $source, Q${question.number} (`${question.key}`)")
        appendLine("**Problem:** $what")
        appendLine("**Answer in the app:** ${question.answer}")
        if (note.isNotEmpty()) {
            appendLine()
            appendLine(note)
        }
        appendLine()
        append("PaperZero $version, Android ${Build.VERSION.RELEASE}")
    }
    val uri = Uri.parse(ISSUES).buildUpon()
        .appendQueryParameter("title", "Q${question.number}, $source: $what")
        .appendQueryParameter("body", body)
        .build()
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
}
