package app.wavelet.integrations

import app.wavelet.net.Http
import app.wavelet.net.at
import app.wavelet.net.str
import app.wavelet.source.Track
import okhttp3.FormBody
import okhttp3.Request
import java.io.IOException
import java.security.MessageDigest

/**
 * Last.fm scrobbler and now-playing integration using the official Last.fm Web Services 2.0 API.
 * https://www.last.fm/api
 */
object LastFm {
    private const val API_URL = "https://ws.audioscrobbler.com/2.0/?format=json"

    /**
     * Authenticate with username and password to obtain a persistent session key.
     */
    suspend fun getMobileSession(
        username: String,
        password: String,
        apiKey: String,
        apiSecret: String,
    ): Pair<String, String> {
        val params = sortedMapOf(
            "method" to "auth.getMobileSession",
            "username" to username.trim(),
            "password" to password,
            "api_key" to apiKey.trim(),
        )
        val sig = generateSignature(params, apiSecret.trim())
        params["api_sig"] = sig

        val form = FormBody.Builder().apply {
            params.forEach { (k, v) -> add(k, v) }
        }.build()

        val req = Request.Builder().url(API_URL).post(form).build()
        val res = Http.request(req)
        val session = res.at("session")
            ?: throw IOException(res.str("message") ?: "Last.fm authentication failed")
        val key = session.str("key") ?: throw IOException("No session key returned by Last.fm")
        val user = session.str("name") ?: username
        return key to user
    }

    /**
     * Update current playing track ("Now Playing") on user profile.
     */
    suspend fun playingNow(
        apiKey: String,
        apiSecret: String,
        sessionKey: String,
        track: Track,
    ) {
        if (track.isLive || track.artist.isBlank() || apiKey.isBlank() || sessionKey.isBlank()) return
        val params = sortedMapOf(
            "method" to "track.updateNowPlaying",
            "artist" to track.artist,
            "track" to track.title,
            "api_key" to apiKey.trim(),
            "sk" to sessionKey.trim(),
        )
        track.album?.let { if (it.isNotBlank()) params["album"] = it }
        track.durationMs?.let { if (it > 0) params["duration"] = (it / 1000).toString() }

        val sig = generateSignature(params, apiSecret.trim())
        params["api_sig"] = sig

        val form = FormBody.Builder().apply {
            params.forEach { (k, v) -> add(k, v) }
        }.build()

        val req = Request.Builder().url(API_URL).post(form).build()
        Http.request(req)
    }

    /**
     * Scrobble track when listened (>= 50% or 4 minutes).
     */
    suspend fun scrobble(
        apiKey: String,
        apiSecret: String,
        sessionKey: String,
        track: Track,
        startedAtSec: Long,
    ) {
        if (track.isLive || track.artist.isBlank() || apiKey.isBlank() || sessionKey.isBlank()) return
        val params = sortedMapOf(
            "method" to "track.scrobble",
            "artist" to track.artist,
            "track" to track.title,
            "timestamp" to startedAtSec.toString(),
            "api_key" to apiKey.trim(),
            "sk" to sessionKey.trim(),
        )
        track.album?.let { if (it.isNotBlank()) params["album"] = it }
        track.durationMs?.let { if (it > 0) params["duration"] = (it / 1000).toString() }

        val sig = generateSignature(params, apiSecret.trim())
        params["api_sig"] = sig

        val form = FormBody.Builder().apply {
            params.forEach { (k, v) -> add(k, v) }
        }.build()

        val req = Request.Builder().url(API_URL).post(form).build()
        Http.request(req)
    }

    /**
     * Generates Last.fm API signature: md5(sorted_param_keys_and_values + secret)
     */
    private fun generateSignature(params: Map<String, String>, secret: String): String {
        val s = StringBuilder()
        params.toSortedMap().forEach { (k, v) ->
            if (k != "format" && k != "callback") {
                s.append(k).append(v)
            }
        }
        s.append(secret)
        val md = MessageDigest.getInstance("MD5")
        val bytes = md.digest(s.toString().toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
