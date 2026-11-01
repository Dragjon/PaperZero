package dev.olaf.paperzero.ui

import dev.olaf.paperzero.core.design.skinCard
import dev.olaf.paperzero.core.design.SkinHeading
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.olaf.paperzero.MemeFrequency
import dev.olaf.paperzero.R
import dev.olaf.paperzero.Settings
import dev.olaf.paperzero.TextSize
import dev.olaf.paperzero.core.data.Progresses
import dev.olaf.paperzero.core.design.ChalkOlaf
import dev.olaf.paperzero.core.design.Ground
import dev.olaf.paperzero.core.design.PaperTheme
import dev.olaf.paperzero.core.design.Skin
import dev.olaf.paperzero.core.design.ThemeMode
import kotlinx.coroutines.launch

/**
 * The settings: how the app looks and reads, how practice goes, memes in the feeds, and the
 * answers kept on the phone, to start again from nothing.
 */
@Composable
fun SettingsScreen(settings: Settings, onChange: ((Settings) -> Settings) -> Unit, progresses: Progresses, onBack: () -> Unit) {
    var confirming by remember { mutableStateOf<Wipe?>(null) }
    var done by remember { mutableStateOf<Wipe?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val version = context.packageManager.getPackageInfo(context.packageName, 0).versionName

    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.settings), null, onBack)
        Column(
            Modifier
                .align(Alignment.CenterHorizontally)
                .widthIn(max = 720.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Section(stringResource(R.string.settings_look)) {
                SkinPicker(settings) { v -> onChange { it.copy(skin = v) } }
                Choice(stringResource(R.string.settings_theme), null, ThemeMode.entries, settings.theme, {
                    stringResource(when (it) { ThemeMode.System -> R.string.theme_system; ThemeMode.Light -> R.string.theme_light; ThemeMode.Dark -> R.string.theme_dark })
                }) { v -> onChange { it.copy(theme = v) } }
                // A skin brings its own ground, so the background is only a choice in the classic one.
                if (settings.skin == Skin.Classic) Choice(stringResource(R.string.settings_ground), stringResource(R.string.settings_ground_description), Ground.entries, settings.ground, {
                    stringResource(when (it) { Ground.Warm -> R.string.ground_warm; Ground.Neutral -> R.string.ground_neutral; Ground.Black -> R.string.ground_black })
                }) { v -> onChange { it.copy(ground = v) } }
                Toggle(stringResource(R.string.settings_science_colours), stringResource(R.string.settings_science_colours_description), settings.scienceColours) { v -> onChange { it.copy(scienceColours = v) } }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Toggle(stringResource(R.string.settings_wallpaper), stringResource(R.string.settings_wallpaper_description), settings.wallpaperColour) { v -> onChange { it.copy(wallpaperColour = v) } }
                }
            }
            Section(stringResource(R.string.settings_reading)) {
                Choice(stringResource(R.string.settings_text_size), stringResource(R.string.settings_text_size_description), TextSize.entries, settings.textSize, {
                    stringResource(when (it) { TextSize.Small -> R.string.text_small; TextSize.Default -> R.string.text_default; TextSize.Large -> R.string.text_large; TextSize.Larger -> R.string.text_larger })
                }) { v -> onChange { it.copy(textSize = v) } }
                Toggle(stringResource(R.string.settings_dark_pictures), stringResource(R.string.settings_dark_pictures_description), settings.darkPictures) { v -> onChange { it.copy(darkPictures = v) } }
            }
            Section(stringResource(R.string.settings_practice)) {
                Toggle(stringResource(R.string.settings_exam_clock), stringResource(R.string.settings_exam_clock_description), settings.examClock) { v -> onChange { it.copy(examClock = v) } }
                Toggle(stringResource(R.string.settings_greeting), stringResource(R.string.settings_greeting_description), settings.greeting) { v -> onChange { it.copy(greeting = v) } }
            }
            Section(stringResource(R.string.settings_memes)) {
                Toggle(stringResource(R.string.settings_memes_on), stringResource(R.string.settings_memes_description), settings.memes) { v -> onChange { it.copy(memes = v) } }
                if (settings.memes) {
                    Choice(stringResource(R.string.settings_meme_frequency), stringResource(R.string.settings_meme_frequency_description), MemeFrequency.entries, settings.memeFrequency, {
                        stringResource(when (it) { MemeFrequency.Rare -> R.string.memes_rare; MemeFrequency.Sometimes -> R.string.memes_sometimes; MemeFrequency.Often -> R.string.memes_often })
                    }) { v -> onChange { it.copy(memeFrequency = v) } }
                }
            }
            Section(stringResource(R.string.settings_data)) {
                Action(stringResource(R.string.settings_reset), stringResource(R.string.settings_reset_description)) { confirming = Wipe.Progress }
                Action(stringResource(R.string.settings_delete_saved), stringResource(R.string.settings_delete_saved_description)) { confirming = Wipe.Saved }
            }
            Section(stringResource(R.string.settings_about_title)) {
                Link(Icons.Rounded.Code, stringResource(R.string.settings_github), stringResource(R.string.settings_github_description)) {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB)))
                }
            }
            Text(
                stringResource(R.string.settings_about, version ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
    }

    confirming?.let { wipe ->
        AlertDialog(
            onDismissRequest = { confirming = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            title = { Text(stringResource(if (wipe == Wipe.Progress) R.string.settings_reset_title else R.string.settings_delete_saved_title)) },
            text = { Text(stringResource(if (wipe == Wipe.Progress) R.string.settings_reset_text else R.string.settings_delete_saved_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirming = null
                    scope.launch {
                        if (wipe == Wipe.Progress) progresses.resetProgress() else progresses.deleteSaved()
                        done = wipe
                    }
                }) { Text(stringResource(if (wipe == Wipe.Progress) R.string.settings_reset_confirm else R.string.settings_delete_saved_confirm), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirming = null }) { Text(stringResource(R.string.cancel), color = MaterialTheme.colorScheme.onSurfaceVariant) } },
        )
    }
    done?.let { wipe ->
        AlertDialog(
            onDismissRequest = { done = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            text = { Text(stringResource(if (wipe == Wipe.Progress) R.string.settings_reset_done else R.string.settings_delete_saved_done)) },
            confirmButton = { TextButton(onClick = { done = null }) { Text(stringResource(R.string.ok)) } },
        )
    }
}

private enum class Wipe { Progress, Saved }

/** Where PaperZero's code lives. */
private const val GITHUB = "https://github.com/Dragjon/PaperZero"

/** A row that opens somewhere outside the app. */
@Composable
private fun Link(icon: ImageVector, title: String, description: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Label(title, description, Modifier.weight(1f))
        Spacer(Modifier.width(16.dp))
        Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    SkinHeading(title, Modifier.fillMaxWidth().padding(start = 8.dp, top = 12.dp))
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth().skinCard(MaterialTheme.shapes.extraLarge)) {
        Column(Modifier.padding(vertical = 8.dp)) { content() }
    }
}

@Composable
private fun Label(title: String, description: String?, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        if (description != null) {
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Toggle(title: String, description: String?, on: Boolean, onSet: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onSet(!on) }.padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Label(title, description, Modifier.weight(1f))
        Spacer(Modifier.width(16.dp))
        Switch(checked = on, onCheckedChange = onSet)
    }
}

/** One of a few, as chips under its name. */
@Composable
private fun <T> Choice(title: String, description: String?, options: List<T>, chosen: T, name: @Composable (T) -> String, onChoose: (T) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
        Label(title, description)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { o -> Chip(name(o), o == chosen, subtle = true) { onChoose(o) } }
        }
    }
}

@Composable
private fun Action(title: String, description: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
        Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The skins, each as a little card in its own colours with Olaf dressed for it. */
@Composable
private fun SkinPicker(settings: Settings, onChoose: (Skin) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Label(stringResource(R.string.settings_skin), stringResource(R.string.settings_skin_description), Modifier.padding(horizontal = 20.dp))
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Skin.entries.forEach { skin ->
                val name = stringResource(skinName(skin))
                val chosen = skin == settings.skin
                // Each card is drawn in its own skin, light or dark as the app is set.
                PaperTheme(themeMode = settings.theme, skin = skin) {
                    val colors = MaterialTheme.colorScheme
                    Surface(
                        selected = chosen,
                        onClick = { onChoose(skin) },
                        shape = MaterialTheme.shapes.large,
                        color = colors.background,
                        border = if (chosen) BorderStroke(3.dp, colors.primary) else BorderStroke(1.dp, colors.outlineVariant),
                        modifier = Modifier.width(112.dp),
                    ) {
                        Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            ChalkOlaf(Modifier.fillMaxWidth().height(80.dp), skin)
                            Spacer(Modifier.height(4.dp))
                            Text(name, style = MaterialTheme.typography.labelLarge, color = colors.onBackground)
                        }
                    }
                }
            }
        }
    }
}

private fun skinName(skin: Skin) = when (skin) {
    Skin.Classic -> R.string.skin_classic
    Skin.Chalkboard -> R.string.skin_chalkboard
    Skin.Summer -> R.string.skin_summer
    Skin.Aurora -> R.string.skin_aurora
    Skin.Lab -> R.string.skin_lab
    Skin.Sakura -> R.string.skin_sakura
    Skin.Halloween -> R.string.skin_halloween
}
