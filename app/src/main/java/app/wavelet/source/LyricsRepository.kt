package app.wavelet.source

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Loads lyrics from the enabled lyrics sources, with a small in-memory cache. Shared by the
 * Now Playing screen and the floating lyrics overlay so each song is fetched once.
 */
class LyricsRepository(private val sources: () -> List<LyricsSource>) {
    private val cache = object : LinkedHashMap<String, Lyrics?>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Lyrics?>?) = size > 60
    }
    private val lock = Mutex()

    private fun cacheKey(t: Track) = "${t.artist.lowercase()}|${t.title.lowercase()}"

    suspend fun get(track: Track): Lyrics? {
        if (track.isLive || track.title.isBlank()) return null
        val key = cacheKey(track)
        lock.withLock { if (cache.containsKey(key)) return cache[key] }
        var found: Lyrics? = null
        for (source in sources()) {
            found = attempt { source.lyrics(track) }.getOrNull()
            if (found != null) break
        }
        lock.withLock { cache[key] = found }
        return found
    }
}

/** Index of the synced line that should be showing at [positionMs], or -1. */
fun List<LyricLine>.indexAt(positionMs: Long): Int = indexOfLast { it.timeMs <= positionMs + 300 }
