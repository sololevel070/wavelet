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

/** Jamendo API v3 (https://developer.jamendo.com). Creative Commons music, free client ID. */
class JamendoSource(
    override val id: String,
    override val name: String,
    private val clientId: String,
) : MusicSource {

    private fun tracksUrl(extra: HttpUrl.Builder.() -> Unit): HttpUrl =
        "https://api.jamendo.com/v3.0/tracks/".toHttpUrl().newBuilder()
            .addQueryParameter("client_id", clientId)
            .addQueryParameter("format", "json")
            .addQueryParameter("audioformat", "mp32")
            .apply(extra)
            .build()

    override suspend fun search(query: String, limit: Int): List<Track> = parse(Http.get(tracksUrl {
        addQueryParameter("search", query)
        addQueryParameter("limit", limit.toString())
    }))

    override suspend fun featured(): List<Track> = parse(Http.get(tracksUrl {
        addQueryParameter("order", "popularity_week")
        addQueryParameter("limit", "30")
    }))

    // Jamendo marks per track whether the artist allows downloads.
    private val downloadUrls = mutableMapOf<String, String>()

    private fun parse(json: JsonElement): List<Track> {
        val header = json.at("headers")
        if (header.str("status") == "failed") error(header.str("error_message") ?: "Jamendo request failed")
        return json.at("results").arr.mapNotNull { r ->
            val trackId = r.str("id") ?: return@mapNotNull null
            val audio = r.str("audio") ?: return@mapNotNull null
            val download = r.str("audiodownload")?.takeIf { r.str("audiodownload_allowed") == "true" }
            if (download != null) synchronized(downloadUrls) { downloadUrls[trackId] = download }
            Track(
                id = trackId,
                sourceId = id,
                title = r.str("name") ?: "Untitled",
                artist = r.str("artist_name") ?: "Unknown artist",
                album = r.str("album_name"),
                durationMs = r.long("duration")?.times(1000),
                artworkUrl = r.str("image"),
                streamUrl = audio,
                downloadable = download != null,
            )
        }
    }

    override suspend fun resolveStream(track: Track): Stream =
        Stream(track.streamUrl ?: error("No audio for this track"))

    override suspend fun resolveDownload(track: Track): Stream? =
        synchronized(downloadUrls) { downloadUrls[track.id] }?.let(::Stream)
}
