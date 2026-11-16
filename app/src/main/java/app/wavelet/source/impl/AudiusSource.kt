package app.wavelet.source.impl

import app.wavelet.net.Http
import app.wavelet.net.arr
import app.wavelet.net.at
import app.wavelet.net.long
import app.wavelet.net.str
import app.wavelet.source.MusicSource
import app.wavelet.source.Stream
import app.wavelet.source.Track
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * Audius (audius.co): music uploaded by artists, streamed through the open Audius API.
 * The API asks clients to pick a discovery node from api.audius.co and pass an app_name.
 */
class AudiusSource(
    override val id: String,
    override val name: String,
) : MusicSource {
    private val lock = Mutex()
    private var host: String? = null

    private suspend fun host(): String = lock.withLock {
        host ?: run {
            val nodes = Http.get("https://api.audius.co".toHttpUrl()).at("data").arr
                .mapNotNull { (it as? JsonPrimitive)?.content }
            (nodes.randomOrNull() ?: "https://discoveryprovider.audius.co").trimEnd('/').also { host = it }
        }
    }

    private suspend fun api(path: String, vararg params: Pair<String, String>): JsonElement {
        val url = "${host()}/v1$path".toHttpUrl().newBuilder()
            .apply { params.forEach { (k, v) -> addQueryParameter(k, v) } }
            .addQueryParameter("app_name", APP_NAME)
            .build()
        return Http.get(url)
    }

    private suspend fun parse(json: JsonElement): List<Track> {
        val h = host()
        return json.at("data").arr.mapNotNull { t ->
            val trackId = t.str("id") ?: return@mapNotNull null
            if (t.str("is_streamable") == "false") return@mapNotNull null
            Track(
                id = trackId,
                sourceId = id,
                title = t.str("title") ?: "Untitled",
                artist = t.at("user").str("name") ?: "Unknown artist",
                album = null,
                durationMs = t.long("duration")?.times(1000),
                artworkUrl = t.at("artwork").str("480x480") ?: t.at("artwork").str("150x150"),
                streamUrl = "$h/v1/tracks/$trackId/stream?app_name=$APP_NAME",
                downloadable = t.str("is_downloadable") == "true",
            )
        }
    }

    override suspend fun search(query: String, limit: Int): List<Track> =
        parse(api("/tracks/search", "query" to query, "limit" to limit.toString())).take(limit)

    override suspend fun featured(): List<Track> = parse(api("/tracks/trending", "time" to "week")).take(30)

    override suspend fun resolveStream(track: Track): Stream =
        Stream("${host()}/v1/tracks/${track.id}/stream?app_name=$APP_NAME")

    /** Only offered when the artist allows downloads. */
    override suspend fun resolveDownload(track: Track): Stream? =
        if (track.downloadable) Stream("${host()}/v1/tracks/${track.id}/download?app_name=$APP_NAME") else null

    private companion object {
        const val APP_NAME = "Wavelet"
    }
}
