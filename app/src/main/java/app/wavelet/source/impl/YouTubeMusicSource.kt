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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * YouTube Music source powered by YouTube Music InnerTube and Piped API fallback.
 * Allows searching, playing, and downloading any song or artist.
 */
class YouTubeMusicSource(
    override val id: String,
    override val name: String,
    private val pipedBaseUrl: String = "https://pipedapi.kavin.rocks",
) : MusicSource {

    override val showInDiscover: Boolean get() = true

    override suspend fun search(query: String, limit: Int): List<Track> {
        val q = query.trim()
        if (q.isBlank()) return emptyList()

        // 1. Try InnerTube web API
        runCatching { searchInnerTube(q, limit) }
            .getOrNull()?.takeIf { it.isNotEmpty() }?.let { return it }

        // 2. Fallback to Piped API
        return runCatching { searchPiped(q, limit) }.getOrDefault(emptyList())
    }

    private suspend fun searchInnerTube(query: String, limit: Int): List<Track> {
        val body = buildJsonObject {
            putJsonObject("context") {
                putJsonObject("client") {
                    put("clientName", "WEB_REMIX")
                    put("clientVersion", "1.20240801.01.00")
                    put("hl", "en")
                }
            }
            put("query", query)
            put("params", "Eg-KAQwIARAAGAAgACgAMABqChAEEAMQCRAFEAo%3D") // songs filter
        }

        val req = Request.Builder()
            .url("https://music.youtube.com/youtubei/v1/search?prettyPrint=false")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .header("Referer", "https://music.youtube.com/")
            .build()

        val json = Http.request(req)
        val tracks = mutableListOf<Track>()

        fun extractTracks(element: JsonElement) {
            when (element) {
                is JsonObject -> {
                    if (element.containsKey("musicResponsiveListItemRenderer")) {
                        parseInnerTubeItem(element.at("musicResponsiveListItemRenderer"))?.let { tracks.add(it) }
                    } else {
                        element.values.forEach { extractTracks(it) }
                    }
                }
                else -> Unit
            }
        }

        extractTracks(json)
        return tracks.distinctBy { it.id }.take(limit)
    }

    private fun parseInnerTubeItem(item: JsonElement?): Track? {
        if (item == null) return null
        val flexColumns = item.at("flexColumns").arr
        if (flexColumns.isEmpty()) return null

        val titleRuns = flexColumns.firstOrNull()?.at("musicResponsiveListItemFlexColumnRenderer")
            ?.at("text")?.at("runs").arr
        val title = titleRuns.firstOrNull().str("text") ?: return null

        var videoId = titleRuns.firstOrNull()?.at("navigationEndpoint")
            ?.at("watchEndpoint").str("videoId")
            ?: item.at("playlistItemData").str("videoId")

        val subRuns = flexColumns.getOrNull(1)?.at("musicResponsiveListItemFlexColumnRenderer")
            ?.at("text")?.at("runs").arr
        val artist = subRuns.firstOrNull().str("text") ?: "YouTube Music"
        val album = subRuns.getOrNull(2).str("text")

        if (videoId == null) {
            val endpoint = item.at("doubleTapEndpoint")?.at("watchEndpoint")
            videoId = endpoint.str("videoId")
        }
        val id = videoId ?: return null

        val thumb = item.at("thumbnail")?.at("musicThumbnailRenderer")
            ?.at("thumbnail")?.at("thumbnails").arr.lastOrNull().str("url")
            ?: "https://i.ytimg.com/vi/$id/hqdefault.jpg"

        return Track(
            id = id,
            sourceId = this.id,
            title = title,
            artist = artist,
            album = album,
            artworkUrl = thumb,
            downloadable = true,
        )
    }

    private suspend fun searchPiped(query: String, limit: Int): List<Track> {
        val url = "$pipedBaseUrl/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("filter", "music_songs")
            .build()
        val json = Http.get(url)
        return json.at("items").arr.mapNotNull { item ->
            val vUrl = item.str("url") ?: return@mapNotNull null
            val id = vUrl.substringAfter("watch?v=", "").takeIf { it.isNotEmpty() }
                ?: vUrl.substringAfterLast("/")
            val title = item.str("title") ?: return@mapNotNull null
            val artist = item.str("uploaderName") ?: "Unknown"
            val thumb = item.str("thumbnail") ?: "https://i.ytimg.com/vi/$id/hqdefault.jpg"
            val durationSec = item.long("duration")
            Track(
                id = id,
                sourceId = this.id,
                title = title,
                artist = artist,
                durationMs = if (durationSec != null && durationSec > 0) durationSec * 1000 else null,
                artworkUrl = thumb,
                downloadable = true,
            )
        }.distinctBy { it.id }.take(limit)
    }

    override suspend fun featured(): List<Track> {
        val url = "$pipedBaseUrl/trending".toHttpUrl().newBuilder()
            .addQueryParameter("region", "US")
            .build()
        return runCatching {
            Http.get(url).arr.mapNotNull { item ->
                val vUrl = item.str("url") ?: return@mapNotNull null
                val id = vUrl.substringAfter("watch?v=", "").takeIf { it.isNotEmpty() }
                    ?: vUrl.substringAfterLast("/")
                val title = item.str("title") ?: return@mapNotNull null
                val artist = item.str("uploaderName") ?: "YouTube Music"
                val thumb = item.str("thumbnail") ?: "https://i.ytimg.com/vi/$id/hqdefault.jpg"
                val durationSec = item.long("duration")
                Track(
                    id = id,
                    sourceId = this.id,
                    title = title,
                    artist = artist,
                    durationMs = if (durationSec != null && durationSec > 0) durationSec * 1000 else null,
                    artworkUrl = thumb,
                    downloadable = true,
                )
            }.take(20)
        }.getOrDefault(emptyList())
    }

    override suspend fun resolveStream(track: Track): Stream {
        // 1. Try InnerTube Android Player
        runCatching {
            val body = buildJsonObject {
                putJsonObject("context") {
                    putJsonObject("client") {
                        put("clientName", "ANDROID")
                        put("clientVersion", "19.29.35")
                        put("hl", "en")
                    }
                }
                put("videoId", track.id)
            }
            val req = Request.Builder()
                .url("https://music.youtube.com/youtubei/v1/player")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()
            val json = Http.request(req)
            val formats = json.at("streamingData")?.at("adaptiveFormats").arr
            val bestAudio = formats.filter {
                val mime = it.str("mimeType").orEmpty()
                mime.startsWith("audio/") && it.str("url") != null
            }.maxByOrNull { it.long("bitrate") ?: 0 }
            bestAudio.str("url")?.let { return Stream(it) }
        }

        // 2. Try Piped Streams API
        val pipedUrl = "$pipedBaseUrl/streams/${track.id}".toHttpUrl()
        val json = Http.get(pipedUrl)
        val audioStreams = json.at("audioStreams").arr
        val best = audioStreams.filter { it.str("url") != null }
            .maxByOrNull { it.long("bitrate") ?: 0 }
            ?: throw IOException("No playable audio stream found for ${track.title}")

        val url = best.str("url") ?: throw IOException("Stream URL missing")
        return Stream(url)
    }

    override suspend fun resolveDownload(track: Track): Stream? = resolveStream(track)
}
