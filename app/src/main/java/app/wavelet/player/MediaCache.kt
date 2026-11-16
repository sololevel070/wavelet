package app.wavelet.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

/**
 * Disk cache for streamed songs. Songs are keyed by their Wavelet track key (not the URL, which
 * can carry one-off login tokens), so a song played once replays from the phone.
 * The size is read once per app start; changing it in Settings applies after a restart.
 */
@OptIn(UnstableApi::class)
class MediaCache(private val context: Context, sizeMb: Int) {
    val enabled = sizeMb > 0
    private val dir = File(context.cacheDir, "media")

    val cache: SimpleCache? by lazy {
        if (!enabled) null
        else SimpleCache(dir, LeastRecentlyUsedCacheEvictor(sizeMb * 1024L * 1024L), StandaloneDatabaseProvider(context))
    }

    /** Bytes currently used. */
    fun usedBytes(): Long = if (enabled) cache?.cacheSpace ?: 0 else dir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }

    /** Removes every cached song. */
    fun clear() {
        val c = cache
        if (c != null) {
            c.keys.toList().forEach { key -> runCatching { c.removeResource(key) } }
        } else {
            runCatching { dir.deleteRecursively() }
        }
    }

    /** True when the whole song is cached. */
    fun isCached(key: String): Boolean {
        val c = cache ?: return false
        val length = androidx.media3.datasource.cache.ContentMetadata.getContentLength(c.getContentMetadata(key))
        return length > 0 && c.isCached(key, 0, length)
    }
}
