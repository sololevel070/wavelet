package app.wavelet.ui

import android.content.Intent
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import app.wavelet.io.ExportFormat
import app.wavelet.source.Track
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

// ---------------------------------------------------------------- shared header

@Composable
fun CollectionHeader(
    title: String,
    subtitle: String,
    tint: Color?,
    onBack: () -> Unit,
    cover: @Composable (Dp) -> Unit,
    description: String? = null,
    actions: @Composable () -> Unit = {},
    onPlay: (() -> Unit)?,
    onShuffle: (() -> Unit)?,
) {
    val top = tint ?: MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
    Column(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(top.copy(alpha = 0.75f), MaterialTheme.colorScheme.background))),
    ) {
        IconButton(onClick = onBack, modifier = Modifier.statusBarsPadding()) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { cover(220.dp) }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 16.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (!description.isNullOrBlank()) {
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp))
        }
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp))
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            actions()
            Spacer(Modifier.weight(1f))
            if (onShuffle != null) IconButton(onClick = onShuffle) { Icon(Icons.Rounded.Shuffle, "Shuffle play", modifier = Modifier.size(28.dp)) }
            if (onPlay != null) {
                FilledIconButton(onClick = onPlay, modifier = Modifier.size(56.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary)) {
                    Icon(Icons.Rounded.PlayArrow, "Play", modifier = Modifier.size(32.dp))
                }
            }
        }
    }
}

/** A simple track-list screen: header plus songs. */
@Composable
private fun TrackCollection(
    vm: MainViewModel,
    nav: NavHostController,
    title: String,
    subtitle: String,
    tracks: List<Track>,
    cover: @Composable (Dp) -> Unit,
    tintUrl: String? = tracks.firstOrNull()?.artworkUrl,
    empty: @Composable () -> Unit = {},
    extra: LazyListScope.() -> Unit = {},
) {
    val tint = rememberArtworkColor(tintUrl)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            CollectionHeader(
                title, subtitle, tint, onBack = { nav.popBackStack() }, cover = cover,
                actions = {
                    if (tracks.any { it.downloadable }) IconButton(onClick = { vm.downloadAll(tracks) }) { Icon(Icons.Rounded.FileDownload, "Download all") }
                    if (tracks.isNotEmpty()) IconButton(onClick = { vm.askAddToPlaylist(tracks) }) { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, "Add all to a playlist") }
                },
                onPlay = if (tracks.isNotEmpty()) ({ vm.playList(tracks) }) else null,
                onShuffle = if (tracks.size > 1) ({ vm.playList(tracks, shuffle = true) }) else null,
            )
        }
        if (tracks.isEmpty()) item { empty() }
        items(tracks, key = { it.key }) { t -> SongRow(vm, t, onClick = { vm.play(t, tracks) }, modifier = Modifier.animateItem()) }
        extra()
    }
}

private fun totalLength(tracks: List<Track>) = tracks.sumOf { it.durationMs ?: 0 }.let { if (it > 0) " · ${formatLength(it)}" else "" }

// ---------------------------------------------------------------- liked, downloads, device

@Composable
fun LikedScreen(vm: MainViewModel, nav: NavHostController) {
    val liked by vm.likedTracks.collectAsStateWithLifecycle()
    TrackCollection(
        vm, nav, "Liked Songs", "${liked.size} songs${totalLength(liked)}", liked,
        cover = { LikedCover(Modifier.size(it), corner = 8.dp) }, tintUrl = null,
        empty = { EmptyState(Icons.Rounded.FavoriteBorder, "Songs you like will appear here", "Tap the heart on any song to save it to Liked Songs.") },
    )
}

@Composable
fun DownloadsScreen(vm: MainViewModel, nav: NavHostController) {
    val downloads by vm.downloads.collectAsStateWithLifecycle()
    TrackCollection(
        vm, nav, "Downloads", "${downloads.size} songs · play without a connection", downloads,
        cover = { Artwork(null, Modifier.size(it), corner = 8.dp, icon = Icons.Rounded.CloudDownload) }, tintUrl = null,
        empty = { EmptyState(Icons.Rounded.CloudDownload, "Nothing downloaded yet", "Open a song's menu and choose Download.") },
    )
}

@Composable
fun DeviceSongsScreen(vm: MainViewModel, nav: NavHostController, onRequestPermission: () -> Unit) {
    val local by vm.local.collectAsStateWithLifecycle()
    TrackCollection(
        vm, nav, "On this device", "${local.tracks.size} songs${totalLength(local.tracks)}", local.tracks,
        cover = { Artwork(null, Modifier.size(it), corner = 8.dp, icon = Icons.Rounded.LibraryMusic) }, tintUrl = null,
        empty = {
            EmptyState(Icons.Rounded.LibraryMusic, if (local.hasPermission) "No music files found" else "Allow access to your music", "Songs stored on this phone show up here.") {
                Button(onClick = if (local.hasPermission) vm::refreshLocal else onRequestPermission) { Text(if (local.hasPermission) "Scan again" else "Allow access") }
            }
        },
    )
}

@Composable
fun AlbumScreen(vm: MainViewModel, nav: NavHostController, album: String) {
    val local by vm.local.collectAsStateWithLifecycle()
    val downloads by vm.downloads.collectAsStateWithLifecycle()
    val tracks = (local.tracks + downloads).filter { it.album == album }
    val artist = tracks.groupingBy { it.artist }.eachCount().maxByOrNull { it.value }?.key.orEmpty()
    TrackCollection(
        vm, nav, album, "Album · $artist · ${tracks.size} songs${totalLength(tracks)}", tracks,
        cover = { Artwork(tracks.firstOrNull()?.artworkUrl, Modifier.size(it), corner = 8.dp) },
    )
}

@Composable
fun ArtistScreen(vm: MainViewModel, nav: NavHostController, artist: String) {
    val local by vm.local.collectAsStateWithLifecycle()
    val downloads by vm.downloads.collectAsStateWithLifecycle()
    val liked by vm.likedTracks.collectAsStateWithLifecycle()
    val yours = (local.tracks + downloads + liked).filter { it.artist.equals(artist, ignoreCase = true) }.distinctBy { it.key }
    val online by produceState<List<Track>?>(null, artist) { value = vm.searchEverywhere(artist, artistOnly = true) }
    val cover = (yours + online.orEmpty()).firstNotNullOfOrNull { it.artworkUrl }
    TrackCollection(
        vm, nav, artist, "Artist · ${yours.size} songs in your library", yours,
        cover = { Artwork(cover, Modifier.size(it).clip(CircleShape), corner = 0.dp, icon = Icons.Rounded.Person) },
        tintUrl = cover,
        extra = {
            item { SectionTitle("From your sources") }
            val list = online
            when {
                list == null -> item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                list.isEmpty() -> item { Text("Nothing more found for $artist.", modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                else -> items(list, key = { "o" + it.key }) { t -> SongRow(vm, t, onClick = { vm.play(t, list) }, subtitle = "${t.artist} · ${vm.sourceName(t.sourceId)}") }
            }
        },
    )
}

@Composable
fun HistoryScreen(vm: MainViewModel, nav: NavHostController) {
    val history by vm.history.collectAsStateWithLifecycle()
    val tracks = history.map { it.track }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    val fmt = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Row(Modifier.statusBarsPadding().padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                Text("Recently played", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                if (history.isNotEmpty()) TextButton(onClick = { confirmClear = true }) { Text("Clear") }
            }
        }
        if (history.isEmpty()) item { EmptyState(Icons.Rounded.History, "Nothing played yet", "Songs you listen to appear here.") }
        itemsIndexed(history, key = { _, h -> h.historyId }) { i, h ->
            SongRow(vm, h.track, modifier = Modifier.animateItem(), onClick = { vm.playList(tracks, i) }, subtitle = "${h.track.artist} · ${fmt.format(Date(h.playedAt))}")
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear listening history?") },
            text = { Text("This removes your recently played list and resets your stats. Playlists and liked songs are kept.") },
            confirmButton = { TextButton(onClick = { confirmClear = false; vm.clearHistory() }) { Text("Clear") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

// ---------------------------------------------------------------- playlist

@Composable
fun PlaylistScreen(vm: MainViewModel, nav: NavHostController, playlistId: Long) {
    val playlist by remember(playlistId) { vm.playlist(playlistId) }.collectAsStateWithLifecycle(initialValue = null)
    val items by remember(playlistId) { vm.playlistItems(playlistId) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val summaries by vm.playlists.collectAsStateWithLifecycle()
    val covers = summaries.firstOrNull { it.id == playlistId }?.coverUrls.orEmpty()
    var editing by rememberSaveable { mutableStateOf(false) }
    var menu by rememberSaveable { mutableStateOf(false) }
    var renaming by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    var exportFormat by remember { mutableStateOf<ExportFormat?>(null) }
    var choosingFormat by rememberSaveable { mutableStateOf(false) }
    var filterText by rememberSaveable { mutableStateOf("") }
    var sortMode by rememberSaveable { mutableStateOf(0) }
    var sortMenu by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tint = rememberArtworkColor(covers.firstOrNull())
    val tracks = items.mapNotNull { it.track }
    val unmatched = items.count { it.track == null }

    val zipLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let { vm.exportPlaylistZip(playlistId, it) }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val f = exportFormat
        if (uri != null && f != null) vm.exportPlaylist(playlistId, f, uri)
        exportFormat = null
    }

    val p = playlist
    if (p == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        LaunchedEffect(playlistId) {
            kotlinx.coroutines.delay(1500)
            if (playlist == null) nav.popBackStack()
        }
        return
    }

    val shownItems = items
        .filter { i ->
            filterText.isBlank() || listOf(i.track?.title ?: i.itemTitle, i.track?.artist ?: i.itemArtist, i.track?.album ?: i.itemAlbum.orEmpty())
                .any { it.contains(filterText.trim(), ignoreCase = true) }
        }
        .let { list ->
            when (sortMode) {
                1 -> list.sortedBy { (it.track?.title ?: it.itemTitle).lowercase() }
                2 -> list.sortedBy { (it.track?.artist ?: it.itemArtist).lowercase() }
                3 -> list.sortedByDescending { it.position }
                else -> list
            }
        }
    val canEditOrder = sortMode == 0 && filterText.isBlank()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            CollectionHeader(
                p.name,
                "${items.size} songs${totalLength(tracks)}" + if (unmatched > 0) " · $unmatched not found" else "",
                tint,
                description = p.description,
                onBack = { nav.popBackStack() },
                cover = { MosaicCover(covers, Modifier.size(it), corner = 8.dp) },
                actions = {
                    if (tracks.any { it.downloadable }) IconButton(onClick = { vm.downloadAll(tracks) }) { Icon(Icons.Rounded.FileDownload, "Download playlist") }
                    if (canEditOrder) IconButton(onClick = { editing = !editing }) {
                        Icon(if (editing) Icons.Rounded.Done else Icons.Rounded.SwapVert, if (editing) "Done editing" else "Edit order")
                    }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "More") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Edit details") }, leadingIcon = { Icon(Icons.Rounded.Edit, null) }, onClick = { menu = false; renaming = true })
                            DropdownMenuItem(text = { Text("Add to queue") }, leadingIcon = { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, null) }, onClick = { menu = false; vm.enqueue(tracks, playNext = false) })
                            if (p.folderUri != null) DropdownMenuItem(text = { Text("Sync folder") }, leadingIcon = { Icon(Icons.Rounded.Sync, null) }, onClick = { menu = false; vm.syncFolder(playlistId) })
                            if (unmatched > 0) DropdownMenuItem(text = { Text("Re-scan $unmatched missing songs") }, leadingIcon = { Icon(Icons.Rounded.Search, null) }, onClick = { menu = false; vm.rescanUnmatched(playlistId) })
                            DropdownMenuItem(text = { Text("Export as ZIP…") }, leadingIcon = { Icon(Icons.Rounded.FolderZip, null) }, onClick = { menu = false; zipLauncher.launch("${p.name.replace(Regex("""[\\/:*?"<>|]"""), "_")}.zip") })
                            DropdownMenuItem(text = { Text("Export song list…") }, leadingIcon = { Icon(Icons.Rounded.FileDownload, null) }, onClick = { menu = false; choosingFormat = true })
                            DropdownMenuItem(text = { Text("Share") }, leadingIcon = { Icon(Icons.Rounded.Share, null) }, onClick = {
                                menu = false
                                scope.launch {
                                    val text = vm.playlistShareText(playlistId)
                                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share playlist"))
                                }
                            })
                            DropdownMenuItem(text = { Text("Delete playlist") }, leadingIcon = { Icon(Icons.Rounded.Delete, null) }, onClick = { menu = false; deleting = true })
                        }
                    }
                },
                onPlay = if (tracks.isNotEmpty()) ({ vm.playPlaylist(items) }) else null,
                onShuffle = if (tracks.size > 1) ({ vm.playPlaylist(items, shuffle = true) }) else null,
            )
        }
        if (items.size > 1) item {
            Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.foundation.text.BasicTextField(
                    value = filterText,
                    onValueChange = { filterText = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        Row(
                            Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp)).background(Color.White.copy(alpha = 0.12f)).padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.Search, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Box { if (filterText.isEmpty()) Text("Find in playlist", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant); inner() }
                        }
                    },
                )
                Spacer(Modifier.width(8.dp))
                Box {
                    Text(
                        "Sort",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp)).background(Color.White.copy(alpha = 0.12f)).clickable { sortMenu = true }.padding(horizontal = 12.dp, vertical = 9.dp),
                    )
                    DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                        listOf("Custom order", "Title", "Artist", "Recently added").forEachIndexed { i, label ->
                            DropdownMenuItem(text = { Text(label) }, onClick = { sortMode = i; sortMenu = false })
                        }
                    }
                }
            }
        }
        if (items.isEmpty()) item {
            EmptyState(Icons.Rounded.Search, "Let's find something for your playlist", "Search for songs and use \"Add to playlist\" from a song's menu.") {
                Button(onClick = { nav.navigate(Routes.SEARCH) }) { Text("Find songs") }
            }
        }
        itemsIndexed(shownItems, key = { _, it -> it.itemId }) { index, item ->
            val track = item.track
            if (editing && canEditOrder) {
                Row(Modifier.animateItem().fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Artwork(track?.artworkUrl, Modifier.size(44.dp), corner = 4.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(track?.title ?: item.itemTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(track?.artist ?: item.itemArtist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { vm.moveInPlaylist(playlistId, index, index - 1) }, enabled = index > 0) { Icon(Icons.Rounded.KeyboardArrowUp, "Move up") }
                    IconButton(onClick = { vm.moveInPlaylist(playlistId, index, index + 1) }, enabled = index < items.lastIndex) { Icon(Icons.Rounded.KeyboardArrowDown, "Move down") }
                    IconButton(onClick = { vm.removeFromPlaylist(playlistId, item.itemId) }) { Icon(Icons.Rounded.Delete, "Remove") }
                }
            } else if (track != null) {
                SongRow(vm, track, onClick = { vm.playPlaylist(items, item) }, playlistId = playlistId, itemId = item.itemId, modifier = Modifier.animateItem())
            } else {
                Row(
                    Modifier.animateItem().fillMaxWidth().clickable(onClickLabel = "Find a match") { vm.openFixMatch(playlistId, item) }.padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(50.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.WarningAmber, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.itemTitle, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${item.itemArtist.ifBlank { "Unknown artist" }} · Not found – tap to pick a match", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    if (renaming) {
        PlaylistNameDialog("Edit details", "Save", p.name, p.description, onDismiss = { renaming = false }) { name, desc ->
            renaming = false
            vm.renamePlaylist(playlistId, name, desc)
        }
    }
    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text("Delete \"${p.name}\"?") },
            text = { Text("The playlist is removed from Your Library. Songs stay in your sources and downloads.") },
            confirmButton = {
                TextButton(onClick = {
                    deleting = false
                    vm.deletePlaylist(playlistId)
                    nav.popBackStack()
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text("Cancel") } },
        )
    }
    if (choosingFormat) {
        AlertDialog(
            onDismissRequest = { choosingFormat = false },
            title = { Text("Export playlist") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ExportFormat.entries.forEach { f ->
                        Text(
                            f.label,
                            modifier = Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp)).clickable {
                                choosingFormat = false
                                exportFormat = f
                                exportLauncher.launch("${p.name.replace(Regex("""[\\/:*?"<>|]"""), "_")}.${f.extension}")
                            }.padding(12.dp),
                        )
                    }
                    Text("Tip: CSV files can be imported into Spotify, Apple Music and YouTube Music with tools such as TuneMyMusic or Soundiiz.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Start)
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { choosingFormat = false }) { Text("Cancel") } },
        )
    }
}
