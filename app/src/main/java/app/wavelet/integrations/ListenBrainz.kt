package app.wavelet.integrations

import app.wavelet.io.ParsedPlaylist
import app.wavelet.io.PlaylistFormats
import app.wavelet.net.Http
import app.wavelet.net.arr
import app.wavelet.net.at
import app.wavelet.net.str
import app.wavelet.source.Track
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

data class ListenBrainzPlaylist(val id: String, val title: String, val creator: String?, val trackCount: Int?)

/**
 * ListenBrainz (listenbrainz.org), the open MetaBrainz listening-history service.
 * Scrobbling and playlist import. Token from https://listenbrainz.org/settings/
 */
object ListenBrainz {
    private const val API = "https://api.listenbrainz.org/1"

    /** Returns the user name for a valid token. */
    suspend fun validate(token: String): String {
        val r = Http.request(
            Request.Builder().url("$API/validate-token").header("Authorization", "Token ${token.trim()}").build(),
        )
        if (r.at("valid") != JsonPrimitive(true)) throw IOException(r.str("message") ?: "This token isn't valid")
        return r.str("user_name") ?: throw IOException("This token isn't valid")
    }

    suspend fun playingNow(token: String, track: Track) = submit(token, "playing_now", track, null)

    suspend fun listened(token: String, track: Track, listenedAtSec: Long) = submit(token, "single", track, listenedAtSec)

    private suspend fun submit(token: String, type: String, track: Track, listenedAt: Long?) {
        if (track.isLive || track.artist.isBlank()) return
        val body = buildJsonObject {
            put("listen_type", type)
            putJsonArray("payload") {
                add(buildJsonObject {
                    if (listenedAt != null) put("listened_at", listenedAt)
                    putJsonObject("track_metadata") {
                        put("artist_name", track.artist)
                        put("track_name", track.title)
                        track.album?.let { put("release_name", it) }
                        putJsonObject("additional_info") {
                            put("media_player", "Wavelet")
                            put("submission_client", "Wavelet")
                            put("submission_client_version", "0.3.0")
                            track.durationMs?.let { put("duration_ms", it) }
                        }
                    }
                })
            }
        }
        Http.request(
            Request.Builder().url("$API/submit-listens")
                .header("Authorization", "Token ${token.trim()}")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build(),
        )
    }

    /** The user's own playlists plus ones ListenBrainz made for them (Weekly Jams, ...). */
    suspend fun playlists(user: String, token: String?): List<ListenBrainzPlaylist> {
        val headers = token?.takeIf { it.isNotBlank() }?.let { mapOf("Authorization" to "Token ${it.trim()}") }.orEmpty()
        val own = runCatching { Http.get("$API/user/$user/playlists".toHttpUrl(), headers) }.getOrNull()
        val createdFor = runCatching { Http.get("$API/user/$user/playlists/createdfor".toHttpUrl(), headers) }.getOrNull()
        if (own == null && createdFor == null) throw IOException("Couldn't load playlists for $user")
        return listOfNotNull(own, createdFor).flatMap { r ->
            r.at("playlists").arr.mapNotNull { wrapper ->
                val p = wrapper.at("playlist")
                val id = p.str("identifier")?.trimEnd('/')?.substringAfterLast('/') ?: return@mapNotNull null
                ListenBrainzPlaylist(id, p.str("title") ?: "Untitled", p.str("creator"), p.at("track").arr.size.takeIf { it > 0 })
            }
        }.distinctBy { it.id }
    }

    /** Accepts a playlist id or a listenbrainz.org playlist link. */
    suspend fun playlist(idOrUrl: String, token: String?): ParsedPlaylist {
        val id = idOrUrl.trim().trimEnd('/').substringAfterLast('/')
        val headers = token?.takeIf { it.isNotBlank() }?.let { mapOf("Authorization" to "Token ${it.trim()}") }.orEmpty()
        val r = Http.get("$API/playlist/$id".toHttpUrl(), headers)
        return PlaylistFormats.parseJspf(r.at("playlist") ?: throw IOException("Playlist not found"), "ListenBrainz playlist")
    }
}
