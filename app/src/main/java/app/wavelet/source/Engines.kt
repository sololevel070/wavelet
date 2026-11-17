package app.wavelet.source

import android.content.Context
import app.wavelet.data.SourceConfig
import app.wavelet.download.DownloadRepository
import app.wavelet.source.impl.AudiusSource
import app.wavelet.source.impl.DownloadsSource
import app.wavelet.source.impl.InternetArchiveSource
import app.wavelet.source.impl.JamendoSource
import app.wavelet.source.impl.JellyfinSource
import app.wavelet.source.impl.LocalSource
import app.wavelet.source.impl.LrcLibSource
import app.wavelet.source.impl.PlexSource
import app.wavelet.source.impl.RadioBrowserSource
import app.wavelet.source.impl.SongLinkSource
import app.wavelet.source.impl.SpotifySource
import app.wavelet.source.impl.SubsonicSource
import app.wavelet.source.impl.YouTubeMusicSource
import app.wavelet.source.impl.YtDlpSource

data class OptionSpec(
    val key: String,
    val label: String,
    val secret: Boolean = false,
    val hint: String = "",
)

data class EngineInfo(
    val engine: String,
    val displayName: String,
    val description: String,
    val options: List<OptionSpec> = emptyList(),
    /** Users may add several instances, e.g. one per server. */
    val multiInstance: Boolean = false,
)

/**
 * The built-in source implementations. Repos can only enable and configure these, so a repo
 * never runs downloaded code on the device.
 */
object Engines {
    private val serverOptions = listOf(
        OptionSpec("url", "Server URL", hint = "https://music.example.com"),
        OptionSpec("username", "Username"),
        OptionSpec("password", "Password", secret = true),
    )

    val all = listOf(
        EngineInfo("local", "On this device", "Music files stored on this phone."),
        EngineInfo("downloads", "Downloads", "Tracks you downloaded in Wavelet."),
        EngineInfo(
            "youtube_music", "YouTube / yt-dlp",
            "Search songs or paste any YouTube URL to stream and download audio (powered by yt-dlp).",
        ),
        EngineInfo(
            "spotify", "Spotify",
            "Browse official Spotify metadata and artwork, streaming and downloading via YouTube audio.",
            listOf(
                OptionSpec("client_id", "Client ID (optional)", hint = "From developer.spotify.com"),
                OptionSpec("client_secret", "Client Secret (optional)", secret = true),
            ),
        ),
        EngineInfo(
            "jamendo", "Jamendo",
            "Free Creative Commons music. Needs a free client ID from devportal.jamendo.com.",
            listOf(OptionSpec("client_id", "Client ID", hint = "From devportal.jamendo.com")),
        ),
        EngineInfo("internet_archive", "Internet Archive", "Live concerts, netlabel releases and public-domain audio from archive.org."),
        EngineInfo("audius", "Audius", "Free music uploaded by independent artists to Audius."),
        EngineInfo("radio_browser", "Radio Browser", "Tens of thousands of internet radio stations from radio-browser.info."),
        EngineInfo("subsonic", "Navidrome / Subsonic", "Your own Navidrome, Airsonic, Gonic or other Subsonic-compatible server.", serverOptions, multiInstance = true),
        EngineInfo("jellyfin", "Jellyfin", "The music library on your own Jellyfin server.", serverOptions, multiInstance = true),
        EngineInfo(
            "plex", "Plex", "Music on your own Plex Media Server.",
            listOf(
                OptionSpec("url", "Server URL", hint = "http://192.168.1.10:32400"),
                OptionSpec("token", "X-Plex-Token", secret = true, hint = "From Plex support: \"Finding an authentication token\""),
            ),
            multiInstance = true,
        ),
        EngineInfo("lrclib", "LRCLIB lyrics", "Synced lyrics from lrclib.net."),
        EngineInfo("songlink", "Song links", "Opens the playing song in Apple Music, Spotify, YouTube Music, Deezer, Tidal or Amazon Music."),
    )

    fun info(engine: String): EngineInfo? = all.firstOrNull { it.engine == engine }

    fun missingOptions(cfg: SourceConfig): List<OptionSpec> =
        info(cfg.engine)?.options.orEmpty().filter { cfg.options[it.key].isNullOrBlank() }

    fun create(cfg: SourceConfig, context: Context, downloads: DownloadRepository): Source? {
        val o = cfg.options
        return when (cfg.engine) {
            "local" -> LocalSource(cfg.instanceId, cfg.name, context)
            "downloads" -> DownloadsSource(cfg.instanceId, cfg.name, downloads)
            "youtube_music" -> YtDlpSource(cfg.instanceId, cfg.name, context)
            "spotify" -> SpotifySource(cfg.instanceId, cfg.name, o["client_id"].orEmpty(), o["client_secret"].orEmpty())
            "jamendo" -> JamendoSource(cfg.instanceId, cfg.name, o.getValue("client_id"))
            "internet_archive" -> InternetArchiveSource(cfg.instanceId, cfg.name)
            "radio_browser" -> RadioBrowserSource(cfg.instanceId, cfg.name)
            "audius" -> AudiusSource(cfg.instanceId, cfg.name)
            "subsonic" -> SubsonicSource(cfg.instanceId, cfg.name, o.getValue("url"), o.getValue("username"), o.getValue("password"))
            "jellyfin" -> JellyfinSource(cfg.instanceId, cfg.name, o.getValue("url"), o.getValue("username"), o.getValue("password"))
            "plex" -> PlexSource(cfg.instanceId, cfg.name, o.getValue("url"), o.getValue("token"))
            "lrclib" -> LrcLibSource(cfg.instanceId, cfg.name)
            "songlink" -> SongLinkSource(cfg.instanceId, cfg.name)
            else -> null
        }
    }
}
