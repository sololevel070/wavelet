package app.wavelet.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs

private data class WelcomePage(
    val icon: ImageVector,
    val title: String,
    val body: String,
    val colors: List<Color>,
)

private val pages = listOf(
    WelcomePage(
        Icons.Rounded.MusicNote,
        "Your music, all in one place",
        "Play songs from your phone, your own Navidrome, Jellyfin or Plex server, and free music catalogs like Audius, Jamendo and the Internet Archive.",
        listOf(Color(0xFF4B2BD6), Color(0xFF8B7CFF)),
    ),
    WelcomePage(
        Icons.Rounded.LibraryMusic,
        "Playlists you control",
        "Make playlists, import them from a file, or turn any folder on your phone into a playlist. Send a whole playlist to a friend as a ZIP.",
        listOf(Color(0xFFB4235A), Color(0xFFFF8A65)),
    ),
    WelcomePage(
        Icons.Rounded.FormatQuote,
        "Lyrics, widget and more",
        "Synced lyrics that follow the song, floating lyrics over other apps, a home-screen widget, a sleep timer and an equalizer.",
        listOf(Color(0xFF0F5132), Color(0xFF2FB380)),
    ),
)

/** The welcome tour shown the first time Wavelet opens. */
@Composable
fun OnboardingScreen(vm: MainViewModel, onRequestPermission: () -> Unit) {
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == pages.lastIndex

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.End) {
            if (!last) TextButton(onClick = { scope.launch { pager.animateScrollToPage(pages.lastIndex) } }) { Text("Skip") }
            else Spacer(Modifier.height(48.dp))
        }

        HorizontalPager(pager, Modifier.weight(1f)) { index ->
            val page = pages[index]
            // How far this page is from the centre: used to fade and slide it a little (parallax).
            val offset = (pager.currentPage - index) + pager.currentPageOffsetFraction
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp)
                    .graphicsLayer {
                        alpha = 1f - abs(offset).coerceIn(0f, 1f)
                        translationX = offset * size.width * 0.25f
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    Modifier
                        .size(168.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(page.colors))
                        .graphicsLayer {
                            val s = 1f - 0.15f * abs(offset).coerceIn(0f, 1f)
                            scaleX = s
                            scaleY = s
                        },
                    contentAlignment = Alignment.Center,
                ) { Icon(page.icon, null, tint = Color.White, modifier = Modifier.size(84.dp)) }
                Spacer(Modifier.height(40.dp))
                Text(page.title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Text(
                    page.body,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }

        // Page dots
        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            repeat(pages.size) { i ->
                val selected = pager.currentPage == i
                val width by animateDpAsState(if (selected) 24.dp else 8.dp, tween(300, easing = FastOutSlowInEasing), label = "dotWidth")
                val color by animateColorAsState(
                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    tween(300), label = "dotColor",
                )
                Box(Modifier.padding(horizontal = 4.dp).height(8.dp).size(width, 8.dp).clip(CircleShape).background(color))
            }
        }

        if (last) {
            Text(
                "Wavelet will ask to read your music files and to show playback controls.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 4.dp),
            )
        }
        Button(
            onClick = {
                if (last) {
                    onRequestPermission()
                    vm.finishOnboarding()
                } else {
                    scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                }
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp).height(52.dp),
        ) { Text(if (last) "Get started" else "Next", style = MaterialTheme.typography.titleMedium) }
    }
}
