package app.wavelet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoGraph
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import java.text.DateFormatSymbols
import java.util.Calendar

@Composable
fun StatsScreen(vm: MainViewModel, nav: NavHostController) {
    val state by vm.stats.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { if (vm.stats.value == null) vm.loadStats() }
    val s = state
    val now = Calendar.getInstance()
    val isCurrentMonth = s != null && s.year == now.get(Calendar.YEAR) && s.month == now.get(Calendar.MONTH)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            Row(Modifier.statusBarsPadding(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                Text("Your stats", style = MaterialTheme.typography.headlineSmall)
            }
        }
        if (s == null) return@LazyColumn
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.shiftStatsMonth(-1) }) { Icon(Icons.Rounded.ChevronLeft, "Previous month") }
                Text(
                    "${DateFormatSymbols.getInstance().months[s.month]} ${s.year}",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                IconButton(onClick = { vm.shiftStatsMonth(1) }, enabled = !isCurrentMonth) { Icon(Icons.Rounded.ChevronRight, "Next month") }
            }
        }
        val stats = s.stats
        if (s.loading || stats == null) {
            item { Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            return@LazyColumn
        }
        if (stats.plays == 0) {
            item { EmptyState(Icons.Rounded.AutoGraph, "No listening this month", "Play some music and your recap will build up here.") }
            return@LazyColumn
        }
        item {
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("Minutes listened", "%,d".format(stats.listenedMs / 60_000), listOf(Color(0xFF4B2BD6), Color(0xFF9D8CFF)), Modifier.weight(1f))
                StatTile("Songs played", "%,d".format(stats.plays), listOf(Color(0xFFB4235A), Color(0xFFFF8A65)), Modifier.weight(1f))
            }
        }
        stats.topArtists.firstOrNull()?.let { top ->
            item {
                Box(
                    Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFF0F5132), Color(0xFF2FB380)))).padding(20.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Artwork(top.artworkUrl, Modifier.size(84.dp).clip(CircleShape), corner = 0.dp, icon = Icons.Rounded.Person)
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text("Your top artist", style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.85f))
                            Text(top.artist, style = MaterialTheme.typography.headlineSmall, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text("${top.plays} plays · ${top.listenedMs / 60_000} min", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f))
                        }
                    }
                }
            }
        }
        if (stats.topTracks.isNotEmpty()) {
            item { SectionTitle("Top songs") }
            itemsIndexed(stats.topTracks, key = { _, t -> "t" + t.trackKey }) { i, t ->
                RankRow(i + 1, t.title, "${t.artist} · ${t.plays} plays", t.artworkUrl, circle = false)
            }
        }
        if (stats.topArtists.size > 1) {
            item { SectionTitle("Top artists") }
            itemsIndexed(stats.topArtists, key = { _, a -> "a" + a.artist }) { i, a ->
                RankRow(i + 1, a.artist, "${a.plays} plays · ${a.listenedMs / 60_000} min", a.artworkUrl, circle = true)
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, colors: List<Color>, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(Brush.linearGradient(colors)).padding(16.dp)) {
        Text(value, style = MaterialTheme.typography.headlineLarge, color = Color.White)
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f))
    }
}

@Composable
private fun RankRow(rank: Int, title: String, subtitle: String, art: String?, circle: Boolean) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("$rank", style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(32.dp))
        Artwork(art, Modifier.size(52.dp).then(if (circle) Modifier.clip(CircleShape) else Modifier), corner = if (circle) 0.dp else 4.dp, icon = if (circle) Icons.Rounded.Person else null)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}
