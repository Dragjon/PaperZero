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
import dev.olaf.paperzero.ui.ScrollScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val app = application as PaperZeroApplication
        setContent {
            PaperTheme {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    val nav = rememberNavController()
                    NavHost(nav, startDestination = "home") {
                        composable("home") {
                            HomeScreen(
                                app.pack,
                                app.progresses,
                                onSubject = { nav.navigate("papers/${it.name}") },
                                onMistakes = { nav.navigate("mistakes") },
                                onScroll = { nav.navigate("scroll/${it.name}") },
                            )
                        }
                        composable("scroll/{subject}", listOf(navArgument("subject") { type = NavType.StringType })) { entry ->
                            ScrollScreen(app.pack, app.progresses, Subject.valueOf(entry.arguments!!.getString("subject")!!), onClose = { nav.popBackStack() })
                        }
                        composable("papers/{subject}", listOf(navArgument("subject") { type = NavType.StringType })) { entry ->
                            PapersScreen(
                                app.pack,
                                app.progresses,
                                Subject.valueOf(entry.arguments!!.getString("subject")!!),
                                onBack = { nav.popBackStack() },
                                onStart = { nav.navigate("quiz/$it") },
                            )
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
                        composable("result/{session}", listOf(navArgument("session") { type = NavType.LongType })) { entry ->
                            val session = entry.arguments!!.getLong("session")
                            ResultScreen(
                                app.pack,
                                app.progresses,
                                session,
                                onDone = { nav.popBackStack() },
                                onReview = { nav.navigate("review/$session") },
                                onStart = { nav.navigate("quiz/$it") { popUpTo("result/{session}") { inclusive = true } } },
                            )
                        }
                        composable("review/{session}", listOf(navArgument("session") { type = NavType.LongType })) { entry ->
                            QuizScreen(
                                app.pack,
                                app.progresses,
                                entry.arguments!!.getLong("session"),
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
