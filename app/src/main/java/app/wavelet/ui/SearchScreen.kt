package app.wavelet.ui

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.rotate
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController

private val browseTiles = listOf(
    "Pop", "Rock", "Jazz", "Hip hop", "Electronic", "Classical", "Lo-fi", "Ambient",
    "Indie", "Blues", "Reggae", "Folk", "Metal", "Soul", "Bollywood", "News",
)

@Composable
fun SearchScreen(vm: MainViewModel, nav: NavHostController) {
    val state by vm.search.collectAsStateWithLifecycle()
    val sources by vm.musicSources.collectAsStateWithLifecycle()
    val focus = LocalFocusManager.current

    Column(Modifier.fillMaxSize()) {
        Text("Search", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.statusBarsPadding().padding(start = 16.dp, top = 12.dp, bottom = 8.dp))
        TextField(
            value = state.query,
            onValueChange = vm::setQuery,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            placeholder = { Text("What do you want to listen to?") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = {
                if (state.query.isNotEmpty()) IconButton(onClick = { vm.setQuery("") }) { Icon(Icons.Rounded.Close, "Clear search") }
            },
            singleLine = true,
            shape = RoundedCornerShape(8.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.onSurface,
                unfocusedContainerColor = MaterialTheme.colorScheme.onSurface,
                focusedTextColor = MaterialTheme.colorScheme.surface,
                unfocusedTextColor = MaterialTheme.colorScheme.surface,
                focusedPlaceholderColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                unfocusedPlaceholderColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                focusedLeadingIconColor = MaterialTheme.colorScheme.surface,
                unfocusedLeadingIconColor = MaterialTheme.colorScheme.surface,
                focusedTrailingIconColor = MaterialTheme.colorScheme.surface,
                unfocusedTrailingIconColor = MaterialTheme.colorScheme.surface,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = MaterialTheme.colorScheme.primary,
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                vm.submitSearch()
                focus.clearFocus()
            }),
        )

        if (state.submitted.isBlank() || (state.query != state.submitted && state.query.isNotBlank())) {
            val recent by vm.recentSearches.collectAsStateWithLifecycle()
            val featured by vm.featured.collectAsStateWithLifecycle()
            val art = featured.flatMap { it.tracks }.mapNotNull { it.artworkUrl }.distinct()
            val shownRecent = if (state.query.isBlank()) recent.take(6) else recent.filter { it.contains(state.query.trim(), ignoreCase = true) }
            BrowseGrid(
                recent = shownRecent,
                artworks = art,
                showTiles = state.query.isBlank(),
                onPick = { term ->
                    vm.submitSearch(term)
                    focus.clearFocus()
                },
                onRemoveRecent = vm::removeRecentSearch,
                onClearRecent = vm::clearRecentSearches,
            )
            return@Column
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { FilterChip(selected = state.filter == null, onClick = { focus.clearFocus(); vm.setFilter(null) }, label = { Text("All") }) }
            items(sources, key = { it.id }) { s ->
                FilterChip(selected = state.filter == s.id, onClick = { focus.clearFocus(); vm.setFilter(s.id) }, label = { Text(s.name) })
            }
        }

        val allDone = state.sections.isNotEmpty() && state.sections.none { it.loading }
        val nothing = allDone && state.sections.all { it.tracks.isEmpty() }

        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            if (nothing) item {
                EmptyState(Icons.Rounded.SearchOff, "No results for \"${state.submitted}\"", "Check the spelling, try fewer words, or turn on more sources.")
            }
            state.sections.forEach { section ->
                if (!section.loading && section.tracks.isEmpty() && section.error == null) return@forEach
                item(key = "header-${section.sourceId}") {
                    Text(
                        section.sourceName,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                    )
                }
                when {
                    section.loading -> item(key = "loading-${section.sourceId}") {
                        SongRowSkeleton(2)
                    }
                    section.error != null -> item(key = "error-${section.sourceId}") {
                        Text(section.error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))
                    }
                    else -> {
                        val shown = if (state.filter == null) section.tracks.take(6) else section.tracks
                        items(shown, key = { "${section.sourceId}/${it.key}" }) { t ->
                            SongRow(vm, t, onClick = { vm.play(t, section.tracks) })
                        }
                        if (state.filter == null && section.tracks.size > 6) item(key = "more-${section.sourceId}") {
                            Text(
                                "See all ${section.tracks.size} from ${section.sourceName}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable { vm.setFilter(section.sourceId) }.padding(horizontal = 16.dp, vertical = 10.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BrowseGrid(
    recent: List<String>,
    artworks: List<String>,
    showTiles: Boolean,
    onPick: (String) -> Unit,
    onRemoveRecent: (String) -> Unit,
    onClearRecent: () -> Unit,
) {
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        if (recent.isNotEmpty()) {
            item {
                Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Recent searches", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    if (showTiles) androidx.compose.material3.TextButton(onClick = onClearRecent) { Text("Clear") }
                }
            }
            items(recent, key = { "r$it" }) { q ->
                Row(
                    Modifier.fillMaxWidth().clickable { onPick(q) }.padding(start = 16.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(48.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.History, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.size(12.dp))
                    Text(q, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1)
                    IconButton(onClick = { onRemoveRecent(q) }) { Icon(Icons.Rounded.Close, "Remove $q", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
        if (!showTiles) return@LazyColumn
        item { Text("Browse all", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 12.dp)) }
        items(browseTiles.chunked(2)) { row ->
            Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { term ->
                    val i = browseTiles.indexOf(term)
                    Box(
                        Modifier
                            .weight(1f)
                            .height(100.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(TileColors[i % TileColors.size])
                            .clickable { onPick(term) },
                    ) {
                        Text(term, style = TileText, modifier = Modifier.padding(12.dp))
                        // Tilted cover in the corner, like the kit's genre tiles.
                        artworks.getOrNull(i)?.let { url ->
                            Artwork(
                                url,
                                Modifier
                                    .size(68.dp)
                                    .align(Alignment.BottomEnd)
                                    .offset(x = 14.dp, y = 4.dp)
                                    .rotate(25f),
                                corner = 2.dp,
                            )
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}
