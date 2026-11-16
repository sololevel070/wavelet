package app.wavelet.source.impl

import app.wavelet.net.Http
import app.wavelet.net.arr
import app.wavelet.net.at
import app.wavelet.net.long
import app.wavelet.net.str
import app.wavelet.source.MusicSource
import app.wavelet.source.Stream
import app.wavelet.source.Track
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.IOException

/**
 * Music on your own Plex Media Server. Needs the server URL and an X-Plex-Token
 * (see "Finding an authentication token" in Plex's support pages).
 */
class PlexSource(
    override val id: String,
    override val name: String,
    baseUrl: String,
    private val token: String,
) : MusicSource {
    private val base = baseUrl.trim().trimEnd('/')
    private val json = mapOf("Accept" to "application/json")

    private fun url(path: String, vararg params: Pair<String, String>): HttpUrl =
        "$base$path".toHttpUrl().newBuilder()
            .apply { params.forEach { (k, v) -> addQueryParameter(k, v) } }
            .addQueryParameter("X-Plex-Token", token)
            .build()

    private suspend fun get(path: String, vararg params: Pair<String, String>): JsonElement =
        Http.get(url(path, *params), json).at("MediaContainer") ?: throw IOException("Not a Plex server")

    private fun parse(items: List<JsonElement>): List<Track> = items.mapNotNull { m ->
        if (m.str("type") != "track") return@mapNotNull null
        val key = m.str("ratingKey") ?: return@mapNotNull null
        val part = m.at("Media").arr.firstOrNull().at("Part").arr.firstOrNull()
        val partKey = part.str("key") ?: return@mapNotNull null
        val thumb = m.str("thumb") ?: m.str("parentThumb") ?: m.str("grandparentThumb")
        Track(
            id = key,
            sourceId = id,
            title = m.str("title") ?: "Untitled",
            artist = m.str("originalTitle") ?: m.str("grandparentTitle") ?: "Unknown artist",
            album = m.str("parentTitle"),
            durationMs = m.long("duration"),
            artworkUrl = thumb?.let { url("/photo/:/transcode", "url" to it, "width" to "300", "height" to "300").toString() },
            streamUrl = url(partKey).toString(),
            downloadable = true,
        )
    }

    override suspend fun search(query: String, limit: Int): List<Track> =
        parse(get("/search", "type" to "10", "query" to query, "limit" to limit.toString()).at("Metadata").arr)

    override suspend fun featured(): List<Track> {
        val sections = get("/library/sections").at("Directory").arr.filter { it.str("type") == "artist" }
        val first = sections.firstOrNull()?.str("key") ?: return emptyList()
        return parse(
            get(
                "/library/sections/$first/all", "type" to "10", "sort" to "random",
                "X-Plex-Container-Start" to "0", "X-Plex-Container-Size" to "40",
            ).at("Metadata").arr,
        )
    }

    override suspend fun resolveStream(track: Track): Stream = Stream(track.streamUrl ?: error("No file for this track"))

    override suspend fun resolveDownload(track: Track): Stream = resolveStream(track)
}
