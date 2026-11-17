package app.wavelet.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import app.wavelet.download.DOWNLOADS_SOURCE_ID
import app.wavelet.source.Track
import kotlin.math.roundToInt

/** Sheets and dialogs that any screen can open through the view model. */
@Composable
fun GlobalSheets(vm: MainViewModel, nav: NavHostController) {
    val options by vm.options.collectAsStateWithLifecycle()
    val addTo by vm.addToPlaylist.collectAsStateWithLifecycle()
    val links by vm.links.collectAsStateWithLifecycle()
    val import by vm.import.collectAsStateWithLifecycle()
    val fix by vm.fixMatch.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val showEq by vm.showEqualizer.collectAsStateWithLifecycle()

    options?.let { TrackOptionsSheet(vm, nav, it) }
    addTo?.let { AddToPlaylistSheet(vm, it) }
    links?.let { LinksDialog(it, vm::closeLinks) }
    import?.let { ImportProgressDialog(vm, nav, it) }
    fix?.let { FixMatchSheet(vm, it) }
    busy?.let { BusyDialog(it) }
    if (showEq) EqualizerSheet(vm, onClose = vm::closeEqualizer)
}

@Composable
private fun SheetAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        leadingContent = { Icon(icon, null) },
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

/** Full-screen song menu, styled after the kit's "Track" options screen. */
@Composable
private fun TrackOptionsSheet(vm: MainViewModel, nav: NavHostController, opts: TrackOptions) {
    val track = opts.track
    val liked by vm.likedKeys.collectAsStateWithLifecycle()
    val downloads by vm.downloads.collectAsStateWithLifecycle()
    val isLiked = track.key in liked
    val downloaded = track.sourceId == DOWNLOADS_SOURCE_ID || downloads.any { it.id == track.key }
    val context = LocalContext.current
    val tint = rememberArtworkColor(track.artworkUrl)
    fun act(block: () -> Unit) {
        vm.closeOptions()
        block()
    }

    FullScreenOverlay(onDismiss = vm::closeOptions) {
        Box(
            Modifier.fillMaxSize().background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    0f to (tint ?: androidx.compose.ui.graphics.Color(0xFF3A2F7A)).copy(alpha = 0.9f),
                    0.45f to androidx.compose.ui.graphics.Color(0xF2121212),
                    1f to androidx.compose.ui.graphics.Color(0xFF121212),
                ),
            ),
        ) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Artwork(track.artworkUrl, Modifier.size(180.dp), live = track.isLive, corner = 4.dp)
                    Spacer(Modifier.size(16.dp))
                    Text(track.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 24.dp))
                    Text("${track.artist} · ${vm.sourceName(track.sourceId)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 24.dp))
                    Spacer(Modifier.size(24.dp))
                    Column(Modifier.fillMaxWidth()) {
                        if (!track.isLive) {
                            SheetAction(if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, if (isLiked) "Liked" else "Like") { act { vm.toggleLike(track) } }
                            SheetAction(Icons.AutoMirrored.Rounded.PlaylistAdd, "Add to playlist") { act { vm.askAddToPlaylist(listOf(track)) } }
                            SheetAction(Icons.AutoMirrored.Rounded.QueueMusic, "Add to queue") { act { vm.enqueue(track, playNext = false) } }
                            SheetAction(Icons.Rounded.PlaylistPlay, "Play next") { act { vm.enqueue(track, playNext = true) } }
                        }
                        if (opts.playlistId != null && opts.itemId != null) {
                            SheetAction(Icons.Rounded.RemoveCircleOutline, "Remove from this playlist") { act { vm.removeFromPlaylist(opts.playlistId, opts.itemId) } }
                        }
                        when {
                            downloaded -> SheetAction(Icons.Rounded.Delete, "Remove download") { act { vm.deleteDownload(track) } }
                            track.downloadable -> SheetAction(Icons.Rounded.Download, "Download") { act { vm.download(track) } }
                        }
                        SheetAction(Icons.Rounded.Share, "Share") {
                            act {
                                val text = if (track.isLive) track.title else "${track.title} – ${track.artist}"
                                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share song"))
                            }
                        }
                        if (!track.isLive) SheetAction(Icons.Rounded.Radio, "Go to radio") { act { vm.startRadio(track) } }
                        if (!track.album.isNullOrBlank() && (track.sourceId == "local" || track.sourceId == DOWNLOADS_SOURCE_ID)) {
                            SheetAction(Icons.Rounded.Album, "View album") { act { nav.navigate(Routes.album(track.album)) } }
                        }
                        if (!track.isLive && track.artist.isNotBlank()) {
                            SheetAction(Icons.Rounded.Person, "View artist") { act { nav.navigate(Routes.artist(track.artist)) } }
                        }
                        if (!track.isLive) SheetAction(Icons.Rounded.Link, "Open in another app") { act { vm.showLinks(track) } }
                    }
                }
                TextButton(onClick = vm::closeOptions, modifier = Modifier.align(Alignment.CenterHorizontally).padding(12.dp)) {
                    Text("Close", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

/** Edge-to-edge dialog used for the kit's full-screen menus. */
@Composable
fun FullScreenOverlay(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) { content() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddToPlaylistSheet(vm: MainViewModel, tracks: List<Track>) {
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    var creating by rememberSaveable { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = vm::closeAddToPlaylist) {
        Column(Modifier.navigationBarsPadding()) {
            Text(
                if (tracks.size == 1) "Add to playlist" else "Add ${tracks.size} songs to playlist",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Button(onClick = { creating = true }, modifier = Modifier.padding(8.dp)) {
                    Icon(Icons.Rounded.Add, null)
                    Spacer(Modifier.width(6.dp))
                    Text("New playlist")
                }
            }
            LazyColumn(Modifier.heightIn(max = 460.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                items(playlists, key = { it.id }) { p ->
                    Row(
                        Modifier.fillMaxWidth().clickable { vm.addToPlaylist(p.id, tracks) }.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MosaicCover(p.coverUrls, Modifier.size(52.dp), corner = 4.dp)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(p.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${p.count} songs", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
    if (creating) {
        PlaylistNameDialog(
            title = "Name your playlist",
            confirm = "Create",
            onDismiss = { creating = false },
            onConfirm = { name, desc ->
                creating = false
                vm.createPlaylist(name, desc, tracks)
            },
        )
    }
}

@Composable
fun PlaylistNameDialog(
    title: String,
    confirm: String,
    initialName: String = "",
    initialDescription: String = "",
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var desc by rememberSaveable { mutableStateOf(initialDescription) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, singleLine = true,
                    label = { Text("Name") }, placeholder = { Text("My playlist") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                )
                OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Description (optional)") }, maxLines = 3)
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(name.ifBlank { "My playlist" }, desc) }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun LinksDialog(state: LinksState, onClose: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Open in…") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                when {
                    state.loading -> CircularProgressIndicator()
                    state.error != null -> Text("Couldn't look up links: ${state.error}")
                    state.links.isEmpty() -> Text("This song wasn't found on other services.")
                    else -> state.links.forEach { (service, url) ->
                        TextButton(onClick = { runCatching { uriHandler.openUri(url) } }) { Text(service) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Close") } },
    )
}

@Composable
private fun ImportProgressDialog(vm: MainViewModel, nav: NavHostController, state: ImportState) {
    AlertDialog(
        onDismissRequest = { if (state.finished) vm.dismissImport() },
        title = { Text(if (state.error != null) "Import failed" else if (state.finished) "Import finished" else "Importing ${state.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when {
                    state.error != null -> Text(state.error)
                    !state.finished -> {
                        Text("Finding each song in your sources…")
                        if (state.total > 0) {
                            LinearProgressIndicator(progress = { state.done.toFloat() / state.total }, modifier = Modifier.fillMaxWidth())
                            Text("${state.done} of ${state.total}", style = MaterialTheme.typography.bodySmall)
                        } else {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                        }
                    }
                    else -> {
                        val pct = if (state.total > 0) (state.matched * 100f / state.total).roundToInt() else 0
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text("\"${state.name}\" added to Your Library.")
                        }
                        Text("${state.matched} of ${state.total} songs matched ($pct%).")
                        if (state.matched < state.total) {
                            Text(
                                "Unmatched songs are kept in the playlist. Open it and tap a song marked \"Not found\" to pick a match, or use ⋮ › Re-scan after adding more music or sources.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (state.finished) {
                TextButton(onClick = {
                    vm.dismissImport()
                    state.playlistId?.let { nav.navigate(Routes.playlist(it)) }
                }) { Text(if (state.playlistId != null) "Open playlist" else "OK") }
            }
        },
        dismissButton = { if (state.finished && state.playlistId != null) TextButton(onClick = vm::dismissImport) { Text("Close") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FixMatchSheet(vm: MainViewModel, state: FixMatchState) {
    var query by remember(state.itemId) { mutableStateOf(state.query) }
    ModalBottomSheet(onDismissRequest = vm::closeFixMatch, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding()) {
            Text("Find a match", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 16.dp))
            Text(
                "For \"${state.entry.title}\"" + (if (state.entry.artist.isNotBlank()) " by ${state.entry.artist}" else ""),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.searchFixMatch(query) }),
            )
            LazyColumn(Modifier.heightIn(max = 520.dp)) {
                if (state.loading) item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                else if (state.candidates.isEmpty()) item {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Warning, null)
                        Spacer(Modifier.width(8.dp))
                        Text("No results. Try a different search, or turn on more sources.")
                    }
                }
                items(state.candidates, key = { it.track.key }) { c ->
                    Row(
                        Modifier.fillMaxWidth().clickable { vm.chooseMatch(c.track) }.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Artwork(c.track.artworkUrl, Modifier.size(48.dp), corner = 4.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                            Text("${c.track.artist} · ${vm.sourceName(c.track.sourceId)}", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("${(c.score * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium, color = if (c.score >= 0.78) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                item {
                    TextButton(onClick = { vm.chooseMatch(null) }, modifier = Modifier.padding(16.dp)) { Text("Remove current match") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerSheet(vm: MainViewModel, onClose: () -> Unit) {
    val fx by vm.audioFxState.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheetState,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text("Equalizer & Audio FX", style = MaterialTheme.typography.titleLarge)
                    Text(
                        if (fx.available) (if (fx.enabled) "On" else "Off") else "Not supported on this device/session",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = fx.enabled,
                    onCheckedChange = vm::setEqualizerEnabled,
                    enabled = fx.available,
                )
            }

            if (fx.available && fx.enabled) {
                Spacer(Modifier.size(16.dp))
                if (fx.presets.isNotEmpty()) {
                    Text("Presets", style = MaterialTheme.typography.labelLarge)
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(vertical = 8.dp),
                    ) {
                        itemsIndexed(fx.presets) { idx, name ->
                            FilterChip(
                                selected = fx.currentPreset == idx.toShort(),
                                onClick = { vm.setEqualizerPreset(idx.toShort()) },
                                label = { Text(name) },
                            )
                        }
                    }
                }

                Spacer(Modifier.size(12.dp))
                Text("Frequency Bands", style = MaterialTheme.typography.labelLarge)
                fx.bands.forEach { band ->
                    val min = band.minMillibels.toFloat()
                    val max = band.maxMillibels.toFloat()
                    val cur = band.levelMillibels.toFloat()
                    val freqLabel = if (band.centerFreqHz >= 1000) "${band.centerFreqHz / 1000} kHz" else "${band.centerFreqHz} Hz"
                    val dbLabel = "${if (band.levelMillibels > 0) "+" else ""}${band.levelMillibels / 100} dB"

                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(freqLabel, Modifier.width(64.dp), style = MaterialTheme.typography.bodyMedium)
                        Slider(
                            value = cur,
                            onValueChange = { vm.setEqualizerBand(band.index, it.toInt().toShort()) },
                            valueRange = min..max,
                            modifier = Modifier.weight(1f),
                        )
                        Text(dbLabel, Modifier.width(54.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(Modifier.size(16.dp))
                Text("Bass Boost", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Slider(
                        value = fx.bassBoost.toFloat(),
                        onValueChange = { vm.setBassBoost(it.toInt().toShort()) },
                        valueRange = 0f..1000f,
                        modifier = Modifier.weight(1f),
                    )
                    Text("${(fx.bassBoost / 10)}%", Modifier.width(44.dp), style = MaterialTheme.typography.bodySmall)
                }

                Spacer(Modifier.size(12.dp))
                Text("Virtualizer / Surround", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Slider(
                        value = fx.virtualizer.toFloat(),
                        onValueChange = { vm.setVirtualizer(it.toInt().toShort()) },
                        valueRange = 0f..1000f,
                        modifier = Modifier.weight(1f),
                    )
                    Text("${(fx.virtualizer / 10)}%", Modifier.width(44.dp), style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.size(16.dp))
        }
    }
}

/** Progress dialog for long tasks (folder scan, re-scan, ZIP). It can't be dismissed while it runs. */
@Composable
private fun BusyDialog(state: BusyState) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(state.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (state.message.isNotBlank()) Text(state.message)
                if (state.total > 0) {
                    LinearProgressIndicator(progress = { state.done.toFloat() / state.total }, modifier = Modifier.fillMaxWidth())
                    Text("${state.done} of ${state.total}", style = MaterialTheme.typography.bodySmall)
                } else {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {},
    )
}
