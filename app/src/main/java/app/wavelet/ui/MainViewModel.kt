package app.wavelet.ui

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import app.wavelet.WaveletApp
import app.wavelet.data.LibraryBackup
import app.wavelet.data.MonthStats
import app.wavelet.data.PlaylistEntry
import app.wavelet.data.Settings
import app.wavelet.data.SourceConfig
import app.wavelet.data.db.PlaylistItemWithTrack
import app.wavelet.download.DOWNLOADS_SOURCE_ID
import app.wavelet.integrations.LastFm
import app.wavelet.integrations.ListenBrainz
import app.wavelet.integrations.ListenBrainzPlaylist
import app.wavelet.io.ExportFormat
import app.wavelet.io.ParsedPlaylist
import app.wavelet.io.PlaylistFormats
import app.wavelet.io.TrackMatcher
import app.wavelet.io.WEB_SOURCE_ID
import app.wavelet.net.Http
import app.wavelet.net.HttpException
import app.wavelet.player.AudioFxController
import app.wavelet.player.AudioFxState
import app.wavelet.player.PlayerConnection
import app.wavelet.player.PlayerUiState
import app.wavelet.player.QueueEntry
import app.wavelet.player.RepeatMode
import app.wavelet.player.toMediaItem
import app.wavelet.source.Engines
import app.wavelet.source.Lyrics
import app.wavelet.source.MusicSource
import app.wavelet.source.RepoEntry
import app.wavelet.source.RepoIndex
import app.wavelet.source.Source
import app.wavelet.source.Track
import app.wavelet.source.attempt
import app.wavelet.source.impl.LocalSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.UnknownHostException
import java.util.Calendar
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class SourceResults(
    val sourceId: String,
    val sourceName: String,
    val tracks: List<Track> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
)

data class SearchState(
    val query: String = "",
    val submitted: String = "",
    val filter: String? = null,
    val sections: List<SourceResults> = emptyList(),
)

data class LocalState(
    val loading: Boolean = false,
    val tracks: List<Track> = emptyList(),
    val hasPermission: Boolean = true,
)

data class LyricsState(val trackKey: String? = null, val loading: Boolean = false, val lyrics: Lyrics? = null)

data class LinksState(val loading: Boolean = true, val links: Map<String, String> = emptyMap(), val error: String? = null)

data class RepoView(val url: String, val loading: Boolean = true, val index: RepoIndex? = null, val error: String? = null)

data class ImportState(
    val name: String,
    val done: Int = 0,
    val total: Int = 0,
    val finished: Boolean = false,
    val matched: Int = 0,
    val playlistId: Long? = null,
    val error: String? = null,
)

data class StatsState(val year: Int, val month: Int, val loading: Boolean = true, val stats: MonthStats? = null)

/** What the track options sheet is showing, and where the track came from. */
data class TrackOptions(val track: Track, val playlistId: Long? = null, val itemId: Long? = null)

data class FixMatchState(
    val playlistId: Long,
    val itemId: Long,
    val entry: PlaylistEntry,
    val query: String,
    val loading: Boolean = true,
    val candidates: List<TrackMatcher.Candidate> = emptyList(),
)

data class SleepTimer(val endsAt: Long, val label: String)

data class LbState(val loading: Boolean = false, val playlists: List<ListenBrainzPlaylist> = emptyList(), val error: String? = null)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as WaveletApp).container
    private val library = c.library

    val appState = c.store.state
    val settings: StateFlow<Settings> = appState.map { it.settings }
        .stateIn(viewModelScope, SharingStarted.Eagerly, appState.value.settings)
    val sources: StateFlow<List<Source>> = c.sources.sources
    val downloads: StateFlow<List<Track>> = c.downloads.items
    val downloadProgress: StateFlow<Map<String, Float>> = c.downloads.progress

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()
    fun say(message: String) {
        _messages.tryEmit(message)
    }

    // ================================================================ library flows

    val playlists = library.playlists.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val likedKeys = library.likedKeys.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    val likedTracks = library.likedTracks.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val recentTracks = library.recentTracks(30).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val history = library.history(300).stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    fun playlist(id: Long) = library.playlist(id)
    fun playlistItems(id: Long) = library.playlistItems(id)

    // ================================================================ player

    private val conn = PlayerConnection(app, viewModelScope, ::say)
    val player: StateFlow<PlayerUiState> = conn.state
    val queue: StateFlow<List<QueueEntry>> = conn.queue

    /** Every track handed to the player, so the UI can show full details for the current one. */
    private val known = ConcurrentHashMap<String, Track>()
    private var queueJob: Job? = null

    val currentTrack: StateFlow<Track?> = player.map { s ->
        val key = s.currentKey ?: return@map null
        known[key] ?: Track(
            id = key.substringAfter(':'), sourceId = key.substringBefore(':'),
            title = s.title ?: "Unknown title", artist = s.artist ?: "",
            artworkUrl = s.artworkUri,
        )
    }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun sourceName(sourceId: String): String = when (sourceId) {
        DOWNLOADS_SOURCE_ID -> "Downloads"
        WEB_SOURCE_ID -> "Web link"
        else -> c.sources.get(sourceId)?.name
            ?: appState.value.sources.firstOrNull { it.instanceId == sourceId }?.name ?: sourceId
    }

    /**
     * Plays [list] starting at [startIndex]. The first song starts at once; the rest of the list
     * is resolved and queued in the background (songs after it, then the ones before it).
     */
    fun playList(list: List<Track>, startIndex: Int = 0, shuffle: Boolean = false) {
        if (list.isEmpty()) return
        val ordered: List<Track>
        val start: Int
        if (shuffle) {
            ordered = list.shuffled()
            start = 0
        } else {
            ordered = list
            start = startIndex.coerceIn(list.indices)
        }
        queueJob?.cancel()
        _radioSeed.value = null
        queueJob = viewModelScope.launch {
            val controller = conn.connect()
            val first = resolve(ordered[start]) ?: return@launch
            controller.shuffleModeEnabled = false
            controller.setMediaItem(first)
            controller.prepare()
            controller.play()
            if (ordered[start].isLive) return@launch
            for (next in ordered.drop(start + 1).take(300)) {
                if (next.isLive) continue
                resolve(next, quiet = true)?.let { controller.addMediaItem(it) }
            }
            val before = ordered.take(start).takeLast(100).filterNot { it.isLive }
                .mapNotNull { resolve(it, quiet = true) }
            if (before.isNotEmpty()) controller.addMediaItems(0, before)
        }
    }

    fun play(track: Track, context: List<Track> = listOf(track)) {
        val index = context.indexOfFirst { it.key == track.key }
        if (index < 0) playList(listOf(track)) else playList(context, index)
    }

    fun enqueue(tracks: List<Track>, playNext: Boolean) {
        viewModelScope.launch {
            val controller = conn.connect()
            val items = tracks.filterNot { it.isLive }.mapNotNull { resolve(it, quiet = tracks.size > 1) }
            if (items.isEmpty()) return@launch
            if (controller.mediaItemCount == 0) {
                controller.setMediaItems(items)
                controller.prepare()
                controller.play()
            } else if (playNext) {
                controller.addMediaItems(controller.currentMediaItemIndex + 1, items)
            } else {
                controller.addMediaItems(items)
            }
            say(
                when {
                    items.size > 1 -> "${items.size} songs ${if (playNext) "will play next" else "added to queue"}"
                    playNext -> "Playing next: ${tracks.first().title}"
                    else -> "Added to queue: ${tracks.first().title}"
                },
            )
        }
    }

    fun enqueue(track: Track, playNext: Boolean) = enqueue(listOf(track), playNext)

    /**
     * "Go to radio": plays this song, then a mix of songs by the same artist from your library
     * and sources, plus songs you liked or played recently that fit.
     */
    fun startRadio(seed: Track) {
        say("Starting radio based on ${seed.title}")
        viewModelScope.launch {
            val sameArtist = (local.value.tracks + downloads.value + likedTracks.value)
                .filter { it.artist.equals(seed.artist, ignoreCase = true) }
            val online = if (seed.artist.isNotBlank()) searchEverywhere(seed.artist, artistOnly = true) else emptyList()
            val extras = (likedTracks.value + recentTracks.value).shuffled().take(10)
            val pool = (sameArtist + online).filterNot { it.key == seed.key || it.isLive }.distinctBy { it.key }.shuffled().take(25)
            val mix = (pool + extras.filterNot { e -> e.key == seed.key || pool.any { it.key == e.key } }).take(30)
            if (mix.isEmpty()) return@launch say("Couldn't find enough similar songs for a radio")
            playList(listOf(seed) + mix, 0)
            _radioSeed.value = seed
        }
    }

    private val _radioSeed = MutableStateFlow<Track?>(null)
    val radioSeed: StateFlow<Track?> = _radioSeed.asStateFlow()

    /** On a cold start, puts the last queue back (paused) so the user can carry on. */
    private suspend fun restoreQueue() {
        val controller = conn.connect()
        if (controller.mediaItemCount > 0) {
            // Service still running: learn the details of what's already queued.
            (0 until controller.mediaItemCount).forEach { i ->
                val key = controller.getMediaItemAt(i).mediaId
                if (!known.containsKey(key)) library.track(key)?.let { known[key] = it }
            }
            conn.refresh()
            return
        }
        if (!settings.value.resumeQueue) return
        val saved = withContext(Dispatchers.IO) { app.wavelet.player.QueueStore.load(getApplication()) } ?: return
        with(app.wavelet.player.QueueStore) {
            saved.items.forEach { item -> library.track(item.key)?.let { known[item.key] = it } }
            controller.setMediaItems(saved.items.map { it.toMediaItem() }, saved.index, saved.positionMs)
        }
        controller.prepare()
        conn.refresh()
    }

    fun setAppVisible(visible: Boolean) { c.appVisible.value = visible }

    private suspend fun resolve(track: Track, quiet: Boolean = false): MediaItem? {
        val result = attempt {
            val offline = if (settings.value.preferOffline) c.downloads.find(track.key)?.streamUrl else null
            val url = offline
                ?: (c.sources.get(track.sourceId) as? MusicSource)?.resolveStream(track)?.url
                ?: track.streamUrl
                ?: error("${sourceName(track.sourceId)} is turned off")
            track.toMediaItem(url)
        }
        result.onSuccess { known[track.key] = track }
        result.exceptionOrNull()?.let { if (!quiet) say("Couldn't play ${track.title}: ${friendly(it)}") }
        return result.getOrNull()
    }

    fun togglePlay() {
        val p = conn.now ?: return
        if (p.isPlaying) p.pause() else {
            if (p.playbackState == androidx.media3.common.Player.STATE_IDLE) p.prepare()
            p.play()
        }
    }

    fun next() { conn.now?.seekToNext() }
    fun previous() { conn.now?.seekToPrevious() }
    fun seekTo(ms: Long) { conn.now?.seekTo(ms); conn.refresh() }
    fun toggleShuffle() { conn.now?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled } }
    fun cycleRepeat() = conn.setRepeat(
        when (player.value.repeat) {
            RepeatMode.Off -> RepeatMode.All
            RepeatMode.All -> RepeatMode.One
            RepeatMode.One -> RepeatMode.Off
        },
    )
    fun setSpeed(speed: Float) { conn.now?.playbackParameters = PlaybackParameters(speed) }
    fun playQueueItem(index: Int) { conn.now?.let { it.seekToDefaultPosition(index); it.play() } }
    fun removeQueueItem(index: Int) { conn.now?.removeMediaItem(index) }
    fun moveQueueItem(from: Int, to: Int) {
        val p = conn.now ?: return
        if (to in 0 until p.mediaItemCount) p.moveMediaItem(from, to)
    }
    fun clearUpcoming() {
        val p = conn.now ?: return
        val cur = p.currentMediaItemIndex
        if (cur + 1 < p.mediaItemCount) p.removeMediaItems(cur + 1, p.mediaItemCount)
    }
    val audioSessionId: Int get() = conn.audioSessionId

    // ---------------------------------------------------------------- sleep timer

    private val _sleep = MutableStateFlow<SleepTimer?>(null)
    val sleep: StateFlow<SleepTimer?> = _sleep.asStateFlow()
    private var sleepJob: Job? = null

    /** Pauses after [minutes]; with null, at the end of the current song. */
    fun setSleepTimer(minutes: Int?) {
        sleepJob?.cancel()
        val ms = minutes?.let { it * 60_000L }
            ?: (player.value.durationMs - player.value.positionMs).takeIf { player.value.durationMs > 0 }
            ?: return say("This stream has no end; pick a time instead")
        val label = if (minutes == null) "End of song" else "$minutes min"
        _sleep.value = SleepTimer(System.currentTimeMillis() + ms, label)
        say("Sleep timer: $label")
        sleepJob = viewModelScope.launch {
            delay(ms)
            conn.now?.pause()
            _sleep.value = null
            say("Sleep timer ended")
        }
    }

    fun cancelSleepTimer() {
        sleepJob?.cancel()
        _sleep.value = null
    }

    // ---------------------------------------------------------------- listening history & scrobbling

    private var playKey: String? = null
    private var playMs = 0L
    private var lastPos = 0L
    private var counted = false
    private var playStartedAt = 0L

    private fun trackListening(s: PlayerUiState) {
        val track = currentTrack.value
        if (s.currentKey != playKey) {
            playKey = s.currentKey
            playMs = 0
            counted = false
            lastPos = s.positionMs
            playStartedAt = System.currentTimeMillis() / 1000
            if (track != null && s.currentKey != null) {
                viewModelScope.launch { library.remember(track) }
                scrobble(track, nowPlaying = true)
            }
            return
        }
        if (s.positionMs < lastPos - 5_000 && s.positionMs < 3_000) {
            // Repeat-one restarted the song: count it again.
            playMs = 0
            counted = false
            playStartedAt = System.currentTimeMillis() / 1000
        } else if (s.isPlaying) {
            val delta = s.positionMs - lastPos
            if (delta in 1..2_500) playMs += delta
            else if (track?.isLive == true) playMs += 400
        }
        lastPos = s.positionMs
        if (counted || track == null) return
        val duration = track.durationMs ?: s.durationMs
        val threshold = when {
            track.isLive -> 60_000L
            duration in 1..29_999 -> duration * 8 / 10
            duration > 0 -> minOf(240_000L, duration / 2)
            else -> 30_000L
        }
        if (playMs >= threshold) {
            counted = true
            val listened = if (!track.isLive && duration > 0) duration else playMs
            if (settings.value.recordHistory) viewModelScope.launch { library.recordPlay(track, listened) }
            scrobble(track, nowPlaying = false)
        }
    }

    private fun scrobble(track: Track, nowPlaying: Boolean) {
        val s = settings.value
        val startedAt = playStartedAt
        if (s.scrobble && s.listenBrainzToken.isNotBlank() && !track.isLive) {
            c.appScope.launch {
                attempt {
                    if (nowPlaying) ListenBrainz.playingNow(s.listenBrainzToken, track)
                    else ListenBrainz.listened(s.listenBrainzToken, track, startedAt)
                }.onFailure { Log.w("Wavelet", "ListenBrainz submit failed", it) }
            }
        }
        if (s.lastFmScrobble && s.lastFmSession.isNotBlank() && !track.isLive) {
            c.appScope.launch {
                attempt {
                    if (nowPlaying) LastFm.playingNow(s.lastFmApiKey, s.lastFmSecret, s.lastFmSession, track)
                    else LastFm.scrobble(s.lastFmApiKey, s.lastFmSecret, s.lastFmSession, track, startedAt)
                }.onFailure { Log.w("Wavelet", "Last.fm submit failed", it) }
            }
        }
    }

    // ================================================================ lyrics & links

    private val _lyrics = MutableStateFlow(LyricsState())
    val lyrics: StateFlow<LyricsState> = _lyrics.asStateFlow()
    private var lyricsJob: Job? = null

    private fun loadLyrics(track: Track?) {
        lyricsJob?.cancel()
        if (track == null || track.isLive) {
            _lyrics.value = LyricsState(track?.key)
            return
        }
        _lyrics.value = LyricsState(track.key, loading = true)
        lyricsJob = viewModelScope.launch {
            var found: Lyrics? = null
            for (source in c.sources.lyrics()) {
                found = attempt { source.lyrics(track) }.getOrNull()
                if (found != null) break
            }
            _lyrics.value = LyricsState(track.key, loading = false, lyrics = found)
        }
    }

    private val _links = MutableStateFlow<LinksState?>(null)
    val links: StateFlow<LinksState?> = _links.asStateFlow()

    fun showLinks(track: Track? = currentTrack.value) {
        track ?: return
        val source = c.sources.links().firstOrNull()
            ?: return say("Turn on \"Song links\" in Settings › Sources first")
        _links.value = LinksState()
        viewModelScope.launch {
            val result = attempt { source.links(track) }
            _links.value = LinksState(
                loading = false,
                links = result.getOrDefault(emptyMap()),
                error = result.exceptionOrNull()?.let(::friendly),
            )
        }
    }

    fun closeLinks() { _links.value = null }

    // ================================================================ home & search

    val musicSources: StateFlow<List<MusicSource>> = sources
        .map { list -> list.filterIsInstance<MusicSource>() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _featured = MutableStateFlow<List<SourceResults>>(emptyList())
    val featured: StateFlow<List<SourceResults>> = _featured.asStateFlow()
    private var featuredJob: Job? = null

    fun refreshFeatured() {
        featuredJob?.cancel()
        val targets = musicSources.value.filter { it.showInDiscover }
        _featured.value = targets.map { SourceResults(it.id, it.name) }
        featuredJob = viewModelScope.launch {
            targets.forEach { s ->
                launch {
                    val r = attempt { s.featured() }
                    _featured.update { list ->
                        list.map {
                            if (it.sourceId != s.id) it
                            else it.copy(tracks = r.getOrDefault(emptyList()).distinctBy { t -> t.key }, loading = false, error = r.exceptionOrNull()?.let(::friendly))
                        }
                    }
                }
            }
        }
    }

    private val _search = MutableStateFlow(SearchState())
    val search: StateFlow<SearchState> = _search.asStateFlow()
    private var searchJob: Job? = null

    fun setQuery(query: String) {
        _search.update { it.copy(query = query) }
        if (query.isBlank()) {
            searchJob?.cancel()
            _search.update { it.copy(submitted = "", sections = emptyList()) }
        }
    }

    fun submitSearch(query: String = _search.value.query) {
        _search.update { it.copy(query = query, submitted = query.trim()) }
        val q = query.trim()
        if (q.isNotEmpty()) c.store.update { st ->
            st.copy(recentSearches = (listOf(q) + st.recentSearches.filterNot { it.equals(q, ignoreCase = true) }).take(15))
        }
        runSearch()
    }

    val recentSearches: StateFlow<List<String>> = appState.map { it.recentSearches }
        .stateIn(viewModelScope, SharingStarted.Eagerly, appState.value.recentSearches)

    fun removeRecentSearch(q: String) = c.store.update { it.copy(recentSearches = it.recentSearches - q) }
    fun clearRecentSearches() = c.store.update { it.copy(recentSearches = emptyList()) }

    fun setFilter(sourceId: String?) {
        _search.update { it.copy(filter = sourceId) }
        runSearch()
    }

    private fun runSearch() {
        searchJob?.cancel()
        val state = _search.value
        val q = state.submitted
        if (q.isBlank()) return
        val targets = musicSources.value.filter { state.filter == null || it.id == state.filter }
        _search.update { it.copy(sections = targets.map { s -> SourceResults(s.id, s.name) }) }
        searchJob = viewModelScope.launch {
            targets.forEach { source ->
                launch {
                    val result = attempt { source.search(q) }
                    _search.update { st ->
                        st.copy(sections = st.sections.map { sec ->
                            if (sec.sourceId != source.id) sec
                            else sec.copy(
                                tracks = result.getOrDefault(emptyList()).distinctBy { it.key },
                                loading = false,
                                error = result.exceptionOrNull()?.let(::friendly),
                            )
                        })
                    }
                }
            }
        }
    }

    /** Searches the online/server sources (not radio) at once; used by artist pages. */
    suspend fun searchEverywhere(query: String, artistOnly: Boolean = false): List<Track> = coroutineScope {
        musicSources.value.filter { it.showInDiscover && it.id != "radio_browser" }
            .map { s -> async { attempt { s.search(query, 20) }.getOrDefault(emptyList()) } }
            .awaitAll().flatten()
            .filter { t ->
                !t.isLive && (!artistOnly || TrackMatcher.similarity(TrackMatcher.normalize(t.artist), TrackMatcher.normalize(query)) >= 0.8)
            }
            .distinctBy { it.key }
    }

    // ================================================================ device library

    private val _local = MutableStateFlow(LocalState())
    val local: StateFlow<LocalState> = _local.asStateFlow()

    fun refreshLocal() {
        val source = sources.value.filterIsInstance<LocalSource>().firstOrNull()
        if (source == null) {
            _local.value = LocalState()
            return
        }
        _local.update { it.copy(loading = true, hasPermission = source.hasPermission()) }
        viewModelScope.launch {
            val tracks = attempt { source.featured() }.getOrDefault(emptyList())
            _local.value = LocalState(false, tracks, source.hasPermission())
        }
    }

    fun onPermissionsChanged() {
        refreshLocal()
        if (_search.value.submitted.isNotBlank()) runSearch()
    }

    // ================================================================ likes & playlists

    fun toggleLike(track: Track) {
        val liked = track.key in likedKeys.value
        viewModelScope.launch {
            library.setLiked(track, !liked)
            say(if (liked) "Removed from Liked Songs" else "Added to Liked Songs")
        }
    }

    private val _options = MutableStateFlow<TrackOptions?>(null)
    val options: StateFlow<TrackOptions?> = _options.asStateFlow()
    fun showOptions(track: Track, playlistId: Long? = null, itemId: Long? = null) {
        _options.value = TrackOptions(track, playlistId, itemId)
    }
    fun closeOptions() { _options.value = null }

    private val _addToPlaylist = MutableStateFlow<List<Track>?>(null)
    val addToPlaylist: StateFlow<List<Track>?> = _addToPlaylist.asStateFlow()
    fun askAddToPlaylist(tracks: List<Track>) { _addToPlaylist.value = tracks.filterNot { it.isLive }.ifEmpty { null } }
    fun closeAddToPlaylist() { _addToPlaylist.value = null }

    fun addToPlaylist(playlistId: Long, tracks: List<Track>) {
        _addToPlaylist.value = null
        viewModelScope.launch {
            val fresh = if (tracks.size == 1 && library.isInPlaylist(playlistId, tracks[0])) emptyList() else tracks
            if (fresh.isEmpty()) return@launch say("Already in this playlist")
            library.addTracks(playlistId, fresh)
            val name = library.playlistNow(playlistId)?.name ?: "playlist"
            say(if (fresh.size == 1) "Added to $name" else "Added ${fresh.size} songs to $name")
        }
    }

    fun createPlaylist(name: String, description: String = "", tracks: List<Track> = emptyList(), onCreated: (Long) -> Unit = {}) {
        _addToPlaylist.value = null
        viewModelScope.launch {
            val id = library.createPlaylist(name, description)
            if (tracks.isNotEmpty()) library.addTracks(id, tracks)
            say("Created ${name.ifBlank { "playlist" }}")
            onCreated(id)
        }
    }

    fun renamePlaylist(id: Long, name: String, description: String) =
        viewModelScope.launch { library.renamePlaylist(id, name, description) }

    fun deletePlaylist(id: Long) = viewModelScope.launch {
        library.deletePlaylist(id)
        say("Playlist deleted")
    }

    fun removeFromPlaylist(playlistId: Long, itemId: Long) =
        viewModelScope.launch { library.removeItem(playlistId, itemId) }

    fun moveInPlaylist(playlistId: Long, from: Int, to: Int) =
        viewModelScope.launch { library.moveItem(playlistId, from, to) }

    fun playPlaylist(items: List<PlaylistItemWithTrack>, start: PlaylistItemWithTrack? = null, shuffle: Boolean = false) {
        val tracks = items.mapNotNull { it.track }
        if (tracks.isEmpty()) return say("No playable songs in this playlist yet")
        val index = start?.track?.let { t -> tracks.indexOfFirst { it.key == t.key } } ?: 0
        playList(tracks, index.coerceAtLeast(0), shuffle)
    }

    // ---------------------------------------------------------------- fix match

    private val _fixMatch = MutableStateFlow<FixMatchState?>(null)
    val fixMatch: StateFlow<FixMatchState?> = _fixMatch.asStateFlow()
    private var fixJob: Job? = null

    fun openFixMatch(playlistId: Long, item: PlaylistItemWithTrack) {
        val entry = PlaylistEntry(item.itemTitle, item.itemArtist, item.itemAlbum, item.itemDurationMs)
        val q = listOf(TrackMatcher.firstArtist(item.itemArtist), item.itemTitle).filter { it.isNotBlank() }.joinToString(" ")
        _fixMatch.value = FixMatchState(playlistId, item.itemId, entry, q)
        searchFixMatch(q)
    }

    fun searchFixMatch(query: String) {
        val st = _fixMatch.value ?: return
        _fixMatch.value = st.copy(query = query, loading = true, candidates = emptyList())
        fixJob?.cancel()
        fixJob = viewModelScope.launch {
            val list = c.matcher.allCandidates(st.entry, query.trim().ifBlank { null })
            _fixMatch.update { it?.copy(loading = false, candidates = list.take(40)) }
        }
    }

    fun chooseMatch(track: Track?) {
        val st = _fixMatch.value ?: return
        _fixMatch.value = null
        viewModelScope.launch {
            library.setMatch(st.playlistId, st.itemId, track)
            say(if (track == null) "Match removed" else "Matched to ${track.title}")
        }
    }

    fun closeFixMatch() { _fixMatch.value = null }

    // ---------------------------------------------------------------- import & export

    private val _import = MutableStateFlow<ImportState?>(null)
    val import: StateFlow<ImportState?> = _import.asStateFlow()

    fun importFile(uri: Uri) {
        val ctx = getApplication<Application>()
        c.appScope.launch {
            val name = displayName(uri) ?: "playlist"
            _import.value = ImportState(name)
            val parsed = attempt {
                val text = withContext(Dispatchers.IO) {
                    ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                } ?: error("Couldn't open the file")
                PlaylistFormats.parse(name, text)
            }
            parsed.onSuccess { runImport(it) }
                .onFailure { e -> _import.value = ImportState(name, finished = true, error = friendly(e)) }
        }
    }

    fun importFromLink(idOrUrl: String) {
        val trimmed = idOrUrl.trim()
        c.appScope.launch {
            val label = when {
                trimmed.contains("spotify.com") -> "Spotify import"
                trimmed.contains("youtube.com") -> "YouTube import"
                else -> "Playlist link"
            }
            _import.value = ImportState(label)
            attempt {
                when {
                    trimmed.contains("spotify.com") -> PlaylistFormats.parseSpotify(trimmed)
                    trimmed.contains("youtube.com") -> PlaylistFormats.parseYouTube(trimmed)
                    else -> ListenBrainz.playlist(trimmed, settings.value.listenBrainzToken)
                }
            }.onSuccess { runImport(it) }
            .onFailure { e -> _import.value = ImportState(label, finished = true, error = friendly(e)) }
        }
    }

    fun importListenBrainz(idOrUrl: String) = importFromLink(idOrUrl)

    private suspend fun runImport(p: ParsedPlaylist) {
        _import.value = ImportState(p.name, 0, p.entries.size)
        val matches = c.matcher.matchAll(p.entries, p.exact) { done, total ->
            _import.update { it?.copy(done = done, total = total) }
        }
        val id = library.createPlaylist(p.name, "Imported")
        library.addEntries(id, p.entries, matches)
        _import.value = ImportState(p.name, p.entries.size, p.entries.size, true, matches.count { it != null }, id)
    }

    fun dismissImport() { _import.value = null }

    fun exportPlaylist(playlistId: Long, format: ExportFormat, target: Uri) {
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            val result = attempt {
                val p = library.playlistNow(playlistId) ?: error("Playlist not found")
                val rows = library.playlistItemsNow(playlistId).map { i ->
                    val t = i.track
                    PlaylistFormats.ExportRow(
                        t?.title ?: i.itemTitle, t?.artist ?: i.itemArtist, t?.album ?: i.itemAlbum,
                        t?.durationMs ?: i.itemDurationMs, t, t?.let { sourceName(it.sourceId) },
                    )
                }
                val text = PlaylistFormats.export(format, p.name, p.description, rows)
                withContext(Dispatchers.IO) {
                    ctx.contentResolver.openOutputStream(target, "wt")?.use { it.write(text.toByteArray()) }
                        ?: error("Couldn't write the file")
                }
                rows.size
            }
            say(result.fold({ "Exported $it songs" }, { "Export failed: ${friendly(it)}" }))
        }
    }

    /** Builds the share text for a playlist (M3U8), for the system share sheet. */
    suspend fun playlistShareText(playlistId: Long): String {
        val p = library.playlistNow(playlistId) ?: return ""
        return buildString {
            appendLine(p.name)
            library.playlistItemsNow(playlistId).forEachIndexed { i, item ->
                val t = item.track
                appendLine("${i + 1}. ${t?.artist ?: item.itemArtist} – ${t?.title ?: item.itemTitle}")
            }
        }
    }

    private fun displayName(uri: Uri): String? = runCatching {
        getApplication<Application>().contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }
    }.getOrNull() ?: uri.lastPathSegment

    // ---------------------------------------------------------------- backup

    fun exportBackup(target: Uri) {
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            val r = attempt {
                // Server passwords and tokens stay out of the backup file.
                val state = appState.value.let { s ->
                    s.copy(
                        sources = s.sources.map { cfg -> cfg.copy(options = cfg.options.filterKeys { it != "password" }) },
                        settings = s.settings.copy(listenBrainzToken = ""),
                    )
                }
                val backup = library.exportAll().copy(settings = state)
                val text = Http.json.encodeToString(LibraryBackup.serializer(), backup)
                withContext(Dispatchers.IO) {
                    ctx.contentResolver.openOutputStream(target, "wt")?.use { it.write(text.toByteArray()) } ?: error("Couldn't write the file")
                }
                backup
            }
            say(r.fold({ "Backup saved: ${it.playlists.size} playlists, ${it.likes.size} liked songs" }, { "Backup failed: ${friendly(it)}" }))
        }
    }

    fun importBackup(source: Uri) {
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            val r = attempt {
                val text = withContext(Dispatchers.IO) {
                    ctx.contentResolver.openInputStream(source)?.use { it.readBytes().toString(Charsets.UTF_8) }
                } ?: error("Couldn't open the file")
                val backup = Http.json.decodeFromString(LibraryBackup.serializer(), text)
                if (backup.format != "wavelet-backup") error("This isn't a Wavelet backup")
                library.importAll(backup)
                backup.settings?.let { restored ->
                    c.updateState { cur ->
                        val ids = cur.sources.map { it.instanceId }.toSet()
                        cur.copy(
                            sources = cur.sources + restored.sources.filter { it.instanceId !in ids },
                            repos = (cur.repos + restored.repos).distinct(),
                        )
                    }
                }
                backup
            }
            say(r.fold({ "Restored ${it.playlists.size} playlists and ${it.likes.size} liked songs" }, { "Restore failed: ${friendly(it)}" }))
        }
    }

    // ================================================================ stats

    private val _stats = MutableStateFlow<StatsState?>(null)
    val stats: StateFlow<StatsState?> = _stats.asStateFlow()

    fun loadStats(year: Int? = null, month: Int? = null) {
        val cal = Calendar.getInstance()
        val y = year ?: cal.get(Calendar.YEAR)
        val m = month ?: cal.get(Calendar.MONTH)
        _stats.value = StatsState(y, m)
        viewModelScope.launch {
            _stats.value = StatsState(y, m, false, library.monthStats(y, m))
        }
    }

    fun shiftStatsMonth(delta: Int) {
        val s = _stats.value ?: return loadStats()
        val cal = Calendar.getInstance().apply { clear(); set(s.year, s.month, 1); add(Calendar.MONTH, delta) }
        loadStats(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH))
    }

    fun clearHistory() = viewModelScope.launch {
        library.clearHistory()
        say("Listening history cleared")
        _stats.value?.let { loadStats(it.year, it.month) }
    }

    // ================================================================ downloads

    fun download(track: Track) {
        if (track.isLive) return say("Radio streams can't be downloaded")
        if (c.downloads.find(track.key) != null) return say("Already downloaded")
        if (c.downloads.isBusy(track.key)) return
        val source = c.sources.get(track.sourceId) as? MusicSource
            ?: return say("${sourceName(track.sourceId)} is turned off")
        c.appScope.launch {
            val result = attempt {
                val stream = source.resolveDownload(track)
                    ?: error("${source.name} doesn't allow downloading this song")
                c.downloads.download(track, stream)
            }
            say(result.fold({ "Downloaded ${track.title}" }, { "Download failed: ${friendly(it)}" }))
        }
    }

    fun downloadAll(tracks: List<Track>) {
        val todo = tracks.filter { it.downloadable && c.downloads.find(it.key) == null }
        if (todo.isEmpty()) return say("Nothing new to download")
        say("Downloading ${todo.size} songs")
        todo.forEach { download(it) }
    }

    fun deleteDownload(track: Track) {
        c.downloads.delete(track)
        say("Removed ${track.title} from downloads")
    }

    // ================================================================ settings, sources & repos

    fun updateSettings(transform: (Settings) -> Settings) = c.updateState { it.copy(settings = transform(it.settings)) }

    private val _lbConnecting = MutableStateFlow(false)
    val lbConnecting: StateFlow<Boolean> = _lbConnecting.asStateFlow()

    fun connectListenBrainz(token: String) {
        _lbConnecting.value = true
        viewModelScope.launch {
            val r = attempt { ListenBrainz.validate(token) }
            _lbConnecting.value = false
            r.onSuccess { user ->
                updateSettings { it.copy(listenBrainzToken = token.trim(), listenBrainzUser = user) }
                say("Connected to ListenBrainz as $user")
            }.onFailure { say("ListenBrainz: ${friendly(it)}") }
        }
    }

    fun disconnectListenBrainz() = updateSettings { it.copy(listenBrainzToken = "", listenBrainzUser = "") }

    private val _lb = MutableStateFlow(LbState())
    val lbPlaylists: StateFlow<LbState> = _lb.asStateFlow()

    fun loadListenBrainzPlaylists() {
        val s = settings.value
        if (s.listenBrainzUser.isBlank()) return
        _lb.value = LbState(loading = true)
        viewModelScope.launch {
            val r = attempt { ListenBrainz.playlists(s.listenBrainzUser, s.listenBrainzToken) }
            _lb.value = LbState(false, r.getOrDefault(emptyList()), r.exceptionOrNull()?.let(::friendly))
        }
    }

    private val _lastFmConnecting = MutableStateFlow(false)
    val lastFmConnecting: StateFlow<Boolean> = _lastFmConnecting.asStateFlow()

    fun connectLastFm(user: String, pass: String, apiKey: String, secret: String) {
        _lastFmConnecting.value = true
        viewModelScope.launch {
            val r = attempt { LastFm.getMobileSession(user, pass, apiKey, secret) }
            _lastFmConnecting.value = false
            r.onSuccess { (sessionKey, username) ->
                updateSettings {
                    it.copy(
                        lastFmApiKey = apiKey.trim(),
                        lastFmSecret = secret.trim(),
                        lastFmUser = username,
                        lastFmSession = sessionKey,
                        lastFmScrobble = true,
                    )
                }
                say("Connected to Last.fm as $username")
            }.onFailure { say("Last.fm: ${friendly(it)}") }
        }
    }

    fun disconnectLastFm() = updateSettings {
        it.copy(lastFmApiKey = "", lastFmSecret = "", lastFmUser = "", lastFmSession = "")
    }

    // ================================================================ AudioFX / Equalizer
    val audioFx = AudioFxController()
    private val _audioFxState = MutableStateFlow(AudioFxState())
    val audioFxState: StateFlow<AudioFxState> = _audioFxState.asStateFlow()

    private val _showEqualizer = MutableStateFlow(false)
    val showEqualizer: StateFlow<Boolean> = _showEqualizer.asStateFlow()
    fun openEqualizer() { _showEqualizer.value = true }
    fun closeEqualizer() { _showEqualizer.value = false }

    fun setEqualizerEnabled(enabled: Boolean) {
        audioFx.setEnabled(enabled)
        updateSettings { it.copy(equalizerEnabled = enabled) }
    }

    fun setEqualizerBand(band: Short, level: Short) {
        audioFx.setBandLevel(band, level)
    }

    fun setEqualizerPreset(preset: Short) {
        audioFx.usePreset(preset)
        updateSettings { it.copy(equalizerPreset = preset.toInt()) }
    }

    fun setBassBoost(strength: Short) {
        audioFx.setBassBoost(strength)
        updateSettings { it.copy(bassBoost = strength.toInt()) }
    }

    fun setVirtualizer(strength: Short) {
        audioFx.setVirtualizer(strength)
        updateSettings { it.copy(virtualizer = strength.toInt()) }
    }

    fun setEnabled(instanceId: String, enabled: Boolean) = c.updateState { s ->
        s.copy(sources = s.sources.map { if (it.instanceId == instanceId) it.copy(enabled = enabled) else it })
    }

    fun saveSource(cfg: SourceConfig) = c.updateState { s ->
        val exists = s.sources.any { it.instanceId == cfg.instanceId }
        s.copy(sources = if (exists) s.sources.map { if (it.instanceId == cfg.instanceId) cfg else it } else s.sources + cfg)
    }

    fun removeSource(instanceId: String) = c.updateState { s ->
        s.copy(sources = s.sources.filterNot { it.instanceId == instanceId })
    }

    fun newServerConfig(engine: String): SourceConfig = SourceConfig(
        instanceId = "$engine-" + UUID.randomUUID().toString().take(8),
        engine = engine,
        name = Engines.info(engine)?.displayName ?: engine,
    )

    private val _repoView = MutableStateFlow<RepoView?>(null)
    val repoView: StateFlow<RepoView?> = _repoView.asStateFlow()

    fun openRepo(rawUrl: String) {
        // Links pasted from chats often pick up stray spaces ("…/sources /private-extensions.json").
        val url = rawUrl.replace(Regex("\\s+"), "").let { if (it.startsWith("http://") || it.startsWith("https://")) it else "https://$it" }
        _repoView.value = RepoView(url)
        viewModelScope.launch {
            val result = attempt { c.repos.fetch(url) }
            _repoView.value = RepoView(url, false, result.getOrNull(), result.exceptionOrNull()?.let(::friendly))
            if (result.isSuccess) c.updateState { s -> if (url in s.repos) s else s.copy(repos = s.repos + url) }
        }
    }

    fun closeRepo() { _repoView.value = null }

    fun removeRepo(url: String) = c.updateState { s -> s.copy(repos = s.repos - url) }

    /** Adds a repo entry as a source. Returns the new config so the UI can ask for missing settings. */
    fun installFromRepo(repoUrl: String, entry: RepoEntry, quiet: Boolean = false): SourceConfig? {
        if (entry.unsupportedReason != null) {
            if (!quiet) say(entry.unsupportedReason)
            return null
        }
        if (Engines.info(entry.engine) == null) {
            if (!quiet) say("${entry.name} needs a newer version of Wavelet")
            return null
        }
        val existing = appState.value.sources.firstOrNull { it.instanceId == entry.id }
        val cfg = SourceConfig(
            instanceId = entry.id,
            engine = entry.engine,
            name = entry.name,
            enabled = true,
            options = entry.options + existing?.options.orEmpty().filterValues { it.isNotBlank() },
            fromRepo = repoUrl,
            version = entry.version,
        )
        saveSource(cfg)
        if (!quiet) say("Installed ${entry.name}")
        return cfg
    }

    /** Installs every usable entry that needs no extra settings; returns how many were added. */
    fun installAllFromRepo(repoUrl: String, entries: List<RepoEntry>): Int {
        val ready = entries.filter { e ->
            e.unsupportedReason == null && Engines.info(e.engine) != null &&
                Engines.info(e.engine)!!.options.all { o -> !e.options[o.key].isNullOrBlank() || appState.value.sources.any { it.instanceId == e.id && !it.options[o.key].isNullOrBlank() } }
        }
        ready.forEach { installFromRepo(repoUrl, it, quiet = true) }
        say(if (ready.isEmpty()) "Nothing to install without settings; tap Install on a source to set it up" else "Installed ${ready.size} sources")
        return ready.size
    }

    // ================================================================ lifecycle

    init {
        audioFx.onStateChanged = { st -> _audioFxState.value = st }
        viewModelScope.launch { attempt { restoreQueue() } }
        viewModelScope.launch {
            currentTrack.map { it?.key }.distinctUntilChanged().collect { loadLyrics(currentTrack.value) }
        }
        viewModelScope.launch { player.collect { trackListening(it) } }
        viewModelScope.launch {
            player.map { conn.audioSessionId }.distinctUntilChanged().collect { sid ->
                if (sid > 0) {
                    audioFx.attach(sid, settings.value.equalizerEnabled)
                    if (settings.value.bassBoost > 0) audioFx.setBassBoost(settings.value.bassBoost.toShort())
                    if (settings.value.virtualizer > 0) audioFx.setVirtualizer(settings.value.virtualizer.toShort())
                    if (settings.value.equalizerPreset >= 0) audioFx.usePreset(settings.value.equalizerPreset.toShort())
                }
            }
        }
        // Reload featured shelves and the device library whenever the set of sources changes.
        viewModelScope.launch {
            sources.collect {
                val filter = _search.value.filter
                if (filter != null && it.none { s -> s.id == filter }) _search.update { st -> st.copy(filter = null) }
                refreshFeatured()
                refreshLocal()
                if (_search.value.submitted.isNotBlank()) runSearch()
            }
        }
    }

    override fun onCleared() {
        audioFx.release()
        conn.release()
    }
}

fun friendly(e: Throwable): String = when (e) {
    is UnknownHostException -> "No internet connection"
    is HttpException -> when (e.code) {
        401, 403 -> "Access denied (check your login or API key)"
        404 -> "Not found"
        429 -> "Too many requests, try again later"
        in 500..599 -> "The server had a problem (${e.code})"
        else -> "Server returned ${e.code}"
    }
    else -> e.message ?: e.javaClass.simpleName
}
