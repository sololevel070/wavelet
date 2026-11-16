package app.wavelet.source

import app.wavelet.net.Http
import app.wavelet.net.str
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import okhttp3.Request

/**
 * A repo is a JSON index hosted anywhere (GitHub, GitLab, your own server). It lists source
 * entries, each naming a built-in engine plus default options. See repo/sources.json.
 *
 * Soundbound-style repos (a JSON array of `source_id` / `manifest.zipline.json` entries) are
 * also read: each entry is matched to the Wavelet engine that does the same job, and entries
 * without one are listed with the reason. Wavelet never downloads or runs plugin code.
 */
@Serializable
data class RepoIndex(
    val name: String,
    val description: String? = null,
    val sources: List<RepoEntry> = emptyList(),
)

@Serializable
data class RepoEntry(
    val id: String,
    val engine: String,
    val name: String,
    val version: String = "1",
    val description: String? = null,
    val options: Map<String, String> = emptyMap(),
    /** Set when the entry can't be used in Wavelet; explains why. */
    val unsupportedReason: String? = null,
    val iconUrl: String? = null,
)

class RepoManager {
    suspend fun fetch(url: String): RepoIndex {
        val body = Http.text(Request.Builder().url(url).build())
        return parse(body, url)
    }

    companion object {
        fun parse(body: String, url: String): RepoIndex {
            val root: JsonElement = Http.json.parseToJsonElement(body.trim())
            return if (root is JsonArray) SoundboundRepo.convert(root, url)
            else Http.json.decodeFromJsonElement(RepoIndex.serializer(), root)
        }
    }
}

/** Maps entries of a Soundbound-format repo onto Wavelet's built-in engines. */
object SoundboundRepo {
    private data class Mapping(val engine: String, val id: String, val name: String, val note: String? = null)

    /** Entries that have a legal Wavelet equivalent. */
    private val supported = mapOf(
        "navidrome" to Mapping("subsonic", "sb-navidrome", "Navidrome", "Enter your server address and login after installing."),
        "jellyfin" to Mapping("jellyfin", "sb-jellyfin", "Jellyfin", "Enter your server address and login after installing."),
        "plex" to Mapping("plex", "sb-plex", "Plex", "Enter your server address and Plex token after installing."),
        "jamendo" to Mapping("jamendo", "jamendo", "Jamendo", "Needs a free client ID from devportal.jamendo.com."),
        "audius" to Mapping("audius", "audius", "Audius", "Free music uploaded by artists to Audius."),
        "lrclib" to Mapping("lrclib", "lrclib", "LRCLIB lyrics"),
        "odesli" to Mapping("songlink", "songlink", "Song links", "Odesli's public API has closed; Wavelet's song links use iTunes Search plus search links instead."),
    )

    private val streamingServices = mapOf(
        "youtube" to "YouTube", "spotify" to "Spotify", "tidal" to "Tidal", "applemusic" to "Apple Music",
        "amazonmusic" to "Amazon Music", "deezer" to "Deezer", "qobuz" to "Qobuz", "soundcloud" to "SoundCloud",
        "saavn" to "JioSaavn", "gaana" to "Gaana", "monochrome" to "Tidal (via Monochrome)",
    )
    private val unofficialLyrics = setOf("netease", "kuwo", "kugou", "qqmusic", "petitlyrics", "ttmllyrics", "ttml", "musixmatch", "genius")

    private fun reasonFor(key: String): String = when {
        key in streamingServices ->
            "Gets audio from ${streamingServices[key]} without an official API, so Wavelet doesn't support it."
        key in unofficialLyrics ->
            "Uses an unofficial lyrics API, so Wavelet doesn't support it. Wavelet uses LRCLIB for lyrics."
        key == "listenbrainz" -> "Built into Wavelet: connect it in Settings › ListenBrainz."
        key == "bandcamp" -> "Bandcamp has no public playback API, so Wavelet doesn't support it."
        key == "slskd" -> "Soulseek peer-to-peer downloads aren't supported."
        key == "shazam" || key == "googletranslate" -> "Uses an unofficial API, so Wavelet doesn't support it."
        key == "acoustid" || key == "lastfm" || key == "discordpresence" -> "Not available in Wavelet yet."
        else -> "Wavelet has no equivalent for this source."
    }

    /** "in.shabinder…extension.shabinder.applemusic" / "AppleMusicProvider" → "applemusic". */
    private val otherKeys = setOf("listenbrainz", "bandcamp", "slskd", "shazam", "googletranslate", "acoustid", "lastfm", "discordpresence")

    private fun keyOf(e: JsonElement): String {
        val fromId = e.str("source_id")?.substringAfterLast('.')?.lowercase()
        val fromName = e.str("source_name")?.removeSuffix("Provider")?.lowercase()
        val candidates = listOfNotNull(fromId, fromName).flatMap { listOf(it, it.removeSuffix("lyrics")) }
        val known = supported.keys + streamingServices.keys + unofficialLyrics + otherKeys
        return candidates.firstOrNull { it in known }
            ?: known.firstOrNull { k -> candidates.any { it.startsWith(k) } }
            ?: fromId ?: fromName ?: "unknown"
    }

    fun convert(array: JsonArray, url: String): RepoIndex {
        val entries = array.mapNotNull { e ->
            val key = keyOf(e)
            val display = e.str("source_name")?.removeSuffix("Provider")?.replace(Regex("([a-z])([A-Z])"), "$1 $2") ?: key
            val version = e.str("source_version_name") ?: "1"
            val map = supported[key]
            if (map != null) {
                RepoEntry(
                    id = map.id, engine = map.engine, name = map.name, version = version,
                    description = map.note ?: Engines.info(map.engine)?.description, iconUrl = e.str("icon_url"),
                )
            } else {
                RepoEntry(
                    id = "sb-$key", engine = "", name = display, version = version,
                    unsupportedReason = reasonFor(key), iconUrl = e.str("icon_url"),
                )
            }
        }.distinctBy { it.id }
            .sortedWith(compareBy({ it.unsupportedReason != null }, { it.name.lowercase() }))
        val supportedCount = entries.count { it.unsupportedReason == null }
        return RepoIndex(
            name = "Soundbound repo",
            description = "A Soundbound extensions repo ($url). $supportedCount of ${entries.size} entries match a Wavelet source. " +
                "Wavelet doesn't download or run Soundbound's plugin code; it uses its own built-in sources.",
            sources = entries,
        )
    }
}
