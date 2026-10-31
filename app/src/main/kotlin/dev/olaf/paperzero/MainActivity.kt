package dev.olaf.paperzero

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.olaf.paperzero.core.data.Subject
import dev.olaf.paperzero.core.design.PaperTheme
import dev.olaf.paperzero.ui.HomeScreen
import dev.olaf.paperzero.ui.MistakesScreen
import dev.olaf.paperzero.ui.PapersScreen
import dev.olaf.paperzero.ui.QuizScreen
import dev.olaf.paperzero.ui.ResultScreen
import dev.olaf.paperzero.ui.FeedScreen
import dev.olaf.paperzero.ui.FeedSource
import dev.olaf.paperzero.ui.SavedFeedScreen
import dev.olaf.paperzero.ui.SavedScreen
import dev.olaf.paperzero.ui.SavedTab
import dev.olaf.paperzero.ui.StatsScreen
import dev.olaf.paperzero.ui.SubjectTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import dev.olaf.paperzero.ui.StartupScreen
import kotlinx.coroutines.delay

/** [content] in the colours of the science a sitting's questions are all from, if they are. */
@Composable
private fun SessionTheme(app: PaperZeroApplication, sessionId: Long, content: @Composable () -> Unit) {
    val subject by produceState<Subject?>(null, sessionId) {
        val keys = app.progresses.session(sessionId)?.keys?.split(',')?.filter { it.isNotEmpty() }.orEmpty()
        value = keys.mapNotNull { app.pack.paper(it.substringBeforeLast('/'))?.subject }.distinct().singleOrNull()
    }
    SubjectTheme(subject, content = content)
}

/** How long Olaf greets on a fresh start. */
private const val STARTUP_MS = 1600L

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val app = application as PaperZeroApplication
        setContent {
            PaperTheme {
                // A fresh start greets with Olaf for a moment; coming back to the app doesn't.
                var greeting by rememberSaveable { mutableStateOf(savedInstanceState == null) }
                LaunchedEffect(Unit) {
                    delay(STARTUP_MS)
                    greeting = false
                }
                if (greeting) {
                    StartupScreen()
                    return@PaperTheme
                }
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    val nav = rememberNavController()
                    val onTopic: (Subject, Int) -> Unit = { s, t -> nav.navigate("feed/${s.name}?topic=$t") }
                    NavHost(nav, startDestination = "home") {
                        composable("home") {
                            HomeScreen(
                                app.pack,
                                app.progresses,
                                onSubject = { nav.navigate("papers/${it.name}") },
                                onMistakes = { nav.navigate("mistakes") },
                                onScroll = { nav.navigate("feed/${it.name}") },
                                onSaved = { nav.navigate("saved/${it.name}") },
                                onOpenSaved = { tab, i -> nav.navigate("savedfeed/${tab.name}/$i") },
                                onStats = { nav.navigate("stats") },
                            )
                        }
                        composable("stats") {
                            StatsScreen(app.progresses, onBack = { nav.popBackStack() }, onTopic = { s, t -> nav.navigate("feed/${s.name}?topic=$t") })
                        }
                        composable(
                            "feed/{subject}?topic={topic}",
                            listOf(
                                navArgument("subject") { type = NavType.StringType },
                                navArgument("topic") { type = NavType.IntType; defaultValue = -1 },
                            ),
                        ) { entry ->
                            val subject = Subject.valueOf(entry.arguments!!.getString("subject")!!)
                            val topic = entry.arguments!!.getInt("topic").takeIf { it >= 0 }
                            SubjectTheme(subject) {
                                FeedScreen(app.pack, app.progresses, FeedSource.Of(subject, topic), onClose = { nav.popBackStack() }, onTopic = onTopic)
                            }
                        }
                        composable("saved/{tab}", listOf(navArgument("tab") { type = NavType.StringType })) { entry ->
                            SavedScreen(
                                app.pack,
                                app.progresses,
                                SavedTab.valueOf(entry.arguments!!.getString("tab")!!),
                                onBack = { nav.popBackStack() },
                                onOpen = { tab, i -> nav.navigate("savedfeed/${tab.name}/$i") },
                            )
                        }
                        composable(
                            "savedfeed/{tab}/{start}",
                            listOf(navArgument("tab") { type = NavType.StringType }, navArgument("start") { type = NavType.IntType }),
                        ) { entry ->
                            SavedFeedScreen(
                                app.pack,
                                app.progresses,
                                SavedTab.valueOf(entry.arguments!!.getString("tab")!!),
                                entry.arguments!!.getInt("start"),
                                onClose = { nav.popBackStack() },
                                onTopic = onTopic,
                            )
                        }
                        composable("papers/{subject}", listOf(navArgument("subject") { type = NavType.StringType })) { entry ->
                            val subject = Subject.valueOf(entry.arguments!!.getString("subject")!!)
                            SubjectTheme(subject) {
                                PapersScreen(
                                    app.pack,
                                    app.progresses,
                                    subject,
                                    onBack = { nav.popBackStack() },
                                    onStart = { nav.navigate("quiz/$it") },
                                )
                            }
                        }
                        composable("mistakes") {
                            MistakesScreen(
                                app.pack,
                                app.progresses,
                                onBack = { nav.popBackStack() },
                                onStart = { nav.navigate("quiz/$it") },
                            )
                        }
                        composable("quiz/{session}", listOf(navArgument("session") { type = NavType.LongType })) { entry ->
                            val session = entry.arguments!!.getLong("session")
                            SessionTheme(app, session) {
                                QuizScreen(
                                    app.pack,
                                    app.progresses,
                                    session,
                                    onClose = { nav.popBackStack() },
                                    onFinished = {
                                        nav.navigate("result/$session") { popUpTo("quiz/{session}") { inclusive = true } }
                                    },
                                )
                            }
                        }
                        composable("result/{session}", listOf(navArgument("session") { type = NavType.LongType })) { entry ->
                            val session = entry.arguments!!.getLong("session")
                            SessionTheme(app, session) {
                                ResultScreen(
                                    app.pack,
                                    app.progresses,
                                    session,
                                    onDone = { nav.popBackStack() },
                                    onReview = { nav.navigate("review/$session") },
                                    onStart = { nav.navigate("quiz/$it") { popUpTo("result/{session}") { inclusive = true } } },
                                )
                            }
                        }
                        composable("review/{session}", listOf(navArgument("session") { type = NavType.LongType })) { entry ->
                            val session = entry.arguments!!.getLong("session")
                            SessionTheme(app, session) {
                                QuizScreen(
                                    app.pack,
                                    app.progresses,
                                    session,
                                    review = true,
                                    onClose = { nav.popBackStack() },
                                    onFinished = { nav.popBackStack() },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
