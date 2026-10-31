package dev.olaf.paperzero

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import dev.olaf.paperzero.core.design.Ground
import dev.olaf.paperzero.core.design.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** How big the app's words are, against the phone's own setting. */
enum class TextSize(val scale: Float) { Small(0.9f), Default(1f), Large(1.15f), Larger(1.3f) }

/** About how many questions go by between memes; never exactly. */
enum class MemeFrequency(val every: Int) { Rare(15), Sometimes(8), Often(4) }

data class Settings(
    val theme: ThemeMode = ThemeMode.System,
    val ground: Ground = Ground.Warm,
    /** Each science's screens in its own colours; else the app's orange throughout. */
    val scienceColours: Boolean = true,
    /** The accent from the wallpaper (Android 12 on). */
    val wallpaperColour: Boolean = false,
    val textSize: TextSize = TextSize.Default,
    /** In the dark theme, questions' pictures as white on black instead of black on white. */
    val darkPictures: Boolean = false,
    /** Olaf for a moment when the app starts. */
    val greeting: Boolean = true,
    /** The clock counting down in an exam (it's timed either way). */
    val examClock: Boolean = true,
    /** Science memes between the questions of a feed, from the internet: the one thing that is. */
    val memes: Boolean = false,
    val memeFrequency: MemeFrequency = MemeFrequency.Sometimes,
)

/** The settings, kept on the phone; [settings] as they are now. */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(read())
    val settings: StateFlow<Settings> = state.asStateFlow()

    private inline fun <reified E : Enum<E>> enum(key: String, default: E): E =
        prefs.getString(key, null)?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: default

    private fun read(): Settings {
        val d = Settings()
        return Settings(
            theme = enum("theme", d.theme),
            ground = enum("ground", d.ground),
            scienceColours = prefs.getBoolean("scienceColours", d.scienceColours),
            wallpaperColour = prefs.getBoolean("wallpaperColour", d.wallpaperColour),
            textSize = enum("textSize", d.textSize),
            darkPictures = prefs.getBoolean("darkPictures", d.darkPictures),
            greeting = prefs.getBoolean("greeting", d.greeting),
            examClock = prefs.getBoolean("examClock", d.examClock),
            memes = prefs.getBoolean("memes", d.memes),
            memeFrequency = enum("memeFrequency", d.memeFrequency),
        )
    }

    fun update(change: (Settings) -> Settings) {
        val s = change(state.value)
        prefs.edit()
            .putString("theme", s.theme.name)
            .putString("ground", s.ground.name)
            .putBoolean("scienceColours", s.scienceColours)
            .putBoolean("wallpaperColour", s.wallpaperColour)
            .putString("textSize", s.textSize.name)
            .putBoolean("darkPictures", s.darkPictures)
            .putBoolean("greeting", s.greeting)
            .putBoolean("examClock", s.examClock)
            .putBoolean("memes", s.memes)
            .putString("memeFrequency", s.memeFrequency.name)
            .apply()
        state.value = s
    }
}

/** The settings, for any screen that goes by one. */
val LocalSettings = staticCompositionLocalOf { Settings() }
