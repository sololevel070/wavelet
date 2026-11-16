package app.wavelet.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController

private data class Guide(val service: String, val color: Color, val steps: String, val link: String?)

private val guides = listOf(
    Guide("Spotify", Color(0xFF1E3264), "Open exportify.net, sign in with Spotify, and export the playlist as CSV. Then pick that file here.", "https://exportify.net"),
    Guide("Apple Music", Color(0xFFE8115B), "In the Music app on a computer: File › Library › Export Playlist, and save as M3U or plain text. Or use TuneMyMusic and choose \"Export to file\".", "https://www.tunemymusic.com"),
    Guide("YouTube Music", Color(0xFFBC5900), "Use TuneMyMusic or Soundiiz: pick YouTube Music as the source and \"Export to file\" (CSV).", "https://www.tunemymusic.com"),
    Guide("Other apps", Color(0xFF477D95), "Most players can save playlists as M3U, M3U8, XSPF or CSV. Wavelet reads all of them, plus its own JSON export.", null),
)

@Composable
fun ImportScreen(vm: MainViewModel, nav: NavHostController) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val lb by vm.lbPlaylists.collectAsStateWithLifecycle()
    var link by rememberSaveable { mutableStateOf("") }
    val uriHandler = LocalUriHandler.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::importFile) }
    LaunchedEffect(settings.listenBrainzUser) { vm.loadListenBrainzPlaylists() }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            Row(Modifier.statusBarsPadding(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                Text("Import a playlist", style = MaterialTheme.typography.headlineSmall)
            }
        }
        item {
            Text(
                "Bring playlists from other apps. Wavelet finds each song in your sources (this phone, your servers, Jamendo…). Songs it can't find are kept so you can match them later.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        item {
            Button(
                onClick = { picker.launch(arrayOf("*/*")) },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            ) {
                Icon(Icons.Rounded.Description, null)
                Spacer(Modifier.width(8.dp))
                Text("Choose a playlist file")
            }
        }
        item {
            Text("CSV · M3U · M3U8 · XSPF · JSPF · Wavelet JSON", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
        }

        item { SectionTitle("How to export from…") }
        items(guides) { g ->
            Column(
                Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .background(g.color).padding(16.dp),
            ) {
                Text(g.service, style = MaterialTheme.typography.titleMedium, color = Color.White)
                Text(g.steps, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.9f), modifier = Modifier.padding(top = 4.dp))
                if (g.link != null) TextButton(onClick = { uriHandler.openUri(g.link) }) { Text("Open ${g.link.removePrefix("https://")}", color = Color.White) }
            }
        }

        item { SectionTitle("From link (Spotify, YouTube Music, ListenBrainz)") }
        item {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = link, onValueChange = { link = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    label = { Text("Spotify, YouTube Music, or ListenBrainz link") },
                    leadingIcon = { Icon(Icons.Rounded.Podcasts, null) },
                )
                OutlinedButton(enabled = link.isNotBlank(), onClick = { vm.importFromLink(link) }) { Text("Import link") }
            }
        }
        if (settings.listenBrainzUser.isBlank()) {
            item {
                Text(
                    "Connect ListenBrainz in Settings to see your own playlists and Weekly Jams here.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        } else {
            when {
                lb.loading -> item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                lb.error != null -> item { Text(lb.error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
                lb.playlists.isEmpty() -> item { Text("No playlists on your ListenBrainz account yet.", modifier = Modifier.padding(16.dp)) }
                else -> items(lb.playlists, key = { it.id }) { p ->
                    ImportRow(Icons.AutoMirrored.Rounded.QueueMusic, p.title, listOfNotNull(p.creator?.let { "by $it" }, p.trackCount?.let { "$it songs" }).joinToString(" · ")) {
                        vm.importListenBrainz(p.id)
                    }
                }
            }
        }
    }
}

@Composable
private fun ImportRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) { Icon(icon, null) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("Import", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
    }
}
