package app.wavelet.source

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable

@Serializable
data class Track(
    /** Identifier unique within its source. */
    val id: String,
    /** Instance id of the source that produced this track. */
    val sourceId: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val durationMs: Long? = null,
    val artworkUrl: String? = null,
    /** Playable URL when the source knows it up front. */
    val streamUrl: String? = null,
    val downloadable: Boolean = false,
    /** Live streams (radio) have no duration and cannot be downloaded. */
    val isLive: Boolean = false,
) {
    val key: String get() = "$sourceId:$id"
}

data class Stream(val url: String)

data class LyricLine(val timeMs: Long, val text: String)

data class Lyrics(val plain: String?, val synced: List<LyricLine>) {
    val isEmpty: Boolean get() = plain.isNullOrBlank() && synced.isEmpty()
}

/** Base contract every source implements. */
interface Source {
    /** Instance id from [app.wavelet.data.SourceConfig.instanceId]. */
    val id: String
    val name: String
}

/** A source of playable music. */
interface MusicSource : Source {
    /** False for library sources (device files, downloads) that Discover skips by default. */
    val showInDiscover: Boolean get() = true

    suspend fun search(query: String, limit: Int = 30): List<Track>

    /** Suggestions shown when the search box is empty. */
    suspend fun featured(): List<Track> = emptyList()

    suspend fun resolveStream(track: Track): Stream

    /** URL to save for offline use, or null when the source doesn't permit downloading. */
    suspend fun resolveDownload(track: Track): Stream? = null
}

interface LyricsSource : Source {
    suspend fun lyrics(track: Track): Lyrics?
}

/** Finds the same song on other services. Returns service name to URL. */
interface LinkSource : Source {
    suspend fun links(track: Track): Map<String, String>
}

/** Like runCatching, but never swallows coroutine cancellation. */
suspend fun <T> attempt(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}
