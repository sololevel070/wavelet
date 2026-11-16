package app.wavelet.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import app.wavelet.source.Track
import kotlinx.coroutines.flow.Flow

/** Snapshot of a track's details, so playlists, likes and history can show it later. */
@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey val key: String,
    val id: String,
    val sourceId: String,
    val title: String,
    val artist: String,
    val album: String?,
    val durationMs: Long?,
    val artworkUrl: String?,
    val streamUrl: String?,
    val downloadable: Boolean,
    val isLive: Boolean,
) {
    fun toTrack() = Track(id, sourceId, title, artist, album, durationMs, artworkUrl, streamUrl, downloadable, isLive)

    companion object {
        fun of(t: Track) = TrackEntity(
            t.key, t.id, t.sourceId, t.title, t.artist, t.album, t.durationMs,
            t.artworkUrl, t.streamUrl, t.downloadable, t.isLive,
        )
    }
}

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

/**
 * One row of a playlist. [trackKey] is null when an imported song hasn't been matched to a
 * source yet; the title/artist are kept so the user can match it later.
 */
@Entity(
    tableName = "playlist_items",
    indices = [Index("playlistId"), Index("trackKey")],
)
data class PlaylistItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val position: Int,
    val trackKey: String?,
    val title: String,
    val artist: String,
    val album: String? = null,
    val durationMs: Long? = null,
    val addedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "likes")
data class LikeEntity(
    @PrimaryKey val trackKey: String,
    val likedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "history", indices = [Index("playedAt"), Index("trackKey")])
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackKey: String,
    val playedAt: Long,
    val listenedMs: Long,
)

/** A playlist row joined with its (optional) cached track. */
data class PlaylistItemWithTrack(
    val itemId: Long,
    val position: Int,
    val trackKey: String?,
    val itemTitle: String,
    val itemArtist: String,
    val itemAlbum: String?,
    val itemDurationMs: Long?,
    val id: String?,
    val sourceId: String?,
    val title: String?,
    val artist: String?,
    val album: String?,
    val durationMs: Long?,
    val artworkUrl: String?,
    val streamUrl: String?,
    val downloadable: Boolean?,
    val isLive: Boolean?,
) {
    val track: Track?
        get() = if (trackKey != null && id != null && sourceId != null) Track(
            id, sourceId, title ?: itemTitle, artist ?: itemArtist, album, durationMs,
            artworkUrl, streamUrl, downloadable ?: false, isLive ?: false,
        ) else null
}

data class PlaylistSummary(
    val id: Long,
    val name: String,
    val description: String,
    val updatedAt: Long,
    val count: Int,
    val covers: String?,
) {
    /** Up to four artwork URLs for a mosaic cover. */
    val coverUrls: List<String> get() = covers?.split('\n')?.filter { it.isNotBlank() }?.distinct()?.take(4).orEmpty()
}

data class PlayCount(
    val trackKey: String,
    val plays: Int,
    val listenedMs: Long,
    val title: String,
    val artist: String,
    val artworkUrl: String?,
)

data class ArtistCount(val artist: String, val plays: Int, val listenedMs: Long, val artworkUrl: String?)

data class HistoryRow(
    val historyId: Long,
    val playedAt: Long,
    val listenedMs: Long,
    val key: String,
    val id: String,
    val sourceId: String,
    val title: String,
    val artist: String,
    val album: String?,
    val durationMs: Long?,
    val artworkUrl: String?,
    val streamUrl: String?,
    val downloadable: Boolean,
    val isLive: Boolean,
) {
    val track get() = Track(id, sourceId, title, artist, album, durationMs, artworkUrl, streamUrl, downloadable, isLive)
}

@Dao
interface LibraryDao {
    // ---- tracks
    @Upsert suspend fun upsertTrack(track: TrackEntity)
    @Upsert suspend fun upsertTracks(tracks: List<TrackEntity>)
    @Query("SELECT * FROM tracks WHERE `key` = :key") suspend fun track(key: String): TrackEntity?

    // ---- playlists
    @Insert suspend fun insertPlaylist(p: PlaylistEntity): Long
    @Query("UPDATE playlists SET name = :name, description = :description, updatedAt = :now WHERE id = :id")
    suspend fun renamePlaylist(id: Long, name: String, description: String, now: Long = System.currentTimeMillis())
    @Query("UPDATE playlists SET updatedAt = :now WHERE id = :id")
    suspend fun touchPlaylist(id: Long, now: Long = System.currentTimeMillis())
    @Query("DELETE FROM playlists WHERE id = :id") suspend fun deletePlaylistRow(id: Long)
    @Query("DELETE FROM playlist_items WHERE playlistId = :id") suspend fun clearPlaylist(id: Long)
    @Query("SELECT * FROM playlists WHERE id = :id") fun playlist(id: Long): Flow<PlaylistEntity?>
    @Query("SELECT * FROM playlists WHERE id = :id") suspend fun playlistNow(id: Long): PlaylistEntity?
    @Query("SELECT * FROM playlists ORDER BY updatedAt DESC") suspend fun allPlaylists(): List<PlaylistEntity>

    @Query(
        """SELECT p.id, p.name, p.description, p.updatedAt,
           (SELECT COUNT(*) FROM playlist_items i WHERE i.playlistId = p.id) AS count,
           (SELECT GROUP_CONCAT(t.artworkUrl, char(10)) FROM
               (SELECT t2.artworkUrl FROM playlist_items i2 JOIN tracks t2 ON t2.`key` = i2.trackKey
                WHERE i2.playlistId = p.id AND t2.artworkUrl IS NOT NULL ORDER BY i2.position LIMIT 12) t) AS covers
           FROM playlists p ORDER BY p.updatedAt DESC""",
    )
    fun playlistSummaries(): Flow<List<PlaylistSummary>>

    @Query(
        """SELECT i.id AS itemId, i.position, i.trackKey, i.title AS itemTitle, i.artist AS itemArtist,
           i.album AS itemAlbum, i.durationMs AS itemDurationMs,
           t.id, t.sourceId, t.title, t.artist, t.album, t.durationMs, t.artworkUrl, t.streamUrl, t.downloadable, t.isLive
           FROM playlist_items i LEFT JOIN tracks t ON t.`key` = i.trackKey
           WHERE i.playlistId = :id ORDER BY i.position""",
    )
    fun playlistItems(id: Long): Flow<List<PlaylistItemWithTrack>>

    @Query(
        """SELECT i.id AS itemId, i.position, i.trackKey, i.title AS itemTitle, i.artist AS itemArtist,
           i.album AS itemAlbum, i.durationMs AS itemDurationMs,
           t.id, t.sourceId, t.title, t.artist, t.album, t.durationMs, t.artworkUrl, t.streamUrl, t.downloadable, t.isLive
           FROM playlist_items i LEFT JOIN tracks t ON t.`key` = i.trackKey
           WHERE i.playlistId = :id ORDER BY i.position""",
    )
    suspend fun playlistItemsNow(id: Long): List<PlaylistItemWithTrack>

    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_items WHERE playlistId = :id")
    suspend fun maxPosition(id: Long): Int
    @Insert suspend fun insertItems(items: List<PlaylistItemEntity>)
    @Query("DELETE FROM playlist_items WHERE id = :itemId") suspend fun deleteItem(itemId: Long)
    @Query("UPDATE playlist_items SET position = :position WHERE id = :itemId")
    suspend fun setPosition(itemId: Long, position: Int)
    @Query("UPDATE playlist_items SET trackKey = :trackKey WHERE id = :itemId")
    suspend fun setItemTrack(itemId: Long, trackKey: String?)
    @Query("SELECT COUNT(*) FROM playlist_items WHERE playlistId = :id AND trackKey = :trackKey")
    suspend fun countInPlaylist(id: Long, trackKey: String): Int

    @Transaction
    suspend fun deletePlaylist(id: Long) {
        clearPlaylist(id)
        deletePlaylistRow(id)
    }

    // ---- likes
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun like(like: LikeEntity)
    @Query("DELETE FROM likes WHERE trackKey = :key") suspend fun unlike(key: String)
    @Query("SELECT trackKey FROM likes") fun likedKeys(): Flow<List<String>>
    @Query("SELECT t.* FROM likes l JOIN tracks t ON t.`key` = l.trackKey ORDER BY l.likedAt DESC")
    fun likedTracks(): Flow<List<TrackEntity>>
    @Query("SELECT * FROM likes") suspend fun allLikes(): List<LikeEntity>

    // ---- history
    @Insert suspend fun insertHistory(h: HistoryEntity)
    @Query("DELETE FROM history") suspend fun clearHistory()
    @Query("SELECT * FROM history") suspend fun allHistory(): List<HistoryEntity>

    @Query(
        """SELECT h.id AS historyId, h.playedAt, h.listenedMs, t.* FROM history h
           JOIN tracks t ON t.`key` = h.trackKey ORDER BY h.playedAt DESC LIMIT :limit""",
    )
    fun recentHistory(limit: Int): Flow<List<HistoryRow>>

    /** Most recently played distinct tracks. */
    @Query(
        """SELECT t.* FROM tracks t JOIN
           (SELECT trackKey, MAX(playedAt) AS last FROM history GROUP BY trackKey) h ON h.trackKey = t.`key`
           ORDER BY h.last DESC LIMIT :limit""",
    )
    fun recentTracks(limit: Int): Flow<List<TrackEntity>>

    @Query(
        """SELECT h.trackKey, COUNT(*) AS plays, SUM(h.listenedMs) AS listenedMs, t.title, t.artist, t.artworkUrl
           FROM history h JOIN tracks t ON t.`key` = h.trackKey
           WHERE h.playedAt >= :from AND h.playedAt < :to
           GROUP BY h.trackKey ORDER BY plays DESC, listenedMs DESC LIMIT :limit""",
    )
    suspend fun topTracks(from: Long, to: Long, limit: Int): List<PlayCount>

    @Query(
        """SELECT t.artist AS artist, COUNT(*) AS plays, SUM(h.listenedMs) AS listenedMs, MAX(t.artworkUrl) AS artworkUrl
           FROM history h JOIN tracks t ON t.`key` = h.trackKey
           WHERE h.playedAt >= :from AND h.playedAt < :to AND t.isLive = 0
           GROUP BY t.artist ORDER BY plays DESC LIMIT :limit""",
    )
    suspend fun topArtists(from: Long, to: Long, limit: Int): List<ArtistCount>

    @Query("SELECT COALESCE(SUM(listenedMs), 0) FROM history WHERE playedAt >= :from AND playedAt < :to")
    suspend fun listenedBetween(from: Long, to: Long): Long

    @Query("SELECT COUNT(*) FROM history WHERE playedAt >= :from AND playedAt < :to")
    suspend fun playsBetween(from: Long, to: Long): Int

    @Query("SELECT * FROM tracks") suspend fun allTracks(): List<TrackEntity>
    @Query("SELECT * FROM playlist_items") suspend fun allItems(): List<PlaylistItemEntity>
}

@Database(
    entities = [TrackEntity::class, PlaylistEntity::class, PlaylistItemEntity::class, LikeEntity::class, HistoryEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class WaveletDatabase : RoomDatabase() {
    abstract fun dao(): LibraryDao

    companion object {
        fun create(context: Context): WaveletDatabase =
            Room.databaseBuilder(context, WaveletDatabase::class.java, "wavelet.db").build()
    }
}
