package app.wavelet.source.impl

import app.wavelet.net.Http
import app.wavelet.net.arr
import app.wavelet.net.at
import app.wavelet.net.str
import app.wavelet.source.MusicSource
import app.wavelet.source.Stream
import app.wavelet.source.Track
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * Internet Archive (archive.org) audio items. Each search hit is one item (an album or a
 * concert); playing it plays the item's first MP3/OGG file.
 */
class InternetArchiveSource(
    override val id: String,
    override val name: String,
) : MusicSource {

    private suspend fun advancedSearch(q: String, rows: Int, sort: String?): List<Track> {
        val url = "https://archive.org/advancedsearch.php".toHttpUrl().newBuilder()
            .addQueryParameter("q", q)
            .addQueryParameter("fl[]", "identifier")
            .addQueryParameter("fl[]", "title")
            .addQueryParameter("fl[]", "creator")
            .addQueryParameter("rows", rows.toString())
            .addQueryParameter("output", "json")
            .apply { if (sort != null) addQueryParameter("sort[]", sort) }
            .build()
        return Http.get(url).at("response").at("docs").arr.mapNotNull { d ->
            val ident = d.str("identifier") ?: return@mapNotNull null
            Track(
                id = ident,
                sourceId = id,
                title = d.str("title") ?: ident,
                artist = d.str("creator") ?: "Internet Archive",
                artworkUrl = "https://archive.org/services/img/$ident",
                downloadable = true,
            )
        }
    }

    // Music collections only (skips radio/TV captures), and only items with streamable files.
    private val musicFilter = "mediatype:(audio) AND " +
        "collection:(etree OR netlabels OR audio_music OR opensource_audio OR georgeblood) AND " +
        "format:(\"VBR MP3\" OR MP3 OR \"Ogg Vorbis\")"

    override suspend fun search(query: String, limit: Int): List<Track> {
        val cleaned = query.replace(Regex("""["():]"""), " ").trim()
        return advancedSearch("($cleaned) AND $musicFilter", limit, null)
    }

    override suspend fun featured(): List<Track> =
        advancedSearch("collection:(etree OR netlabels) AND $musicFilter", 30, "downloads desc")

    override suspend fun resolveStream(track: Track): Stream {
        val meta = Http.get("https://archive.org/metadata/${track.id}".toHttpUrl())
        val files = meta.at("files").arr
        val preferred = listOf("VBR MP3", "MP3", "128Kbps MP3", "64Kbps MP3", "Ogg Vorbis")
        val file = preferred.firstNotNullOfOrNull { fmt ->
            files.firstOrNull { it.str("format") == fmt }
        } ?: error("This item has no playable audio file")
        val fileName = file.str("name") ?: error("This item has no playable audio file")
        val url = "https://archive.org/download".toHttpUrl().newBuilder()
            .addPathSegment(track.id)
            .addPathSegments(fileName)
            .build()
        return Stream(url.toString())
    }

    override suspend fun resolveDownload(track: Track): Stream = resolveStream(track)
}
