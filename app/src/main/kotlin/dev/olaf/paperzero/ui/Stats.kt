package dev.olaf.paperzero.ui

import dev.olaf.paperzero.core.design.skinCard
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.olaf.paperzero.R
import dev.olaf.paperzero.core.data.Progresses
import dev.olaf.paperzero.core.data.Stats
import dev.olaf.paperzero.core.data.Subject
import dev.olaf.paperzero.core.data.SubjectStats
import dev.olaf.paperzero.core.data.TopicStats
import dev.olaf.paperzero.core.design.LocalPaperExtras
import dev.olaf.paperzero.core.design.pressScale
import java.time.format.TextStyle
import java.util.Locale

/** "2 h 05 min", "12 min", "0 min": time studied, to the minute. */
fun studied(ms: Long): String {
    val min = ms / 60_000
    return if (min >= 60) "%d h %02d min".format(min / 60, min % 60) else "%d min".format(min)
}

/** On Home: the streak, this week's time and how right you've been, a tap from the full stats. */
@Composable
fun StatsPeek(stats: Stats?, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
        interactionSource = interaction,
        modifier = Modifier.fillMaxWidth().pressScale(interaction).skinCard(MaterialTheme.shapes.extraLarge),
    ) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Insights, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.stats), style = MaterialTheme.typography.titleMedium)
                val s = stats
                Text(
                    when {
                        s == null -> " "
                        s.tally.answered == 0 && s.studiedMs < 60_000 -> stringResource(R.string.stats_peek_none)
                        else -> listOfNotNull(
                            pluralStringResource(R.plurals.streak_days, s.streak, s.streak),
                            stringResource(R.string.stats_peek_week, studied(s.days.sumOf { it.second })),
                            s.tally.percent?.let { stringResource(R.string.stats_peek_right, it) },
                        ).joinToString(" · ")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * How it's going: time studied (this week day by day), the streak, how often right, and that by
 * science and by topic, weakest topics first, each a tap from a feed of its questions.
 */
@Composable
fun StatsScreen(progresses: Progresses, onBack: () -> Unit, onTopic: (Subject, Int) -> Unit) {
    val stats by progresses.stats.collectAsState(initial = null)
    val s = stats
    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.stats), null, onBack)
        if (s == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return
        }
        // Topics of the science answered most, to begin with.
        var subject by rememberSaveable { mutableStateOf(s.subjects.maxByOrNull { it.tally.answered }?.subject) }
        val navBar = WindowInsets.navigationBars.asPaddingValues()
        LazyColumn(
            modifier = Modifier.fillMaxSize().align(Alignment.CenterHorizontally).widthIn(max = 720.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = navBar.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Big(studied(s.studiedMs), stringResource(R.string.stats_studied), Modifier.weight(1f))
                    Big(
                        s.streak.toString(),
                        // The best only when it's more than now.
                        pluralStringResource(R.plurals.stats_streak, s.streak) +
                            (if (s.longestStreak > s.streak) "\n" + stringResource(R.string.stats_best, s.longestStreak) else ""),
                        Modifier.weight(1f),
                    )
                    Big(
                        s.tally.percent?.let { "$it%" } ?: "–",
                        pluralStringResource(R.plurals.stats_answered, s.tally.answered, s.tally.answered),
                        Modifier.weight(1f),
                    )
                }
            }
            item { Week(s) }
            if (s.fixed > 0) {
                item {
                    Card {
                        Text(
                            pluralStringResource(R.plurals.stats_fixed, s.fixed, s.fixed),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
            item { Head(stringResource(R.string.stats_by_science)) }
            items(s.subjects, key = { "s/${it.subject}" }) { ss -> SubjectTheme(ss.subject, fill = false) { Science(ss) } }

            val known = s.topics.filter { it.tally.answered >= Stats.ENOUGH }
            if (known.size >= 2) {
                val weakest = known.sortedBy { it.tally.right.toFloat() / it.tally.answered }.take(3)
                val strongest = known.sortedByDescending { it.tally.right.toFloat() / it.tally.answered }.filter { it !in weakest }.take(3)
                item { Head(stringResource(R.string.stats_weakest), stringResource(R.string.stats_weakest_hint)) }
                items(weakest, key = { "w/${it.subject}/${it.topic}" }) { t ->
                    SubjectTheme(t.subject, fill = false) { Topic(t, showSubject = true) { onTopic(t.subject, t.topic) } }
                }
                if (strongest.isNotEmpty()) {
                    item { Head(stringResource(R.string.stats_strongest)) }
                    items(strongest, key = { "b/${it.subject}/${it.topic}" }) { t ->
                        SubjectTheme(t.subject, fill = false) { Topic(t, showSubject = true) { onTopic(t.subject, t.topic) } }
                    }
                }
            }

            item { Head(stringResource(R.string.stats_by_topic), stringResource(R.string.stats_topics_note)) }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    s.subjects.forEach { ss ->
                        SubjectTheme(ss.subject, fill = false) {
                            Chip(subjectName(ss.subject), subject == ss.subject) { subject = ss.subject }
                        }
                    }
                }
            }
            val shown = s.topics.filter { it.subject == subject }
                .sortedWith(compareBy<TopicStats> { it.tally.answered == 0 }.thenBy { it.tally.right.toFloat() / it.tally.answered.coerceAtLeast(1) })
            items(shown, key = { "t/${it.subject}/${it.topic}" }) { t ->
                SubjectTheme(t.subject, fill = false) { Topic(t, showSubject = false) { onTopic(t.subject, t.topic) } }
            }
        }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth().skinCard(MaterialTheme.shapes.extraLarge)) {
        Box(Modifier.padding(20.dp)) { content() }
    }
}

@Composable
private fun Head(title: String, hint: String? = null) {
    Column(Modifier.padding(start = 4.dp, top = 12.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A number to see first, and what it is. */
@Composable
private fun Big(value: String, label: String, modifier: Modifier) {
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier.skinCard(MaterialTheme.shapes.extraLarge)) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 16.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, minLines = 2)
        }
    }
}

/** The last seven days, a bar each for the time studied. */
@Composable
private fun Week(s: Stats) {
    val colors = MaterialTheme.colorScheme
    val most = s.days.maxOf { it.second }.coerceAtLeast(60_000)
    Card {
        Column {
            Text(stringResource(R.string.stats_this_week), style = MaterialTheme.typography.titleMedium)
            Text(
                listOfNotNull(
                    studied(s.days.sumOf { it.second }),
                    pluralStringResource(R.plurals.stats_answered, s.week.answered, s.week.answered),
                    s.week.percent?.let { stringResource(R.string.stats_peek_right, it) },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                s.days.forEachIndexed { i, (day, ms) ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            if (ms >= 60_000) (ms / 60_000).toString() else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant,
                        )
                        val today = i == s.days.lastIndex
                        Canvas(Modifier.fillMaxWidth().height(96.dp)) {
                            val h = size.height * (ms.toFloat() / most)
                            val r = CornerRadius(8.dp.toPx())
                            drawRoundRect(colors.surfaceContainerHigh, cornerRadius = r)
                            if (h > 0f) {
                                drawRoundRect(
                                    if (today) colors.primary else colors.primary.copy(alpha = 0.55f),
                                    topLeft = Offset(0f, size.height - h),
                                    size = Size(size.width, h),
                                    cornerRadius = r,
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            day.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (today) colors.onSurface else colors.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

/** A science: how often right, how long, and how much of it tried. */
@Composable
private fun Science(ss: SubjectStats) {
    val colors = MaterialTheme.colorScheme
    Card {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = MaterialTheme.shapes.large, color = colors.primaryContainer, contentColor = colors.onPrimaryContainer, modifier = Modifier.size(44.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(ss.subject.icon, contentDescription = null) }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(subjectName(ss.subject), style = MaterialTheme.typography.titleMedium)
                    Text(
                        listOf(
                            studied(ss.studiedMs),
                            pluralStringResource(R.plurals.stats_answered, ss.tally.answered, ss.tally.answered),
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                }
                Text(ss.tally.percent?.let { "$it%" } ?: "–", style = MaterialTheme.typography.headlineSmall, color = colors.primary)
            }
            Spacer(Modifier.height(14.dp))
            Bar(ss.tried.toFloat() / ss.questions.coerceAtLeast(1))
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.stats_tried_of, ss.tried, ss.questions),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

/** A topic: how often right, as a bar; a tap opens a feed of its questions. */
@Composable
private fun Topic(t: TopicStats, showSubject: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val untried = t.tally.answered == 0
    Surface(shape = MaterialTheme.shapes.large, color = colors.surfaceContainer, modifier = Modifier.fillMaxWidth().skinCard(MaterialTheme.shapes.large).clickable(onClick = onClick)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(t.name, style = MaterialTheme.typography.titleSmall, color = if (untried) colors.onSurfaceVariant else colors.onSurface)
                    Text(
                        listOfNotNull(
                            if (showSubject) subjectName(t.subject) else null,
                            if (untried) stringResource(R.string.stats_topic_untried, t.questions)
                            else stringResource(R.string.stats_topic_line, t.tally.answered, t.tried, t.questions),
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
                Text(t.tally.percent?.let { "$it%" } ?: "", style = MaterialTheme.typography.titleMedium, color = colors.primary)
            }
            if (!untried) {
                Spacer(Modifier.height(8.dp))
                Bar(t.tally.right.toFloat() / t.tally.answered, accuracy = true)
            }
        }
    }
}

/** A thin rounded bar: how far, or (green when mostly right) how often right. */
@Composable
private fun Bar(fraction: Float, accuracy: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    val color = when {
        !accuracy -> colors.primary
        fraction >= 0.7f -> LocalPaperExtras.current.success
        fraction >= 0.4f -> colors.primary
        else -> colors.error
    }
    LinearProgressIndicator(
        progress = { fraction.coerceIn(0f, 1f) },
        modifier = Modifier.fillMaxWidth().height(6.dp),
        color = color,
        trackColor = colors.surfaceContainerHigh,
        strokeCap = StrokeCap.Round,
        drawStopIndicator = {},
    )
}
