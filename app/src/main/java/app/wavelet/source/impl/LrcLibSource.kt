package app.wavelet.source.impl

import app.wavelet.net.Http
import app.wavelet.net.HttpException
import app.wavelet.net.arr
import app.wavelet.net.str
import app.wavelet.source.LyricLine
import app.wavelet.source.Lyrics
import app.wavelet.source.LyricsSource
import app.wavelet.source.Track
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl

/** Lyrics from LRCLIB (https://lrclib.net), a free, open lyrics database. */
class LrcLibSource(
    override val id: String,
    override val name: String,
) : LyricsSource {

    override suspend fun lyrics(track: Track): Lyrics? {
        if (track.isLive) return null
        val exact = "https://lrclib.net/api/get".toHttpUrl().newBuilder()
            .addQueryParameter("artist_name", track.artist)
            .addQueryParameter("track_name", track.title)
            .apply {
                track.album?.let { addQueryParameter("album_name", it) }
                track.durationMs?.let { addQueryParameter("duration", (it / 1000).toString()) }
            }
            .build()
        val hit: JsonElement? = try {
            Http.get(exact)
        } catch (e: HttpException) {
            if (e.code != 404) throw e
            val search = "https://lrclib.net/api/search".toHttpUrl().newBuilder()
                .addQueryParameter("track_name", track.title)
                .addQueryParameter("artist_name", track.artist)
                .build()
            Http.get(search).arr.firstOrNull()
        }
        hit ?: return null
        val lyrics = Lyrics(
            plain = hit.str("plainLyrics"),
            synced = hit.str("syncedLyrics")?.let(::parseLrc).orEmpty(),
        )
        return lyrics.takeUnless { it.isEmpty }
    }
}

private val timeTag = Regex("""\[(\d{1,3}):(\d{1,2}(?:[.:]\d{1,3})?)]""")

/** Parses LRC text ("[01:23.45] line"), including lines with several time tags. */
fun parseLrc(text: String): List<LyricLine> = text.lineSequence().flatMap { line ->
    val tags = timeTag.findAll(line).toList()
    if (tags.isEmpty()) return@flatMap emptySequence()
    val content = line.substring(tags.last().range.last + 1).trim()
    tags.asSequence().map { m ->
        val minutes = m.groupValues[1].toLong()
        val seconds = m.groupValues[2].replace(':', '.').toDouble()
        LyricLine(minutes * 60_000 + (seconds * 1000).toLong(), content)
    }
}.sortedBy { it.timeMs }.toList()
