package app.wavelet.source.impl

import app.wavelet.net.Http
import app.wavelet.net.arr
import app.wavelet.net.at
import app.wavelet.net.long
import app.wavelet.net.str
import app.wavelet.source.MusicSource
import app.wavelet.source.Stream
import app.wavelet.source.Track
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import java.io.IOException

/**
 * Spotify catalog source (inspired by Spotube architecture).
 * Queries official Spotify Web API for search, metadata, and high-res album artwork,
 * then resolves playable audio streams via YouTube Music.
 */
class SpotifySource(
    override val id: String,
    override val name: String,
    private val clientId: String = "",
    private val clientSecret: String = "",
) : MusicSource {

    override val showInDiscover: Boolean get() = true

    private var cachedToken: String? = null
    private var tokenExpiresAt: Long = 0
    private val ytFallback = YouTubeMusicSource("yt_resolver", "YouTube Music")

    override suspend fun search(query: String, limit: Int): List<Track> {
        val q = query.trim()
        if (q.isBlank()) return emptyList()

        val token = getAccessToken()
        val url = "https://api.spotify.com/v1/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", q)
            .addQueryParameter("type", "track")
            .addQueryParameter("limit", limit.coerceIn(1, 50).toString())
            .build()

        val headers = mapOf("Authorization" to "Bearer $token")
        val json = runCatching { Http.get(url, headers) }.getOrNull() ?: return emptyList()

        return json.at("tracks")?.at("items").arr.mapNotNull { item ->
            val trackId = item.str("id") ?: return@mapNotNull null
            val title = item.str("name") ?: return@mapNotNull null
            val artists = item.at("artists").arr.mapNotNull { it.str("name") }.joinToString(", ")
            val album = item.at("album").str("name")
            val artwork = item.at("album")?.at("images").arr.firstOrNull().str("url")
            val duration = item.long("duration_ms")

            Track(
                id = trackId,
                sourceId = this.id,
                title = title,
                artist = if (artists.isNotBlank()) artists else "Unknown Artist",
                album = album,
                durationMs = duration,
                artworkUrl = artwork,
                downloadable = true,
            )
        }
    }

    override suspend fun featured(): List<Track> {
        val token = runCatching { getAccessToken() }.getOrNull() ?: return emptyList()
        val url = "https://api.spotify.com/v1/browse/new-releases?limit=20".toHttpUrl()
        val headers = mapOf("Authorization" to "Bearer $token")
        val json = runCatching { Http.get(url, headers) }.getOrNull() ?: return emptyList()

        return json.at("albums")?.at("items").arr.mapNotNull { album ->
            val albumId = album.str("id") ?: return@mapNotNull null
            val title = album.str("name") ?: return@mapNotNull null
            val artists = album.at("artists").arr.mapNotNull { it.str("name") }.joinToString(", ")
            val artwork = album.at("images").arr.firstOrNull().str("url")
            Track(
                id = "album_$albumId",
                sourceId = this.id,
                title = title,
                artist = if (artists.isNotBlank()) artists else "Spotify New Releases",
                album = title,
                artworkUrl = artwork,
                downloadable = true,
            )
        }
    }

    override suspend fun resolveStream(track: Track): Stream {
        val query = "${track.artist} - ${track.title}"
        val ytTracks = ytFallback.search(query, limit = 5)
        val matched = ytTracks.firstOrNull()
            ?: throw IOException("No matching audio stream found for $query")
        return ytFallback.resolveStream(matched)
    }

    override suspend fun resolveDownload(track: Track): Stream? = resolveStream(track)

    private suspend fun getAccessToken(): String {
        val now = System.currentTimeMillis()
        if (cachedToken != null && now < tokenExpiresAt - 60_000) {
            return cachedToken!!
        }

        // If client ID and secret are configured, use official client_credentials flow
        if (clientId.isNotBlank() && clientSecret.isNotBlank()) {
            val req = Request.Builder()
                .url("https://accounts.spotify.com/api/token")
                .header(
                    "Authorization",
                    "Basic " + android.util.Base64.encodeToString(
                        "$clientId:$clientSecret".toByteArray(Charsets.UTF_8),
                        android.util.Base64.NO_WRAP,
                    ),
                )
                .post(FormBody.Builder().add("grant_type", "client_credentials").build())
                .build()

            val res = Http.request(req)
            val token = res.str("access_token") ?: throw IOException("Could not obtain Spotify token")
            val expiresIn = res.long("expires_in") ?: 3600
            cachedToken = token
            tokenExpiresAt = now + (expiresIn * 1000)
            return token
        }

        // Fallback: anonymous public web player token (Spotube mechanism)
        val anonUrl = "https://open.spotify.com/get_access_token?reason=transport&productType=web_player".toHttpUrl()
        val res = Http.get(anonUrl)
        val token = res.str("accessToken") ?: throw IOException("Could not obtain public Spotify token")
        val expiresAt = res.long("accessTokenExpirationTimestampMs") ?: (now + 3600_000)
        cachedToken = token
        tokenExpiresAt = expiresAt
        return token
    }
}
