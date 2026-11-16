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
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/** Music on a Jellyfin server, through its REST API. */
class JellyfinSource(
    override val id: String,
    override val name: String,
    baseUrl: String,
    private val username: String,
    private val password: String,
) : MusicSource {
    private val base = baseUrl.trim().trimEnd('/')
    private val deviceId = "wavelet-" + id.hashCode().toUInt().toString(16)
    private val loginLock = Mutex()
    private var token: String? = null
    private var userId: String? = null

    private fun authHeader(): String =
        "MediaBrowser Client=\"Wavelet\", Device=\"Android\", DeviceId=\"$deviceId\", Version=\"0.1.0\"" +
            (token?.let { ", Token=\"$it\"" } ?: "")

    private suspend fun login() = loginLock.withLock {
        if (token != null) return@withLock
        val body = buildJsonObject {
            put("Username", username)
            put("Pw", password)
        }.toString().toRequestBody("application/json".toMediaType())
        val response = Http.request(
            Request.Builder()
                .url("$base/Users/AuthenticateByName")
                .post(body)
                .header("Authorization", authHeader())
                .build(),
        )
        userId = response.at("User").str("Id") ?: throw IOException("Jellyfin login failed")
        token = response.str("AccessToken") ?: throw IOException("Jellyfin login failed")
    }

    private suspend fun items(vararg params: Pair<String, String>): List<Track> {
        login()
        val url = "$base/Users/$userId/Items".toHttpUrl().newBuilder()
            .addQueryParameter("IncludeItemTypes", "Audio")
            .addQueryParameter("Recursive", "true")
            .apply { params.forEach { (k, v) -> addQueryParameter(k, v) } }
            .build()
        return Http.get(url, mapOf("Authorization" to authHeader())).at("Items").arr.mapNotNull { item ->
            val itemId = item.str("Id") ?: return@mapNotNull null
            val artist = (item.at("Artists").arr.firstOrNull() as? JsonPrimitive)?.contentOrNull
                ?: item.str("AlbumArtist") ?: "Unknown artist"
            val artwork = when {
                item.at("ImageTags").str("Primary") != null -> "$base/Items/$itemId/Images/Primary?maxHeight=300"
                item.str("AlbumId") != null && item.str("AlbumPrimaryImageTag") != null ->
                    "$base/Items/${item.str("AlbumId")}/Images/Primary?maxHeight=300"
                else -> null
            }
            Track(
                id = itemId,
                sourceId = id,
                title = item.str("Name") ?: "Untitled",
                artist = artist,
                album = item.str("Album"),
                durationMs = item.long("RunTimeTicks")?.div(10_000),
                artworkUrl = artwork,
                downloadable = true,
            )
        }
    }

    override suspend fun search(query: String, limit: Int): List<Track> =
        items("searchTerm" to query, "Limit" to limit.toString())

    override suspend fun featured(): List<Track> =
        items("SortBy" to "Random", "Limit" to "40")

    override suspend fun resolveStream(track: Track): Stream {
        login()
        return Stream("$base/Audio/${track.id}/stream?static=true&api_key=$token")
    }

    override suspend fun resolveDownload(track: Track): Stream {
        login()
        return Stream("$base/Items/${track.id}/Download?api_key=$token")
    }
}
