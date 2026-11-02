package dev.olaf.paperzero.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import dev.olaf.paperzero.core.data.Stats
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.MilitaryTech
import androidx.compose.material.icons.rounded.Quiz
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.olaf.paperzero.R
import dev.olaf.paperzero.core.data.Badge
import dev.olaf.paperzero.core.data.BadgeKind
import dev.olaf.paperzero.core.data.Rewards
import dev.olaf.paperzero.core.data.Subject
import dev.olaf.paperzero.core.design.skinCard
import kotlinx.coroutines.delay

/** The run of right answers shows from this long; every [COMBO_BUZZ] of it, a buzz. */
private const val COMBO_SHOWS = 3
private const val COMBO_BUZZ = 5
private const val NOTE_MS = 1500L
private const val BADGE_MS = 2800L
/** Badges listed as closest, before "See all". */
private const val BADGES_CLOSEST = 3

/**
 * Over a feed: what each answer just earned, popping in and gone again; a level or the day's goal
 * reached, louder; and the run of right answers while it lasts. Takes no touches.
 */
@Composable
fun RewardOverlay(rewards: Rewards?, badges: List<Badge>?) {
    val haptics = LocalHapticFeedback.current
    var before by remember { mutableStateOf<Rewards?>(null) }
    var note by remember { mutableStateOf<String?>(null) }
    var big by remember { mutableStateOf(false) }
    var notes by remember { mutableIntStateOf(0) }
    val levelUp = rewards?.let { stringResource(R.string.reward_level_up, it.level) }
    val goalDone = stringResource(R.string.reward_goal_done)
    val plain = rewards?.last?.let { stringResource(R.string.reward_xp, it.xp) }
    val lucky = rewards?.last?.let { stringResource(R.string.reward_xp_lucky, it.xp) }
    val putRight = rewards?.last?.let { stringResource(R.string.reward_xp_put_right, it.xp) }

    LaunchedEffect(rewards) {
        val now = rewards ?: return@LaunchedEffect
        val was = before
        before = now
        // Only for an answer just given: not on opening, and not on an undo.
        if (was == null || now.xp <= was.xp) return@LaunchedEffect
        val last = now.last
        val crossedGoal = was.today < Rewards.GOAL && now.today >= Rewards.GOAL
        big = now.level > was.level || crossedGoal
        note = when {
            now.level > was.level -> levelUp
            crossedGoal -> goalDone
            last?.lucky == true -> lucky
            last?.putRight == true -> putRight
            else -> plain
        }
        notes++
        if (big || last?.lucky == true || (now.combo > 0 && now.combo % COMBO_BUZZ == 0)) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    // A badge just earned stays a little longer, one at a time. Not those held on opening.
    var held by remember { mutableStateOf<Set<Pair<BadgeKind, Int>>?>(null) }
    val waiting = remember { mutableStateListOf<Badge>() }
    LaunchedEffect(badges) {
        val now = badges?.filter { it.unlocked } ?: return@LaunchedEffect
        val had = held
        held = now.map { it.kind to it.need }.toSet()
        if (had != null) waiting += now.filter { (it.kind to it.need) !in had }
    }
    val earned = waiting.firstOrNull()

    Box(Modifier.fillMaxSize().statusBarsPadding().padding(top = 56.dp, start = 12.dp, end = 12.dp)) {
        earned?.let { b ->
            val pop = remember(b) { Animatable(0.6f) }
            LaunchedEffect(b) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                delay(BADGE_MS)
                waiting.remove(b)
            }
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 44.dp).graphicsLayer {
                    scaleX = pop.value
                    scaleY = pop.value
                },
            ) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(badgeIcon(b.kind), contentDescription = null, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(stringResource(R.string.badge_earned), style = MaterialTheme.typography.labelMedium)
                        Text(badgeName(b), style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
        }
        note?.let { text ->
            val pop = remember(notes) { Animatable(0.6f) }
            val fade = remember(notes) { Animatable(1f) }
            LaunchedEffect(notes) {
                pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                delay(if (big) NOTE_MS * 2 else NOTE_MS)
                fade.animateTo(0f, tween(250))
                note = null
            }
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.align(Alignment.TopCenter).graphicsLayer {
                    scaleX = pop.value
                    scaleY = pop.value
                    alpha = fade.value
                },
            ) {
                Text(
                    text,
                    style = if (big) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = if (big) 10.dp else 6.dp),
                )
            }
        }
        val combo = rewards?.combo ?: 0
        if (combo >= COMBO_SHOWS) {
            Surface(shape = CircleShape, color = Glass, contentColor = Ink, modifier = Modifier.align(Alignment.TopEnd)) {
                Row(Modifier.padding(start = 6.dp, end = 10.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.LocalFireDepartment, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(2.dp))
                    Text(stringResource(R.string.reward_combo, combo), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

/** On Home: today's goal as a ring, the level and what's left of it, the streak and the best run. Tap to go on. */
@Composable
fun RewardsCard(rewards: Rewards, streak: Int, next: Badge?, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.extraLarge,
        color = colors.surfaceContainer,
        modifier = Modifier.fillMaxWidth().skinCard(MaterialTheme.shapes.extraLarge),
    ) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { (rewards.today.toFloat() / Rewards.GOAL).coerceAtMost(1f) },
                    modifier = Modifier.fillMaxSize(),
                    color = colors.primary,
                    trackColor = colors.surfaceContainerHigh,
                    strokeWidth = 6.dp,
                    strokeCap = StrokeCap.Round,
                    gapSize = 0.dp,
                )
                if (rewards.today >= Rewards.GOAL) {
                    Icon(Icons.Rounded.Check, contentDescription = null, tint = colors.primary)
                } else {
                    Text(rewards.today.toString(), style = MaterialTheme.typography.titleMedium)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.reward_level, rewards.level), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text(
                        stringResource(R.string.reward_to_next, rewards.levelSize - rewards.intoLevel),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { rewards.intoLevel.toFloat() / rewards.levelSize },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = colors.primary,
                    trackColor = colors.surfaceContainerHigh,
                    strokeCap = StrokeCap.Round,
                    drawStopIndicator = {},
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    if (rewards.today >= Rewards.GOAL) {
                        stringResource(R.string.reward_summary_done, rewards.today, streak, rewards.bestCombo)
                    } else {
                        stringResource(R.string.reward_summary, Rewards.GOAL - rewards.today, streak, rewards.bestCombo)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
                next?.let { b ->
                    Text(
                        stringResource(R.string.badge_next, badgeName(b), badgeToGo(b)),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.primary,
                    )
                }
            }
        }
    }
}

/** Who you are so far, first on the stats: the level and what's left of it, and the three numbers that matter most. */
@Composable
fun Overview(r: Rewards, s: Stats) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = MaterialTheme.shapes.extraLarge, color = colors.surfaceContainer, modifier = Modifier.fillMaxWidth().skinCard(MaterialTheme.shapes.extraLarge)) {
        Column(Modifier.padding(20.dp)) {
            Text(stringResource(R.string.reward_level, r.level), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.overview_xp, r.xp, r.levelSize - r.intoLevel), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { r.intoLevel.toFloat() / r.levelSize },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = colors.primary,
                trackColor = colors.surfaceContainerHigh,
                strokeCap = StrokeCap.Round,
                drawStopIndicator = {},
            )
            Spacer(Modifier.height(20.dp))
            Row(Modifier.height(IntrinsicSize.Min)) {
                Vital(studied(s.studiedMs), stringResource(R.string.stats_studied), Modifier.weight(1f))
                Rule()
                Vital(
                    s.streak.toString(),
                    // The best only when it's more than now.
                    pluralStringResource(R.plurals.stats_streak, s.streak) +
                        (if (s.longestStreak > s.streak) "\n" + stringResource(R.string.stats_best, s.longestStreak) else ""),
                    Modifier.weight(1f),
                )
                Rule()
                Vital(
                    s.tally.percent?.let { "$it%" } ?: "–",
                    pluralStringResource(R.plurals.stats_answered, s.tally.answered, s.tally.answered),
                    Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun Rule() {
    Box(Modifier.padding(horizontal = 12.dp).width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant))
}

@Composable
private fun Vital(value: String, label: String, modifier: Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The records: a plain list to run down, not more boxes. */
@Composable
fun Records(r: Rewards) {
    val rows = listOf(
        Triple(Icons.Rounded.LocalFireDepartment, R.string.record_best_run, r.bestCombo),
        Triple(Icons.Rounded.Bolt, R.string.record_best_day, r.bestDay),
        Triple(Icons.Rounded.TaskAlt, R.string.record_goal_days, r.goalDays),
        Triple(Icons.Rounded.AutoFixHigh, R.string.record_put_right, r.putRights),
        Triple(Icons.Rounded.Casino, R.string.record_lucky, r.luckyDoubles),
    )
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Head(stringResource(R.string.records))
        Surface(shape = MaterialTheme.shapes.extraLarge, color = colors.surfaceContainer, modifier = Modifier.fillMaxWidth().skinCard(MaterialTheme.shapes.extraLarge)) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                rows.forEachIndexed { i, (icon, label, value) ->
                    if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(colors.outlineVariant))
                    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(icon, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(label), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text(value.toString(), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

/**
 * The badges, as one card: how many of how many; the best held of each kind; the few closest to
 * being earned. "See all" lays every kind out as a row of its tiers, so all of them read at a glance.
 */
@Composable
fun BadgesSection(badges: List<Badge>) {
    var all by rememberSaveable { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val earned = badges.count { it.unlocked }
    val byKind = badges.groupBy { it.kind }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // Padded as a row, so the heading and "See all" line up with each other.
        Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { Head(stringResource(R.string.badges), top = 0.dp) }
            TextButton(onClick = { all = !all }) { Text(stringResource(if (all) R.string.badges_fewer else R.string.see_all)) }
        }
        Surface(shape = MaterialTheme.shapes.extraLarge, color = colors.surfaceContainer, modifier = Modifier.fillMaxWidth().skinCard(MaterialTheme.shapes.extraLarge)) {
            Column(Modifier.padding(20.dp)) {
                Text(stringResource(R.string.badges_count, earned, badges.size), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { earned.toFloat() / badges.size.coerceAtLeast(1) },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = colors.primary,
                    trackColor = colors.surfaceContainerHigh,
                    strokeCap = StrokeCap.Round,
                    drawStopIndicator = {},
                )
                if (all) {
                    byKind.forEach { (kind, tiers) ->
                        Spacer(Modifier.height(18.dp))
                        KindRow(kind, tiers)
                    }
                } else {
                    val best = byKind.values.mapNotNull { tiers -> tiers.lastOrNull { it.unlocked } }
                    if (best.isNotEmpty()) {
                        Spacer(Modifier.height(18.dp))
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            best.forEach { HeldBadge(it) }
                        }
                    }
                    // The next tier of each kind, nearest first.
                    val closest = byKind.values.mapNotNull { tiers -> tiers.firstOrNull { !it.unlocked } }.sortedByDescending { it.progress }.take(BADGES_CLOSEST)
                    if (closest.isNotEmpty()) {
                        Spacer(Modifier.height(18.dp))
                        Text(stringResource(R.string.badges_closest), style = MaterialTheme.typography.titleSmall, color = colors.onSurfaceVariant)
                        closest.forEach { b ->
                            Spacer(Modifier.height(12.dp))
                            ClosestBadge(b)
                        }
                    }
                }
            }
        }
    }
}

/** The disc a badge's icon sits on, in full colour once it's earned. */
@Composable
private fun Medal(kind: BadgeKind, earned: Boolean, size: Dp) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = CircleShape,
        color = if (earned) colors.primaryContainer else colors.surfaceContainerHigh,
        contentColor = if (earned) colors.onPrimaryContainer else colors.onSurfaceVariant,
        modifier = Modifier.size(size),
    ) {
        Box(contentAlignment = Alignment.Center) { Icon(badgeIcon(kind), contentDescription = null, modifier = Modifier.size(size * 0.5f)) }
    }
}

@Composable
private fun HeldBadge(b: Badge) {
    Column(Modifier.width(76.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Medal(b.kind, earned = true, size = 48.dp)
        Spacer(Modifier.height(6.dp))
        Text(badgeName(b), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ClosestBadge(b: Badge) {
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Medal(b.kind, earned = false, size = 36.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(badgeName(b), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Text(badgeToGo(b), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
            }
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { b.progress },
                modifier = Modifier.fillMaxWidth().height(4.dp),
                color = colors.primary,
                trackColor = colors.surfaceContainerHigh,
                strokeCap = StrokeCap.Round,
                drawStopIndicator = {},
            )
        }
    }
}

/** A kind of badge and its tiers, left to right: earned filled in, the next ringed, the rest quiet. */
@Composable
private fun KindRow(kind: BadgeKind, tiers: List<Badge>) {
    val colors = MaterialTheme.colorScheme
    val next = tiers.firstOrNull { !it.unlocked }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(badgeIcon(kind), contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(kindName(kind), style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            tiers.forEach { b ->
                val name = badgeName(b)
                val state = stringResource(if (b.unlocked) R.string.badge_done else R.string.badge_not_yet)
                Surface(
                    shape = CircleShape,
                    color = if (b.unlocked) colors.primaryContainer else Color.Transparent,
                    contentColor = if (b.unlocked) colors.onPrimaryContainer else colors.onSurfaceVariant,
                    border = when {
                        b.unlocked -> null
                        b == next -> BorderStroke(2.dp, colors.primary)
                        else -> BorderStroke(1.dp, colors.outlineVariant)
                    },
                    modifier = Modifier.size(44.dp).semantics(mergeDescendants = true) { contentDescription = "$name, $state" },
                ) {
                    Box(contentAlignment = Alignment.Center) { Text(tierLabel(b), style = MaterialTheme.typography.labelMedium, maxLines = 1) }
                }
            }
        }
    }
}

/** A tier, short enough for its disc: 5, 1k, 25%. */
private fun tierLabel(b: Badge): String = when {
    b.kind == BadgeKind.Biology || b.kind == BadgeKind.Chemistry || b.kind == BadgeKind.Physics -> "${b.need}%"
    b.need >= 1000 -> "${b.need / 1000}k"
    else -> b.need.toString()
}

@Composable
private fun kindName(kind: BadgeKind): String = when (kind) {
    BadgeKind.Answers -> stringResource(R.string.badge_kind_answers)
    BadgeKind.Combo -> stringResource(R.string.badge_kind_combo)
    BadgeKind.Streak -> stringResource(R.string.badge_kind_streak)
    BadgeKind.Goals -> stringResource(R.string.badge_kind_goals)
    BadgeKind.PutRight -> stringResource(R.string.badge_kind_put_right)
    BadgeKind.Lucky -> stringResource(R.string.badge_kind_lucky)
    BadgeKind.Level -> stringResource(R.string.badge_kind_level)
    BadgeKind.BestDay -> stringResource(R.string.badge_kind_best_day)
    BadgeKind.Topics -> stringResource(R.string.badge_kind_topics)
    BadgeKind.Biology, BadgeKind.Chemistry, BadgeKind.Physics -> stringResource(R.string.badge_kind_science, subjectName(Subject.valueOf(kind.name)))
}

private fun badgeIcon(kind: BadgeKind): ImageVector = when (kind) {
    BadgeKind.Answers -> Icons.Rounded.Quiz
    BadgeKind.Combo -> Icons.Rounded.LocalFireDepartment
    BadgeKind.Streak -> Icons.Rounded.EventAvailable
    BadgeKind.Goals -> Icons.Rounded.TaskAlt
    BadgeKind.PutRight -> Icons.Rounded.AutoFixHigh
    BadgeKind.Lucky -> Icons.Rounded.Casino
    BadgeKind.Level -> Icons.Rounded.MilitaryTech
    BadgeKind.BestDay -> Icons.Rounded.Bolt
    BadgeKind.Topics -> Icons.Rounded.School
    BadgeKind.Biology -> Subject.Biology.icon
    BadgeKind.Chemistry -> Subject.Chemistry.icon
    BadgeKind.Physics -> Subject.Physics.icon
}

@Composable
private fun badgeName(b: Badge): String = when (b.kind) {
    BadgeKind.Answers -> stringResource(R.string.badge_answers, b.need)
    BadgeKind.Combo -> stringResource(R.string.badge_combo, b.need)
    BadgeKind.Streak -> stringResource(R.string.badge_streak, b.need)
    BadgeKind.Goals -> pluralStringResource(R.plurals.badge_goals, b.need, b.need)
    BadgeKind.PutRight -> stringResource(R.string.badge_put_right, b.need)
    BadgeKind.Lucky -> stringResource(R.string.badge_lucky, b.need)
    BadgeKind.Level -> stringResource(R.string.reward_level, b.need)
    BadgeKind.BestDay -> stringResource(R.string.badge_best_day, b.need)
    BadgeKind.Topics -> pluralStringResource(R.plurals.badge_topics, b.need, b.need)
    BadgeKind.Biology, BadgeKind.Chemistry, BadgeKind.Physics -> stringResource(R.string.badge_science, b.need, subjectName(Subject.valueOf(b.kind.name)))
}

/** What's left to earn it. */
@Composable
private fun badgeToGo(b: Badge): String = when (b.kind) {
    BadgeKind.Biology, BadgeKind.Chemistry, BadgeKind.Physics -> stringResource(R.string.badge_to_go_percent, b.need - b.value)
    BadgeKind.Level -> stringResource(R.string.badge_to_go_levels, b.need - b.value)
    else -> stringResource(R.string.badge_to_go, b.need - b.value)
}
