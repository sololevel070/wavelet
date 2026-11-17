package app.wavelet.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController

@Composable
fun AboutScreen(vm: MainViewModel, nav: NavHostController) {
    val context = LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
        Row(Modifier.statusBarsPadding(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
            Text("About", style = MaterialTheme.typography.headlineSmall)
        }
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(96.dp).clip(RoundedCornerShape(24.dp)).background(Brush.linearGradient(listOf(Color(0xFF4B2BD6), Color(0xFF8B7CFF)))),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.GraphicEq, null, tint = Color.White, modifier = Modifier.size(56.dp)) }
            Spacer(Modifier.height(16.dp))
            Text("Wavelet", style = MaterialTheme.typography.headlineMedium)
            Text("Version $version", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Text(
                "A music player for your own files, your own servers and free music catalogs.",
                style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center,
            )
        }

        AboutGroup("Music and data come from") {
            Text("Your phone and any folder you pick", style = MaterialTheme.typography.bodyMedium)
            Text("Navidrome / Subsonic, Jellyfin and Plex servers you add", style = MaterialTheme.typography.bodyMedium)
            Text("Audius, Jamendo, Internet Archive and Radio Browser", style = MaterialTheme.typography.bodyMedium)
            Text("Lyrics from LRCLIB; listening history to ListenBrainz and Last.fm if you connect them", style = MaterialTheme.typography.bodyMedium)
        }
        AboutGroup("Built with") {
            Text("Kotlin and Jetpack Compose, Material 3, Media3 (ExoPlayer), Room, OkHttp, Coil and AndroidX Palette", style = MaterialTheme.typography.bodyMedium)
        }
        AboutGroup("Your data") {
            Text(
                "Playlists, likes and history are stored on this phone. Backups and exports go only where you save them. Server passwords are kept in the app's private storage.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { vm.updateSettings { it.copy(onboarded = false) } },
            modifier = Modifier.padding(horizontal = 16.dp),
        ) { Text("Replay the welcome tour") }
    }
}

@Composable
private fun AboutGroup(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        content()
    }
}
