package app.wavelet.source.impl

import android.content.Context
import app.wavelet.source.MusicSource
import app.wavelet.source.Stream
import app.wavelet.source.Track
import app.wavelet.ytdlp.YtDlp

/**
 * Music source powered by yt-dlp (bundled). Searches YouTube and resolves direct audio streams,
 * allowing playback and downloads of songs by name or URL.
 */
class YtDlpSource(
    override val id: String,
    override val name: String,
    private val appContext: Context,
) : MusicSource {

    override val showInDiscover: Boolean get() = true

    private suspend fun ready() { YtDlp.ensureInitialized(appContext) }

    override suspend fun search(query: String, limit: Int): List<Track> {
        val q = query.trim()
        if (q.isBlank()) return emptyList()
        ready()

        // If a URL was pasted, resolve it directly as a single audio track.
        if (YtDlp.isUrl(q)) {
            val entry = YtDlp.getUrlInfo(q) ?: return emptyList()
            return listOf(entry.toTrack())
        }

        return YtDlp.search(q, limit).map { it.toTrack() }
    }

    override suspend fun featured(): List<Track> {
        ready()
        // Use trending-ish search seeds; keep lightweight.
        return YtDlp.search("popular music", 20).map { it.toTrack() }
    }

    override suspend fun resolveStream(track: Track): Stream {
        ready()
        val url = YtDlp.resolveAudioUrl(track.id)
            ?: YtDlp.resolveAudioUrl("https://www.youtube.com/watch?v=${track.id}")
            ?: throw java.io.IOException("Could not resolve audio stream for ${track.title}")
        return Stream(url)
    }

    override suspend fun resolveDownload(track: Track): Stream =
        // Hand the raw video id to the download repository, which lets yt-dlp do the transfer.
        Stream("${YtDlp.DOWNLOAD_PREFIX}${track.id}")

    private fun YtDlp.Entry.toTrack(): Track = Track(
        id = id,
        sourceId = this@YtDlpSource.id,
        title = title,
        artist = artist,
        durationMs = durationSec?.let { it * 1000 },
        artworkUrl = thumbnail,
        downloadable = true,
    )
}