package app.wavelet.source.impl

import app.wavelet.net.Http
import app.wavelet.net.arr
import app.wavelet.net.str
import app.wavelet.source.MusicSource
import app.wavelet.source.Stream
import app.wavelet.source.Track
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl

/** Internet radio from the community-run Radio Browser directory (api.radio-browser.info). */
class RadioBrowserSource(
    override val id: String,
    override val name: String,
) : MusicSource {
    private val base = "https://de1.api.radio-browser.info/json"

    override suspend fun search(query: String, limit: Int): List<Track> {
        val url = "$base/stations/search".toHttpUrl().newBuilder()
            .addQueryParameter("name", query)
            .addQueryParameter("limit", limit.toString())
            .addQueryParameter("hidebroken", "true")
            .addQueryParameter("order", "clickcount")
            .addQueryParameter("reverse", "true")
            .build()
        return parse(Http.get(url))
    }

    override suspend fun featured(): List<Track> =
        parse(Http.get("$base/stations/topclick/30".toHttpUrl()))

    private fun parse(json: JsonElement): List<Track> = json.arr.mapNotNull { s ->
        val uuid = s.str("stationuuid") ?: return@mapNotNull null
        val stream = s.str("url_resolved") ?: s.str("url") ?: return@mapNotNull null
        val tags = s.str("tags")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.take(3)
        val subtitle = listOfNotNull(s.str("country"), tags?.joinToString(", ")?.ifBlank { null })
            .joinToString(" · ").ifBlank { "Internet radio" }
        Track(
            id = uuid,
            sourceId = id,
            title = s.str("name")?.trim() ?: "Unnamed station",
            artist = subtitle,
            artworkUrl = s.str("favicon"),
            streamUrl = stream,
            isLive = true,
        )
    }

    override suspend fun resolveStream(track: Track): Stream =
        Stream(track.streamUrl ?: error("Station has no stream URL"))
}
