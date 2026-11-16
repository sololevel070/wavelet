package app.wavelet.ui

import android.content.Intent
import android.media.audiofx.AudioEffect
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoGraph
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import app.wavelet.data.ThemeMode

@Composable
fun SettingsScreen(vm: MainViewModel, nav: NavHostController) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val connecting by vm.lbConnecting.collectAsStateWithLifecycle()
    val lastFmConnecting by vm.lastFmConnecting.collectAsStateWithLifecycle()
    var lbDialog by rememberSaveable { mutableStateOf(false) }
    var lastFmDialog by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(vm::exportBackup)
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::importBackup)
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            Row(Modifier.statusBarsPadding(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                Text("Settings", style = MaterialTheme.typography.headlineSmall)
            }
        }

        item { Group("Music") }
        item { NavRow(Icons.Rounded.Extension, "Sources", "Turn sources on or off, add servers and repos") { nav.navigate(Routes.SOURCES) } }
        item { NavRow(Icons.Rounded.FileUpload, "Import a playlist", "From Spotify, Apple Music, YouTube Music, files or ListenBrainz") { nav.navigate(Routes.IMPORT) } }

        item { Group("Appearance") }
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Icon(Icons.Rounded.DarkMode, null)
                    Text("Theme", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 16.dp))
                }
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    ThemeMode.entries.forEachIndexed { i, mode ->
                        SegmentedButton(
                            selected = settings.theme == mode,
                            onClick = { vm.updateSettings { it.copy(theme = mode) } },
                            shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size),
                        ) { Text(mode.name) }
                    }
                }
            }
        }
        item {
            ToggleRow(Icons.Rounded.Palette, "Colours from artwork", "Tint the player with the colours of the song's cover", settings.artworkColors) { on ->
                vm.updateSettings { it.copy(artworkColors = on) }
            }
        }
        if (android.os.Build.VERSION.SDK_INT >= 31) item {
            ToggleRow(Icons.Rounded.ColorLens, "Wallpaper colours", "Use your phone's Material You colours", settings.dynamicColor) { on ->
                vm.updateSettings { it.copy(dynamicColor = on) }
            }
        }

        item { Group("Playback") }
        item {
            ToggleRow(Icons.Rounded.CloudOff, "Prefer downloaded copies", "Play the offline file when you have one", settings.preferOffline) { on ->
                vm.updateSettings { it.copy(preferOffline = on) }
            }
        }
        item {
            NavRow(Icons.Rounded.Equalizer, "Equalizer", "Open your phone's sound effects") {
                val intent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL)
                    .putExtra(AudioEffect.EXTRA_AUDIO_SESSION, vm.audioSessionId)
                    .putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                    .putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
                runCatching { context.startActivity(intent) }.onFailure { vm.say("No equalizer app found on this phone") }
            }
        }

        item {
            ToggleRow(Icons.Rounded.PlayCircle, "Resume where you left off", "Bring back your last queue when Wavelet opens", settings.resumeQueue) { on ->
                vm.updateSettings { it.copy(resumeQueue = on) }
            }
        }

        item { Group("Floating lyrics & widget") }
        item {
            val hasOverlay = android.os.Build.VERSION.SDK_INT < 23 || android.provider.Settings.canDrawOverlays(context)
            ToggleRow(
                Icons.Rounded.Lyrics, "Floating lyrics",
                if (settings.floatingLyrics && !hasOverlay) "Allow \"Display over other apps\" to show the bubble"
                else "Synced lyrics in a bubble over other apps. Drag to move, tap to open Wavelet.",
                settings.floatingLyrics,
            ) { on ->
                vm.updateSettings { it.copy(floatingLyrics = on) }
                if (on && !hasOverlay) {
                    runCatching {
                        context.startActivity(
                            Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:" + context.packageName)),
                        )
                    }
                }
            }
        }
        if (settings.floatingLyrics) {
            item {
                Column(Modifier.padding(start = 56.dp, end = 16.dp, bottom = 8.dp)) {
                    Text("Text size", style = MaterialTheme.typography.bodyMedium)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                        listOf("Small", "Medium", "Large").forEachIndexed { i, label ->
                            SegmentedButton(
                                selected = settings.floatingLyricsSize == i,
                                onClick = { vm.updateSettings { it.copy(floatingLyricsSize = i) } },
                                shape = SegmentedButtonDefaults.itemShape(i, 3),
                            ) { Text(label) }
                        }
                    }
                }
            }
            item {
                ToggleRow(Icons.Rounded.VisibilityOff, "Hide while Wavelet is open", null, settings.floatingLyricsHideInApp) { on ->
                    vm.updateSettings { it.copy(floatingLyricsHideInApp = on) }
                }
            }
        }
        item {
            NavRow(Icons.Rounded.Widgets, "Add widget to home screen", "Artwork, song and controls on your home screen") {
                val manager = android.appwidget.AppWidgetManager.getInstance(context)
                val ok = android.os.Build.VERSION.SDK_INT >= 26 && manager.isRequestPinAppWidgetSupported &&
                    manager.requestPinAppWidget(android.content.ComponentName(context, app.wavelet.player.PlayerWidget::class.java), null, null)
                if (!ok) vm.say("Long-press your home screen, choose Widgets, and find Wavelet")
            }
        }

        item { Group("Listening & Sound") }
        item {
            NavRow(
                Icons.Rounded.Equalizer, "Equalizer & Audio FX",
                "5-band equalizer, presets, bass boost, and surround",
            ) { vm.openEqualizer() }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Crossfade", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (settings.crossfadeSeconds > 0) "${settings.crossfadeSeconds}s" else "Off",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Slider(
                    value = settings.crossfadeSeconds.toFloat(),
                    onValueChange = { vm.updateSettings { s -> s.copy(crossfadeSeconds = it.toInt()) } },
                    valueRange = 0f..10f,
                    steps = 9,
                )
            }
        }
        item {
            ToggleRow(Icons.Rounded.History, "Save listening history", "Needed for Recently played and your stats", settings.recordHistory) { on ->
                vm.updateSettings { it.copy(recordHistory = on) }
            }
        }
        item { NavRow(Icons.Rounded.History, "Recently played", null) { nav.navigate(Routes.HISTORY) } }
        item { NavRow(Icons.Rounded.AutoGraph, "Your stats", "Monthly recap of top songs and artists") { nav.navigate(Routes.STATS) } }
        item {
            NavRow(
                Icons.Rounded.Podcasts, "ListenBrainz",
                if (settings.listenBrainzUser.isNotBlank()) "Connected as ${settings.listenBrainzUser}" else "Scrobble what you play and import playlists",
            ) { lbDialog = true }
        }
        if (settings.listenBrainzUser.isNotBlank()) item {
            ToggleRow(Icons.Rounded.Podcasts, "Send listens to ListenBrainz", null, settings.scrobble) { on ->
                vm.updateSettings { it.copy(scrobble = on) }
            }
        }
        item {
            NavRow(
                Icons.Rounded.Podcasts, "Last.fm",
                if (settings.lastFmUser.isNotBlank()) "Connected as ${settings.lastFmUser}" else "Scrobble what you play to Last.fm",
            ) { lastFmDialog = true }
        }
        if (settings.lastFmUser.isNotBlank()) item {
            ToggleRow(Icons.Rounded.Podcasts, "Send listens to Last.fm", null, settings.lastFmScrobble) { on ->
                vm.updateSettings { it.copy(lastFmScrobble = on) }
            }
        }

        item { Group("Backup") }
        item { NavRow(Icons.Rounded.Backup, "Back up library", "Save playlists, liked songs, history and sources to a file") { backupLauncher.launch("wavelet-backup.json") } }
        item { NavRow(Icons.Rounded.Restore, "Restore from backup", "Adds the backup's playlists and likes to this phone") { restoreLauncher.launch(arrayOf("application/json", "*/*")) } }

        item { Group("About") }
        item { NavRow(Icons.Rounded.Info, "Wavelet 0.3.0", "An open music player for legal, open sources", showArrow = false) {} }
    }

    if (lbDialog) ListenBrainzDialog(vm, settings.listenBrainzUser, connecting, onClose = { lbDialog = false })
    if (lastFmDialog) LastFmDialog(vm, settings.lastFmUser, lastFmConnecting, onClose = { lastFmDialog = false })
}

@Composable
private fun Group(title: String) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 4.dp))
}

@Composable
private fun NavRow(icon: ImageVector, title: String, subtitle: String?, showArrow: Boolean = true, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { Icon(icon, null) },
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        trailingContent = if (showArrow) ({ Icon(Icons.AutoMirrored.Rounded.ArrowForward, null) }) else null,
    )
}

@Composable
private fun ToggleRow(icon: ImageVector, title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        modifier = Modifier.clickable { onChange(!checked) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { Icon(icon, null) },
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
    )
}

@Composable
private fun ListenBrainzDialog(vm: MainViewModel, user: String, connecting: Boolean, onClose: () -> Unit) {
    var token by rememberSaveable { mutableStateOf("") }
    val uriHandler = LocalUriHandler.current
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("ListenBrainz") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (user.isNotBlank()) {
                    Text("Connected as $user. Songs you play for at least half their length (or 4 minutes) are sent to your ListenBrainz profile.")
                } else {
                    Text("ListenBrainz is a free, open service that keeps your listening history. Paste your user token to connect.")
                    TextButton(onClick = { uriHandler.openUri("https://listenbrainz.org/settings/") }) { Text("Get my token at listenbrainz.org") }
                    OutlinedTextField(
                        value = token, onValueChange = { token = it }, singleLine = true, label = { Text("User token") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    )
                    if (connecting) CircularProgressIndicator()
                }
            }
        },
        confirmButton = {
            if (user.isNotBlank()) TextButton(onClick = { vm.disconnectListenBrainz(); onClose() }) { Text("Disconnect") }
            else TextButton(enabled = token.isNotBlank() && !connecting, onClick = { vm.connectListenBrainz(token) }) { Text("Connect") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text(if (user.isNotBlank()) "Close" else "Cancel") } },
    )
    if (user.isNotBlank() && token.isNotBlank()) token = ""
}

@Composable
private fun LastFmDialog(vm: MainViewModel, user: String, connecting: Boolean, onClose: () -> Unit) {
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var apiKey by rememberSaveable { mutableStateOf("") }
    var secret by rememberSaveable { mutableStateOf("") }
    val uriHandler = LocalUriHandler.current

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Last.fm") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (user.isNotBlank()) {
                    Text("Connected as $user. Played tracks are automatically scrobbled to your Last.fm profile.")
                } else {
                    Text("Connect your Last.fm account to scrobble songs you listen to.")
                    TextButton(onClick = { uriHandler.openUri("https://www.last.fm/api/account/create") }) {
                        Text("Get free API account at last.fm/api")
                    }
                    OutlinedTextField(
                        value = username, onValueChange = { username = it }, singleLine = true, label = { Text("Last.fm Username") },
                    )
                    OutlinedTextField(
                        value = password, onValueChange = { password = it }, singleLine = true, label = { Text("Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    )
                    OutlinedTextField(
                        value = apiKey, onValueChange = { apiKey = it }, singleLine = true, label = { Text("API Key") },
                    )
                    OutlinedTextField(
                        value = secret, onValueChange = { secret = it }, singleLine = true, label = { Text("API Secret") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    )
                    if (connecting) CircularProgressIndicator(modifier = Modifier.padding(top = 8.dp))
                }
            }
        },
        confirmButton = {
            if (user.isNotBlank()) TextButton(onClick = { vm.disconnectLastFm(); onClose() }) { Text("Disconnect") }
            else TextButton(
                enabled = username.isNotBlank() && password.isNotBlank() && apiKey.isNotBlank() && secret.isNotBlank() && !connecting,
                onClick = { vm.connectLastFm(username, password, apiKey, secret) },
            ) { Text("Connect") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text(if (user.isNotBlank()) "Close" else "Cancel") } },
    )
}
