package app.wavelet.data

import android.content.Context
import app.wavelet.net.Http
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import java.io.File

/**
 * One configured source. [engine] names a built-in implementation; [options] holds its
 * settings (server URL, API key, ...). Several instances may share an engine, e.g. two
 * Navidrome servers.
 */
@Serializable
data class SourceConfig(
    val instanceId: String,
    val engine: String,
    val name: String,
    val enabled: Boolean = true,
    val options: Map<String, String> = emptyMap(),
    /** URL of the repo this source was installed from, or null for built-in/user-added. */
    val fromRepo: String? = null,
    val version: String? = null,
)

@Serializable
enum class ThemeMode { System, Light, Dark }

/** Audio container used for YouTube downloads. Best keeps the stream's own format. */
@Serializable
enum class YtAudioFormat { Best, Opus, M4a, Mp3 }

/** A playlist or channel URL checked for new uploads. */
@Serializable
data class WatchedPlaylist(
    val url: String,
    val title: String,
    val knownIds: List<String> = emptyList(),
    /** Download uploads that were not in [knownIds] the last time this was checked. */
    val autoDownload: Boolean = true,
)

@Serializable
data class Settings(
    val theme: ThemeMode = ThemeMode.Dark,
    /** Use the wallpaper-based colours on Android 12+. */
    val dynamicColor: Boolean = false,
    /** Tint Now Playing and the mini player with the artwork's colours. */
    val artworkColors: Boolean = true,
    val listenBrainzToken: String = "",
    val listenBrainzUser: String = "",
    val scrobble: Boolean = true,
    val recordHistory: Boolean = true,
    /** Prefer downloaded copies when they exist. */
    val preferOffline: Boolean = true,
    /** The welcome tour has been completed. */
    val onboarded: Boolean = false,
    /** Show synced lyrics in a bubble over other apps. */
    val floatingLyrics: Boolean = false,
    /** 0 = small, 1 = medium, 2 = large. */
    val floatingLyricsSize: Int = 1,
    val floatingLyricsHideInApp: Boolean = true,
    /** Restore the last queue (paused) when the app starts. */
    val resumeQueue: Boolean = true,
    /** Keep streamed songs on the phone so replays work offline. 0 turns the cache off. */
    val cacheSizeMb: Int = 1024,
    val skipSilence: Boolean = false,
    /** Evens out loudness between songs. */
    val normalizeVolume: Boolean = false,
    /** Pure black backgrounds (saves power on OLED screens). */
    val pureBlack: Boolean = false,
    /** Accent colour follows the playing song's artwork. */
    val dynamicAccent: Boolean = false,
    /** Fade out over the last seconds of the sleep timer. */
    val sleepFade: Boolean = true,
    val lastFmApiKey: String = "",
    val lastFmSecret: String = "",
    val lastFmUser: String = "",
    val lastFmSession: String = "",
    val lastFmScrobble: Boolean = true,
    /** Crossfade duration in seconds between consecutive songs. 0 turns it off. */
    val crossfadeSeconds: Int = 0,
    /** Container yt-dlp writes when saving YouTube audio. */
    val ytAudioFormat: YtAudioFormat = YtAudioFormat.Best,
    val ytEmbedMetadata: Boolean = true,
    val ytEmbedThumbnail: Boolean = true,
    /** Cut sponsor, intro, and outro segments out of YouTube downloads. */
    val ytSponsorBlock: Boolean = false,
    /** How many downloads run at once. */
    val ytConcurrent: Int = 2,
    /** Netscape cookie file contents, used for age-restricted or signed-in videos. */
    val ytCookies: String = "",
    /** Extra yt-dlp arguments, appended as-is. */
    val ytExtraArgs: String = "",
    val equalizerEnabled: Boolean = false,
    val equalizerPreset: Int = -1,
    val bassBoost: Int = 0,
    val virtualizer: Int = 0,
)

@Serializable
data class AppState(
    val sources: List<SourceConfig> = DefaultSources.list,
    val repos: List<String> = emptyList(),
    val settings: Settings = Settings(),
    val recentSearches: List<String> = emptyList(),
    val watched: List<WatchedPlaylist> = emptyList(),
)

object DefaultSources {
    val list = listOf(
        SourceConfig("local", "local", "On this device"),
        SourceConfig("downloads", "downloads", "Downloads"),
        SourceConfig("youtube_music", "youtube_music", "YouTube / yt-dlp"),
        SourceConfig("spotify", "spotify", "Spotify"),
        SourceConfig("internet_archive", "internet_archive", "Internet Archive"),
        SourceConfig("radio_browser", "radio_browser", "Radio Browser"),
        SourceConfig("audius", "audius", "Audius"),
        SourceConfig("jamendo", "jamendo", "Jamendo", enabled = false),
        SourceConfig("lrclib", "lrclib", "LRCLIB lyrics"),
        SourceConfig("songlink", "songlink", "Song links"),
    )

    val ids: Set<String> = list.map { it.instanceId }.toSet()
}

/** Persists [AppState] as a JSON file in app-private storage. */
class AppStore(context: Context) {
    private val file = File(context.filesDir, "state.json")
    private val _state = MutableStateFlow(load())
    val state: StateFlow<AppState> = _state.asStateFlow()

    private fun load(): AppState = runCatching {
        Http.json.decodeFromString(AppState.serializer(), file.readText())
    }.getOrElse { AppState() }

    @Synchronized
    fun update(transform: (AppState) -> AppState) {
        val next = transform(_state.value)
        _state.value = next
        runCatching { file.writeText(Http.json.encodeToString(AppState.serializer(), next)) }
    }
}
