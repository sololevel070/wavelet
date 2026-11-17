package app.wavelet.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController

private enum class LibFilter(val label: String) { Playlists("Playlists"), Albums("Albums"), Artists("Artists"), Downloaded("Downloaded") }
private enum class LibSort(val label: String) { Recent("Recently updated"), Alphabetical("Alphabetical") }

@Composable
fun LibraryScreen(vm: MainViewModel, nav: NavHostController, onRequestPermission: () -> Unit) {
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val liked by vm.likedTracks.collectAsStateWithLifecycle()
    val downloads by vm.downloads.collectAsStateWithLifecycle()
    val local by vm.local.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf<LibFilter?>(null) }
    var sort by rememberSaveable { mutableStateOf(LibSort.Recent) }
    var sortMenu by rememberSaveable { mutableStateOf(false) }
    var creating by rememberSaveable { mutableStateOf(false) }
    var grid by rememberSaveable { mutableStateOf(false) }
    var addMenu by rememberSaveable { mutableStateOf(false) }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { vm.addFolderPlaylist(it) { id -> nav.navigate(Routes.playlist(id)) } }
    }

    val libraryTracks = (local.tracks + downloads).filterNot { it.isLive }
    val albums = libraryTracks.filter { !it.album.isNullOrBlank() }.groupBy { it.album!! }
        .map { (name, songs) -> Triple(name, songs.first().artist, songs.first().artworkUrl) }
        .sortedBy { it.first.lowercase() }
    val artists = libraryTracks.groupBy { it.artist }.filterKeys { it.isNotBlank() && it != "Unknown artist" }
        .map { (name, songs) -> Triple(name, songs.size, songs.firstNotNullOfOrNull { it.artworkUrl }) }
        .sortedBy { it.first.lowercase() }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Row(Modifier.statusBarsPadding().padding(start = 16.dp, end = 4.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(34.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) { Text("W", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.titleMedium) }
                Spacer(Modifier.width(12.dp))
                Text("Your Library", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                Box {
                    IconButton(onClick = { addMenu = true }) { Icon(Icons.Rounded.Add, "Add") }
                    DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                        DropdownMenuItem(text = { Text("New playlist") }, leadingIcon = { Icon(Icons.Rounded.Add, null) }, onClick = { addMenu = false; creating = true })
                        DropdownMenuItem(text = { Text("Add music folder") }, leadingIcon = { Icon(Icons.Rounded.CreateNewFolder, null) }, onClick = { addMenu = false; folderPicker.launch(null) })
                        DropdownMenuItem(text = { Text("Import a playlist") }, leadingIcon = { Icon(Icons.Rounded.FileUpload, null) }, onClick = { addMenu = false; nav.navigate(Routes.IMPORT) })
                    }
                }
                IconButton(onClick = { nav.navigate(Routes.SETTINGS) }) { Icon(Icons.Rounded.Settings, "Settings") }
            }
        }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(LibFilter.entries) { f ->
                    FilterChip(selected = filter == f, onClick = { filter = if (filter == f) null else f }, label = { Text(f.label) })
                }
            }
        }
        item {
            Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.clip(CircleShape).clickable { sortMenu = true }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Rounded.Sort, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(sort.label, style = MaterialTheme.typography.labelLarge)
                }
                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                    LibSort.entries.forEach { s -> DropdownMenuItem(text = { Text(s.label) }, onClick = { sort = s; sortMenu = false }) }
                }
                Spacer(Modifier.weight(1f))
                if (filter == null || filter == LibFilter.Playlists) {
                    IconButton(onClick = { grid = !grid }) {
                        Icon(if (grid) Icons.AutoMirrored.Rounded.ViewList else Icons.Rounded.GridView, if (grid) "Show as list" else "Show as grid")
                    }
                }
            }
        }

        when (filter) {
            null, LibFilter.Playlists -> {
                item {
                    LibraryRow("Liked Songs", "Playlist · ${liked.size} songs", onClick = { nav.navigate(Routes.LIKED) }) {
                        LikedCover(Modifier.size(it), corner = 4.dp)
                    }
                }
                if (filter == null) {
                    item {
                        LibraryRow("Downloads", "${downloads.size} songs on this phone", onClick = { nav.navigate(Routes.DOWNLOADS) }) {
                            Artwork(null, Modifier.size(it), corner = 4.dp, icon = Icons.Rounded.CloudDownload)
                        }
                    }
                    item {
                        LibraryRow(
                            "On this device",
                            if (local.hasPermission) "${local.tracks.size} songs" else "Allow access to see your music files",
                            onClick = { if (local.hasPermission) nav.navigate(Routes.DEVICE) else onRequestPermission() },
                        ) { Artwork(null, Modifier.size(it), corner = 4.dp, icon = Icons.Rounded.PhoneAndroid) }
                    }
                }
                val sorted = when (sort) {
                    LibSort.Recent -> playlists
                    LibSort.Alphabetical -> playlists.sortedBy { it.name.lowercase() }
                }
                if (grid) {
                    items(sorted.chunked(3), key = { row -> "g" + row.joinToString { it.id.toString() } }) { row ->
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            row.forEach { p ->
                                Column(Modifier.weight(1f).clickable { nav.navigate(Routes.playlist(p.id)) }) {
                                    MosaicCover(p.coverUrls, Modifier.fillMaxWidth().aspectRatio(1f), corner = 4.dp)
                                    Text(p.name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                                    Text("${p.count} songs", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                } else {
                    items(sorted, key = { "p${it.id}" }) { p ->
                        LibraryRow(p.name, "Playlist · ${p.count} songs", onClick = { nav.navigate(Routes.playlist(p.id)) }) {
                            MosaicCover(p.coverUrls, Modifier.size(it), corner = 4.dp)
                        }
                    }
                }
                if (playlists.isEmpty()) item {
                    EmptyState(
                        Icons.Rounded.Add, "Create your first playlist",
                        "Tap + to make a playlist, or import one from Spotify, Apple Music, YouTube Music or a file.",
                    )
                }
            }
            LibFilter.Albums -> {
                if (albums.isEmpty()) item { EmptyState(Icons.Rounded.Album, "No albums yet", "Albums from music on this phone and your downloads show up here.") }
                items(albums, key = { "a${it.first}" }) { (name, artist, art) ->
                    LibraryRow(name, "Album · $artist", onClick = { nav.navigate(Routes.album(name)) }) { Artwork(art, Modifier.size(it), corner = 4.dp) }
                }
            }
            LibFilter.Artists -> {
                if (artists.isEmpty()) item { EmptyState(Icons.Rounded.Person, "No artists yet", "Artists from music on this phone and your downloads show up here.") }
                items(artists, key = { "r${it.first}" }) { (name, count, art) ->
                    LibraryRow(name, "Artist · $count songs", onClick = { nav.navigate(Routes.artist(name)) }, circle = true) {
                        Artwork(art, Modifier.size(it).clip(CircleShape), corner = 0.dp, icon = Icons.Rounded.Person)
                    }
                }
            }
            LibFilter.Downloaded -> {
                if (downloads.isEmpty()) item { EmptyState(Icons.Rounded.CloudDownload, "Nothing downloaded", "Use Download in a song's menu to play it without a connection.") }
                items(downloads, key = { "d${it.key}" }) { t -> SongRow(vm, t, onClick = { vm.play(t, downloads) }, modifier = Modifier.animateItem()) }
            }
        }
    }

    if (creating) {
        PlaylistNameDialog("Name your playlist", "Create", onDismiss = { creating = false }) { name, desc ->
            creating = false
            vm.createPlaylist(name, desc) { id -> nav.navigate(Routes.playlist(id)) }
        }
    }
}

@Composable
private fun LibraryRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    @Suppress("UNUSED_PARAMETER") circle: Boolean = false,
    cover: @Composable (Dp) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        cover(64.dp)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
