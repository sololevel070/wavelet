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
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Subsonic API (Navidrome, Airsonic, Gonic, ...). Uses salted-token auth from API 1.13+,
 * so the password itself is never sent over the network.
 */
class SubsonicSource(
    override val id: String,
    override val name: String,
    baseUrl: String,
    private val username: String,
    private val password: String,
) : MusicSource {
    private val base: HttpUrl = baseUrl.trim().trimEnd('/').toHttpUrl()
    private val random = SecureRandom()

    private fun api(method: String, vararg params: Pair<String, String>): HttpUrl {
        val salt = ByteArray(6).also(random::nextBytes).toHex()
        val token = MessageDigest.getInstance("MD5").digest((password + salt).toByteArray()).toHex()
        return base.newBuilder()
            .addPathSegment("rest")
            .addPathSegment("$method.view")
            .addQueryParameter("u", username)
            .addQueryParameter("t", token)
            .addQueryParameter("s", salt)
            .addQueryParameter("v", "1.16.1")
            .addQueryParameter("c", "Wavelet")
            .addQueryParameter("f", "json")
            .apply { params.forEach { (k, v) -> addQueryParameter(k, v) } }
            .build()
    }

    private suspend fun call(method: String, vararg params: Pair<String, String>): JsonElement {
        val response = Http.get(api(method, *params)).at("subsonic-response")
            ?: throw IOException("Not a Subsonic server")
        if (response.str("status") != "ok") {
            throw IOException(response.at("error").str("message") ?: "Subsonic server error")
        }
        return response
    }

    private fun parseSongs(songs: JsonElement?): List<Track> = songs.arr.mapNotNull { s ->
        val songId = s.str("id") ?: return@mapNotNull null
        Track(
            id = songId,
            sourceId = id,
            title = s.str("title") ?: "Untitled",
            artist = s.str("artist") ?: "Unknown artist",
            album = s.str("album"),
            durationMs = s.long("duration")?.times(1000),
            artworkUrl = s.str("coverArt")?.let { api("getCoverArt", "id" to it, "size" to "300").toString() },
            downloadable = true,
        )
    }

    override suspend fun search(query: String, limit: Int): List<Track> = parseSongs(
        call(
            "search3", "query" to query, "songCount" to limit.toString(),
            "artistCount" to "0", "albumCount" to "0",
        ).at("searchResult3").at("song"),
    )

    override suspend fun featured(): List<Track> =
        parseSongs(call("getRandomSongs", "size" to "40").at("randomSongs").at("song"))

    override suspend fun resolveStream(track: Track): Stream =
        Stream(api("stream", "id" to track.id).toString())

    override suspend fun resolveDownload(track: Track): Stream =
        Stream(api("download", "id" to track.id).toString())
}

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
