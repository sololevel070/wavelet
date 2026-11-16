package app.wavelet.source.impl

import app.wavelet.net.Http
import app.wavelet.net.arr
import app.wavelet.net.at
import app.wavelet.net.str
import app.wavelet.source.LinkSource
import app.wavelet.source.Track
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.net.URLEncoder

/**
 * Finds the playing song on other services. Gets the exact Apple Music link from the iTunes
 * Search API, and builds search links for the other services. Only runs when the user asks.
 *
 * (Odesli/song.link closed its public API in 2026, so it is no longer used.)
 */
class SongLinkSource(
    override val id: String,
    override val name: String,
) : LinkSource {

    override suspend fun links(track: Track): Map<String, String> {
        if (track.isLive) return emptyMap()
        val term = "${track.artist} ${track.title}".trim()
        val links = linkedMapOf<String, String>()

        val itunes = "https://itunes.apple.com/search".toHttpUrl().newBuilder()
            .addQueryParameter("term", term)
            .addQueryParameter("entity", "song")
            .addQueryParameter("limit", "1")
            .build()
        runCatching { Http.get(itunes).at("results").arr.firstOrNull().str("trackViewUrl") }
            .getOrNull()?.let { links["Apple Music"] = it }

        val path = URLEncoder.encode(term, "UTF-8").replace("+", "%20")
        links["Search Spotify"] = "https://open.spotify.com/search/$path"
        links["Search YouTube Music"] = "https://music.youtube.com/search?q=$path"
        links["Search Deezer"] = "https://www.deezer.com/search/$path"
        links["Search Tidal"] = "https://tidal.com/search?q=$path"
        links["Search Amazon Music"] = "https://music.amazon.com/search/$path"
        return links
    }
}
