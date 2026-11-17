package app.wavelet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoGraph
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** One tile in the "Recently played" row: a playlist, a collection or a song. */
private data class RecentTile(val key: String, val title: String, val circle: Boolean, val onClick: () -> Unit, val cover: @Composable (Modifier) -> Unit)

@Composable
fun HomeScreen(vm: MainViewModel, nav: NavHostController) {
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val liked by vm.likedTracks.collectAsStateWithLifecycle()
    val recent by vm.recentTracks.collectAsStateWithLifecycle()
    val featured by vm.featured.collectAsStateWithLifecycle()
    val downloads by vm.downloads.collectAsStateWithLifecycle()
    val greeting = remember {
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 5..11 -> "Good morning"
            in 12..17 -> "Good afternoon"
            else -> "Good evening"
        }
    }
    val month = remember { SimpleDateFormat("MMMM", Locale.getDefault()).format(Date()) }

    val tiles = buildList {
        add(RecentTile("liked", "Liked Songs", false, { nav.navigate(Routes.LIKED) }) { LikedCover(it, corner = 0.dp) })
        playlists.take(3).forEach { p ->
            add(RecentTile("p${p.id}", p.name, false, { nav.navigate(Routes.playlist(p.id)) }) { MosaicCover(p.coverUrls, it, corner = 0.dp) })
        }
        // Recent artists show as circles, like the kit.
        recent.filterNot { it.isLive }.map { it.artist }.filter { it.isNotBlank() }.distinct().take(3).forEach { artist ->
            val art = recent.firstOrNull { it.artist == artist }?.artworkUrl
            add(RecentTile("a$artist", artist, true, { nav.navigate(Routes.artist(artist)) }) { Artwork(art, it, corner = 0.dp) })
        }
        recent.take(8).forEach { t ->
            add(RecentTile("t${t.key}", t.title, false, { vm.play(t, recent) }) { Artwork(t.artworkUrl, it, live = t.isLive, corner = 0.dp) })
        }
        if (downloads.isNotEmpty()) add(RecentTile("dl", "Downloads", false, { nav.navigate(Routes.DOWNLOADS) }) {
            Artwork(null, it, corner = 0.dp, icon = Icons.Rounded.CloudDownload)
        })
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Row(
                Modifier.statusBarsPadding().padding(start = 16.dp, end = 4.dp, top = 16.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(greeting, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                IconButton(onClick = { nav.navigate(Routes.HISTORY) }) { Icon(Icons.Rounded.History, "Recently played") }
                IconButton(onClick = { nav.navigate(Routes.STATS) }) { Icon(Icons.Rounded.AutoGraph, "Your stats") }
                IconButton(onClick = { nav.navigate(Routes.SETTINGS) }) { Icon(Icons.Rounded.Settings, "Settings") }
            }
        }

        item {
            Text("Recently played", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp))
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                items(tiles, key = { it.key }) { t ->
                    Column(Modifier.width(106.dp).clickable(onClick = t.onClick), horizontalAlignment = if (t.circle) Alignment.CenterHorizontally else Alignment.Start) {
                        t.cover(Modifier.size(106.dp).clip(if (t.circle) CircleShape else RoundedCornerShape(0.dp)))
                        Spacer(Modifier.height(8.dp))
                        Text(t.title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }

        // "Your month in review", styled after the kit's Wrapped section.
        item {
            Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                RecapArt(Modifier.size(58.dp), "${month.take(3)}")
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("#WAVELETRECAP", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Your $month in review", style = MaterialTheme.typography.headlineSmall)
                }
            }
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                item {
                    RecapCard("Your Top Songs", month, listOf(Color(0xFFD7F26B), Color(0xFFB9E64B)), Color(0xFF4A2B63)) { nav.navigate(Routes.STATS) }
                }
                item {
                    RecapCard("Your Artists Revealed", month, listOf(Color(0xFFC9B6F2), Color(0xFFA48BEA)), Color(0xFF3A2BD6)) { nav.navigate(Routes.STATS) }
                }
                if (liked.isNotEmpty()) item {
                    RecapCard("Your Liked Songs", "${liked.size} songs", listOf(Color(0xFF4B2BD6), Color(0xFFB7A9FF)), Color.White) { nav.navigate(Routes.LIKED) }
                }
            }
        }

        if (playlists.isNotEmpty()) {
            item {
                SectionTitle("Your playlists")
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(playlists, key = { it.id }) { p ->
                        SquareCard(p.name, "${p.count} songs", onClick = { nav.navigate(Routes.playlist(p.id)) }, width = 160.dp) {
                            MosaicCover(p.coverUrls, Modifier.fillMaxSize(), corner = 0.dp)
                        }
                    }
                }
            }
        }

        if (recent.isNotEmpty()) item { TrackShelf(vm, "Jump back in", recent) }
        if (liked.isNotEmpty()) item { TrackShelf(vm, "From your Liked Songs", liked.take(20)) }

        featured.forEach { section ->
            item(key = "featured-${section.sourceId}") {
                when {
                    section.loading -> {
                        ShelfSkeleton()
                    }
                    section.tracks.isNotEmpty() -> TrackShelf(
                        vm,
                        if (section.tracks.firstOrNull()?.isLive == true) "Top stations on ${section.sourceName}" else "Popular on ${section.sourceName}",
                        section.tracks,
                    )
                }
            }
        }

        if (featured.isEmpty()) {
            item {
                EmptyState(
                    Icons.Rounded.Extension, "Add some music sources",
                    "Turn on internet radio, the Internet Archive, Jamendo or your own Navidrome, Jellyfin or Plex server.",
                ) { OutlinedButton(onClick = { nav.navigate(Routes.SOURCES) }) { Text("Open sources") } }
            }
        }
    }
}

@Composable
private fun RecapArt(modifier: Modifier, text: String) {
    Box(
        modifier.background(Brush.linearGradient(listOf(Color(0xFFF59B7C), Color(0xFF8B7CFF), Color(0xFFD7F26B)))),
        contentAlignment = Alignment.Center,
    ) { Text(text, fontWeight = FontWeight.Black, color = Color(0xFF241A3A), fontSize = 16.sp) }
}

/** Big square card with typographic cover, like the kit's Wrapped cards. */
@Composable
private fun RecapCard(title: String, caption: String, colors: List<Color>, ink: Color, onClick: () -> Unit) {
    Column(Modifier.width(170.dp).clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).background(Brush.linearGradient(colors)).padding(12.dp)) {
            Text(
                title,
                color = ink,
                fontWeight = FontWeight.Black,
                fontSize = 22.sp,
                lineHeight = 24.sp,
                modifier = Modifier.align(Alignment.BottomStart),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text("$title · $caption", style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
