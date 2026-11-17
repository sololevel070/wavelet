package app.wavelet.data

import app.wavelet.data.db.ArtistCount
import app.wavelet.data.db.HistoryEntity
import app.wavelet.data.db.LibraryDao
import app.wavelet.data.db.LikeEntity
import app.wavelet.data.db.PlayCount
import app.wavelet.data.db.PlaylistEntity
import app.wavelet.data.db.PlaylistItemEntity
import app.wavelet.data.db.TrackEntity
import app.wavelet.source.Track
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Calendar

/** A song as listed in an imported or exported playlist file. */
data class PlaylistEntry(
    val title: String,
    val artist: String,
    val album: String? = null,
    val durationMs: Long? = null,
    /** A direct URL or file path from the file, if it had one. */
    val location: String? = null,
)

data class MonthStats(
    val year: Int,
    val month: Int,
    val listenedMs: Long,
    val plays: Int,
    val topTracks: List<PlayCount>,
    val topArtists: List<ArtistCount>,
)

/** Playlists, liked songs and listening history, stored in Room. */
class LibraryRepository(private val dao: LibraryDao) {

    val playlists = dao.playlistSummaries()
    val likedKeys: Flow<Set<String>> = dao.likedKeys().map { it.toSet() }
    val likedTracks: Flow<List<Track>> = dao.likedTracks().map { list -> list.map { it.toTrack() } }
    fun recentTracks(limit: Int = 20): Flow<List<Track>> = dao.recentTracks(limit).map { l -> l.map { it.toTrack() } }
    fun history(limit: Int = 300) = dao.recentHistory(limit)
    fun playlist(id: Long) = dao.playlist(id)
    fun playlistItems(id: Long) = dao.playlistItems(id)
    suspend fun playlistItemsNow(id: Long) = dao.playlistItemsNow(id)
    suspend fun playlistNow(id: Long) = dao.playlistNow(id)

    suspend fun remember(track: Track) = dao.upsertTrack(TrackEntity.of(track))

    suspend fun track(key: String): Track? = dao.track(key)?.toTrack()

    // ---- playlists

    suspend fun createPlaylist(name: String, description: String = ""): Long =
        dao.insertPlaylist(PlaylistEntity(name = name.trim().ifBlank { "New playlist" }, description = description.trim()))

    /** Creates a playlist linked to a folder and fills it with [tracks]. */
    suspend fun createFolderPlaylist(name: String, folderUri: String, tracks: List<Track>): Long {
        val id = dao.insertPlaylist(PlaylistEntity(name = name.trim().ifBlank { "Music folder" }, description = "From a folder on this phone", folderUri = folderUri))
        addTracks(id, tracks)
        return id
    }

    /** Adds the tracks from a re-scan that aren't in the playlist yet; returns how many were added. */
    suspend fun addNewTracks(playlistId: Long, tracks: List<Track>): Int {
        val have = dao.playlistItemsNow(playlistId).mapNotNull { it.trackKey }.toSet()
        val fresh = tracks.filter { it.key !in have }
        addTracks(playlistId, fresh)
        return fresh.size
    }

    suspend fun renamePlaylist(id: Long, name: String, description: String) =
        dao.renamePlaylist(id, name.trim().ifBlank { "Untitled" }, description.trim())

    suspend fun deletePlaylist(id: Long) = dao.deletePlaylist(id)

    suspend fun isInPlaylist(id: Long, track: Track) = dao.countInPlaylist(id, track.key) > 0

    suspend fun addTracks(playlistId: Long, tracks: List<Track>) {
        if (tracks.isEmpty()) return
        dao.upsertTracks(tracks.map(TrackEntity::of))
        var pos = dao.maxPosition(playlistId)
        dao.insertItems(tracks.map { t ->
            PlaylistItemEntity(
                playlistId = playlistId, position = ++pos, trackKey = t.key,
                title = t.title, artist = t.artist, album = t.album, durationMs = t.durationMs,
            )
        })
        dao.touchPlaylist(playlistId)
    }

    /** Adds imported rows; [matches] gives the matched track for each entry, or null. */
    suspend fun addEntries(playlistId: Long, entries: List<PlaylistEntry>, matches: List<Track?>) {
        matches.filterNotNull().takeIf { it.isNotEmpty() }?.let { dao.upsertTracks(it.map(TrackEntity::of)) }
        var pos = dao.maxPosition(playlistId)
        dao.insertItems(entries.mapIndexed { i, e ->
            PlaylistItemEntity(
                playlistId = playlistId, position = ++pos, trackKey = matches.getOrNull(i)?.key,
                title = e.title, artist = e.artist, album = e.album, durationMs = e.durationMs,
            )
        })
        dao.touchPlaylist(playlistId)
    }

    suspend fun removeItem(playlistId: Long, itemId: Long) {
        dao.deleteItem(itemId)
        dao.touchPlaylist(playlistId)
    }

    /** Moves the item at [from] to [to] and renumbers the playlist. */
    suspend fun moveItem(playlistId: Long, from: Int, to: Int) {
        val items = dao.playlistItemsNow(playlistId).toMutableList()
        if (from !in items.indices || to !in items.indices) return
        items.add(to, items.removeAt(from))
        items.forEachIndexed { i, item -> if (item.position != i) dao.setPosition(item.itemId, i) }
        dao.touchPlaylist(playlistId)
    }

    suspend fun setMatch(playlistId: Long, itemId: Long, track: Track?) {
        track?.let { remember(it) }
        dao.setItemTrack(itemId, track?.key)
        dao.touchPlaylist(playlistId)
    }

    // ---- likes

    suspend fun setLiked(track: Track, liked: Boolean) {
        if (liked) {
            remember(track)
            dao.like(LikeEntity(track.key))
        } else {
            dao.unlike(track.key)
        }
    }

    // ---- history & stats

    suspend fun recordPlay(track: Track, listenedMs: Long) {
        remember(track)
        dao.insertHistory(HistoryEntity(trackKey = track.key, playedAt = System.currentTimeMillis(), listenedMs = listenedMs))
    }

    suspend fun clearHistory() = dao.clearHistory()

    suspend fun monthStats(year: Int, month: Int): MonthStats {
        val cal = Calendar.getInstance().apply {
            clear()
            set(year, month, 1)
        }
        val from = cal.timeInMillis
        cal.add(Calendar.MONTH, 1)
        val to = cal.timeInMillis
        return MonthStats(
            year, month,
            listenedMs = dao.listenedBetween(from, to),
            plays = dao.playsBetween(from, to),
            topTracks = dao.topTracks(from, to, 10),
            topArtists = dao.topArtists(from, to, 10),
        )
    }

    // ---- backup

    suspend fun exportAll(): LibraryBackup = LibraryBackup(
        tracks = dao.allTracks().map { it.toTrack() },
        playlists = dao.allPlaylists().map { p ->
            BackupPlaylist(p.name, p.description, dao.playlistItemsNow(p.id).map { i ->
                BackupItem(i.trackKey, i.itemTitle, i.itemArtist, i.itemAlbum, i.itemDurationMs)
            })
        },
        likes = dao.allLikes().map { BackupLike(it.trackKey, it.likedAt) },
        history = dao.allHistory().map { BackupHistory(it.trackKey, it.playedAt, it.listenedMs) },
    )

    /** Merges a backup into the library (existing data is kept). */
    suspend fun importAll(b: LibraryBackup) {
        dao.upsertTracks(b.tracks.map(TrackEntity::of))
        b.playlists.forEach { p ->
            val id = dao.insertPlaylist(PlaylistEntity(name = p.name, description = p.description))
            dao.insertItems(p.items.mapIndexed { i, item ->
                PlaylistItemEntity(
                    playlistId = id, position = i, trackKey = item.trackKey,
                    title = item.title, artist = item.artist, album = item.album, durationMs = item.durationMs,
                )
            })
        }
        b.likes.forEach { dao.like(LikeEntity(it.trackKey, it.likedAt)) }
        val existing = dao.allHistory().map { it.trackKey to it.playedAt }.toSet()
        b.history.filter { (it.trackKey to it.playedAt) !in existing }.forEach {
            dao.insertHistory(HistoryEntity(trackKey = it.trackKey, playedAt = it.playedAt, listenedMs = it.listenedMs))
        }
    }
}

@kotlinx.serialization.Serializable
data class LibraryBackup(
    val format: String = "wavelet-backup",
    val version: Int = 1,
    val tracks: List<Track> = emptyList(),
    val playlists: List<BackupPlaylist> = emptyList(),
    val likes: List<BackupLike> = emptyList(),
    val history: List<BackupHistory> = emptyList(),
    val settings: AppState? = null,
)

@kotlinx.serialization.Serializable
data class BackupPlaylist(val name: String, val description: String = "", val items: List<BackupItem> = emptyList())

@kotlinx.serialization.Serializable
data class BackupItem(val trackKey: String?, val title: String, val artist: String, val album: String? = null, val durationMs: Long? = null)

@kotlinx.serialization.Serializable
data class BackupLike(val trackKey: String, val likedAt: Long)

@kotlinx.serialization.Serializable
data class BackupHistory(val trackKey: String, val playedAt: Long, val listenedMs: Long)
