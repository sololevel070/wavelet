package app.wavelet.source.impl

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import app.wavelet.download.DownloadRepository
import app.wavelet.source.MusicSource
import app.wavelet.source.Stream
import app.wavelet.source.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Music files indexed by Android's MediaStore. */
class LocalSource(
    override val id: String,
    override val name: String,
    private val context: Context,
) : MusicSource {
    override val showInDiscover = false

    fun hasPermission(): Boolean {
        val perm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE
        return context.checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED
    }

    override suspend fun search(query: String, limit: Int): List<Track> {
        val like = "%$query%"
        return query(
            "(${MediaStore.Audio.Media.TITLE} LIKE ? OR ${MediaStore.Audio.Media.ARTIST} LIKE ? OR ${MediaStore.Audio.Media.ALBUM} LIKE ?)",
            arrayOf(like, like, like),
            limit,
        )
    }

    /** Every song on the device, used by the Library tab. */
    override suspend fun featured(): List<Track> = query(null, null, 5000)

    override suspend fun resolveStream(track: Track): Stream =
        Stream(track.streamUrl ?: contentUri(track.id.toLong()))

    private fun contentUri(mediaId: Long) =
        ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, mediaId).toString()

    private suspend fun query(selection: String?, args: Array<String>?, limit: Int): List<Track> =
        withContext(Dispatchers.IO) {
            if (!hasPermission()) return@withContext emptyList()
            val base = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
            val where = if (selection == null) base else "$base AND $selection"
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.ALBUM_ID,
            )
            val out = mutableListOf<Track>()
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, where, args,
                "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC",
            )?.use { c ->
                while (c.moveToNext() && out.size < limit) {
                    val mediaId = c.getLong(0)
                    out += Track(
                        id = mediaId.toString(),
                        sourceId = id,
                        title = c.getString(1) ?: "Unknown title",
                        artist = c.getString(2)?.takeUnless { it == MediaStore.UNKNOWN_STRING } ?: "Unknown artist",
                        album = c.getString(3),
                        durationMs = c.getLong(4),
                        artworkUrl = "content://media/external/audio/albumart/${c.getLong(5)}",
                        streamUrl = contentUri(mediaId),
                    )
                }
            }
            out
        }
}

/** Tracks saved for offline use by [DownloadRepository]. */
class DownloadsSource(
    override val id: String,
    override val name: String,
    private val downloads: DownloadRepository,
) : MusicSource {
    override val showInDiscover = false

    override suspend fun search(query: String, limit: Int): List<Track> {
        val q = query.lowercase()
        return downloads.items.value.filter {
            it.title.lowercase().contains(q) || it.artist.lowercase().contains(q) ||
                (it.album?.lowercase()?.contains(q) == true)
        }.take(limit)
    }

    override suspend fun featured(): List<Track> = downloads.items.value

    override suspend fun resolveStream(track: Track): Stream =
        Stream(track.streamUrl ?: error("Downloaded file is missing"))
}
