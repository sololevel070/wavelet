package app.wavelet.io

import android.util.Xml
import app.wavelet.data.PlaylistEntry
import app.wavelet.net.Http
import app.wavelet.net.arr
import app.wavelet.net.at
import app.wavelet.net.long
import app.wavelet.net.str
import app.wavelet.source.Track
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.xmlpull.v1.XmlPullParser
import java.io.IOException
import java.io.StringReader

/** A playlist read from a file. [exact] holds a ready track for entries that need no matching. */
data class ParsedPlaylist(
    val name: String,
    val entries: List<PlaylistEntry>,
    val exact: List<Track?>,
)

enum class ExportFormat(val label: String, val extension: String, val mime: String) {
    Json("Wavelet (.json) – best for moving between Wavelet installs", "json", "application/json"),
    M3u("M3U8 playlist", "m3u8", "audio/x-mpegurl"),
    Csv("CSV spreadsheet", "csv", "text/csv"),
    Xspf("XSPF playlist", "xspf", "application/xspf+xml"),
}

@Serializable
data class WaveletPlaylistFile(
    val format: String = "wavelet-playlist",
    val version: Int = 1,
    val name: String,
    val description: String = "",
    val tracks: List<WaveletPlaylistTrack>,
)

@Serializable
data class WaveletPlaylistTrack(
    val title: String,
    val artist: String,
    val album: String? = null,
    val durationMs: Long? = null,
    /** Full track details when the song was matched; lets another Wavelet play it directly. */
    val track: Track? = null,
)

/** Synthetic source id for songs that are plain web URLs from an imported file. */
const val WEB_SOURCE_ID = "web"

object PlaylistFormats {

    fun parse(fileName: String, text: String): ParsedPlaylist {
        val base = fileName.substringBeforeLast('.').ifBlank { "Imported playlist" }
        val ext = fileName.substringAfterLast('.', "").lowercase()
        val trimmed = text.trimStart('﻿', ' ', '\n', '\r', '\t')
        return when {
            ext == "json" || trimmed.startsWith("{") -> parseJson(base, trimmed)
            ext == "xspf" || trimmed.startsWith("<") -> parseXspf(base, trimmed)
            ext == "m3u" || ext == "m3u8" || trimmed.startsWith("#EXTM3U") -> parseM3u(base, trimmed)
            else -> parseCsv(base, trimmed)
        }.let { p -> if (p.entries.isEmpty()) error("No songs found in this file") else p }
    }

    // ------------------------------------------------------------------ M3U

    private fun parseM3u(name: String, text: String): ParsedPlaylist {
        var title = name
        val entries = mutableListOf<PlaylistEntry>()
        var pendingInfo: Pair<String, Long?>? = null
        text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.forEach { line ->
            when {
                line.startsWith("#PLAYLIST:") -> title = line.removePrefix("#PLAYLIST:").trim().ifBlank { title }
                line.startsWith("#EXTINF:") -> {
                    val body = line.removePrefix("#EXTINF:")
                    val seconds = body.substringBefore(',').substringBefore(' ').toLongOrNull()
                    pendingInfo = body.substringAfter(',', "").trim() to seconds?.takeIf { it > 0 }?.times(1000)
                }
                line.startsWith("#") -> Unit
                else -> {
                    val (label, duration) = pendingInfo ?: (fileStem(line) to null)
                    val (artist, songTitle) = splitArtistTitle(label)
                    entries += PlaylistEntry(songTitle, artist, null, duration, line)
                    pendingInfo = null
                }
            }
        }
        return ParsedPlaylist(title, entries, entries.map { null })
    }

    private fun fileStem(location: String) =
        location.substringAfterLast('/').substringAfterLast('\\').substringBeforeLast('.')
            .let { runCatching { java.net.URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) }

    /** "Artist - Title" → (artist, title). */
    private fun splitArtistTitle(label: String): Pair<String, String> {
        val idx = label.indexOf(" - ")
        return if (idx > 0) label.substring(0, idx).trim() to label.substring(idx + 3).trim() else "" to label.trim()
    }

    private fun webTrack(e: PlaylistEntry): Track? {
        val loc = e.location ?: return null
        if (!loc.startsWith("http://") && !loc.startsWith("https://")) return null
        return Track(
            id = loc, sourceId = WEB_SOURCE_ID, title = e.title.ifBlank { fileStem(loc) },
            artist = e.artist.ifBlank { "Unknown artist" }, album = e.album, durationMs = e.durationMs,
            streamUrl = loc,
        )
    }

    // ------------------------------------------------------------------ CSV

    private fun parseCsv(name: String, text: String): ParsedPlaylist {
        val rows = csvRows(text)
        if (rows.isEmpty()) error("The file is empty")
        val header = rows.first().map { it.trim().lowercase() }
        fun col(vararg names: String) = names.firstNotNullOfOrNull { n -> header.indexOf(n).takeIf { it >= 0 } }
            ?: names.firstNotNullOfOrNull { n -> header.indexOfFirst { it.contains(n) }.takeIf { it >= 0 } }

        val titleCol = col("track name", "title", "song", "name", "track")
        val artistCol = col("artist name(s)", "artist name", "artists", "artist", "creator")
        if (titleCol == null) error("Couldn't find a title column. The first row must be a header such as Title, Artist")
        val albumCol = col("album name", "album")
        val durationMsCol = col("duration (ms)", "track duration (ms)", "duration_ms")
        val durationCol = if (durationMsCol == null) col("duration", "length") else null
        val playlistCol = col("playlist name", "playlist")
        val linkCol = col("url", "link", "location")

        val entries = rows.drop(1).mapNotNull { r ->
            val title = r.getOrNull(titleCol)?.trim().orEmpty()
            if (title.isEmpty()) return@mapNotNull null
            val duration = durationMsCol?.let { r.getOrNull(it)?.trim()?.toDoubleOrNull()?.toLong() }
                ?: durationCol?.let { r.getOrNull(it)?.let(::parseDuration) }
            PlaylistEntry(
                title = title,
                artist = artistCol?.let { r.getOrNull(it)?.trim() }.orEmpty(),
                album = albumCol?.let { r.getOrNull(it)?.trim() }?.ifBlank { null },
                durationMs = duration,
                location = linkCol?.let { r.getOrNull(it)?.trim() }?.ifBlank { null },
            )
        }
        val title = playlistCol?.let { rows.getOrNull(1)?.getOrNull(it)?.trim() }?.ifBlank { null } ?: name
        return ParsedPlaylist(title, entries, entries.map { null })
    }

    /** "3:45", "1:02:03" or seconds. */
    private fun parseDuration(s: String): Long? {
        val parts = s.trim().split(':').map { it.toDoubleOrNull() ?: return null }
        val seconds = parts.fold(0.0) { acc, p -> acc * 60 + p }
        return (seconds * 1000).toLong().takeIf { it > 0 }
    }

    /** RFC-4180 CSV parser (quotes, escaped quotes, newlines in quotes); also accepts ; and tab. */
    fun csvRows(text: String): List<List<String>> {
        val firstLine = text.lineSequence().firstOrNull().orEmpty()
        val delimiter = listOf(',', ';', '\t').maxBy { d -> firstLine.count { it == d } }
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            when {
                quoted && ch == '"' && i + 1 < text.length && text[i + 1] == '"' -> { cell.append('"'); i++ }
                ch == '"' -> quoted = !quoted
                !quoted && ch == delimiter -> { row += cell.toString(); cell.clear() }
                !quoted && (ch == '\n' || ch == '\r') -> {
                    if (ch == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                    row += cell.toString(); cell.clear()
                    if (row.any { it.isNotBlank() }) rows += row
                    row = mutableListOf()
                }
                else -> cell.append(ch)
            }
            i++
        }
        row += cell.toString()
        if (row.any { it.isNotBlank() }) rows += row
        return rows
    }

    private fun csvCell(s: String?): String {
        val v = s.orEmpty()
        return if (v.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + v.replace("\"", "\"\"") + "\"" else v
    }

    // ------------------------------------------------------------------ XSPF

    private fun parseXspf(name: String, text: String): ParsedPlaylist {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(StringReader(text))
        var title = name
        val entries = mutableListOf<PlaylistEntry>()
        var inTrack = false
        var depth = 0
        var trackDepth = -1
        val fields = mutableMapOf<String, String>()
        var tag: String? = null
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    depth++
                    tag = parser.name
                    if (parser.name == "track") { inTrack = true; trackDepth = depth; fields.clear() }
                }
                XmlPullParser.TEXT -> {
                    val value = parser.text.trim()
                    if (value.isNotEmpty()) when {
                        inTrack && tag != null -> fields.putIfAbsent(tag, value)
                        tag == "title" && depth == 2 -> title = value
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "track" && depth == trackDepth) {
                        inTrack = false
                        val t = fields["title"] ?: fields["location"]?.let(::fileStem)
                        if (t != null) entries += PlaylistEntry(
                            t, fields["creator"].orEmpty(), fields["album"],
                            fields["duration"]?.toLongOrNull(), fields["location"],
                        )
                    }
                    tag = null
                    depth--
                }
            }
        }
        return ParsedPlaylist(title, entries, entries.map { null })
    }

    private fun xml(s: String?) = s.orEmpty()
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    // ------------------------------------------------------------------ JSON (Wavelet + JSPF)

    private fun parseJson(name: String, text: String): ParsedPlaylist {
        val root: JsonElement = Http.json.parseToJsonElement(text)
        if (root.str("format") == "wavelet-playlist") {
            val file = Http.json.decodeFromJsonElement(WaveletPlaylistFile.serializer(), root)
            val entries = file.tracks.map { PlaylistEntry(it.title, it.artist, it.album, it.durationMs) }
            return ParsedPlaylist(file.name, entries, entries.map { null })
        }
        // JSPF, as used by ListenBrainz.
        val playlist = root.at("playlist") ?: error("Unknown JSON playlist format")
        return parseJspf(playlist, name)
    }

    fun parseJspf(playlist: JsonElement, fallbackName: String): ParsedPlaylist {
        val entries = playlist.at("track").arr.mapNotNull { t ->
            val title = t.str("title") ?: return@mapNotNull null
            PlaylistEntry(title, t.str("creator").orEmpty(), t.str("album"), t.long("duration"), t.str("location"))
        }
        return ParsedPlaylist(playlist.str("title") ?: fallbackName, entries, entries.map { null })
    }

    // ------------------------------------------------------------------ Spotify & YouTube Web links

    suspend fun parseSpotify(url: String): ParsedPlaylist {
        val oembedUrl = "https://open.spotify.com/oembed".toHttpUrl().newBuilder()
            .addQueryParameter("url", url.trim())
            .build()
        val oembed = runCatching { Http.get(oembedUrl) }.getOrNull()
        val playlistTitle = oembed.str("title") ?: "Spotify import"

        val tokenUrl = "https://open.spotify.com/get_access_token?reason=transport&productType=web_player".toHttpUrl()
        val tokenRes = Http.get(tokenUrl)
        val token = tokenRes.str("accessToken") ?: throw IOException("Could not connect to Spotify")

        val cleanUrl = url.substringBefore('?')
        val id = cleanUrl.substringAfterLast('/')
        val isAlbum = cleanUrl.contains("/album/")
        val isTrack = cleanUrl.contains("/track/")

        val apiUrl = if (isAlbum) {
            "https://api.spotify.com/v1/albums/$id/tracks?limit=50".toHttpUrl()
        } else if (isTrack) {
            "https://api.spotify.com/v1/tracks/$id".toHttpUrl()
        } else {
            "https://api.spotify.com/v1/playlists/$id/tracks?limit=100".toHttpUrl()
        }

        val json = Http.get(apiUrl, mapOf("Authorization" to "Bearer $token"))
        val entries = mutableListOf<PlaylistEntry>()

        if (isTrack) {
            val title = json.str("name") ?: "Unknown"
            val artists = json.at("artists").arr.mapNotNull { it.str("name") }.joinToString(", ")
            val album = json.at("album").str("name")
            val dur = json.long("duration_ms")
            entries.add(PlaylistEntry(title, artists, album, dur))
        } else {
            val items = json.at("items").arr
            items.forEach { item ->
                val trackObj = if (isAlbum) item else (item.at("track") ?: item)
                val title = trackObj.str("name") ?: return@forEach
                val artists = trackObj.at("artists").arr.mapNotNull { it.str("name") }.joinToString(", ")
                val album = if (isAlbum) playlistTitle else trackObj.at("album").str("name")
                val dur = trackObj.long("duration_ms")
                entries.add(PlaylistEntry(title, artists, album, dur))
            }
        }
        if (entries.isEmpty()) throw IOException("No tracks found on Spotify")
        return ParsedPlaylist(playlistTitle, entries, entries.map { null })
    }

    suspend fun parseYouTube(url: String, pipedBaseUrl: String = "https://pipedapi.kavin.rocks"): ParsedPlaylist {
        val listId = url.substringAfter("list=", "").substringBefore('&').takeIf { it.isNotEmpty() }
            ?: url.substringAfterLast('/')
        val pipedUrl = "$pipedBaseUrl/playlists/$listId".toHttpUrl()
        val json = Http.get(pipedUrl)
        val name = json.str("name") ?: "YouTube Playlist"
        val streams = json.at("relatedStreams").arr
        val entries = streams.mapNotNull { s ->
            val title = s.str("title") ?: return@mapNotNull null
            val artist = s.str("uploaderName").orEmpty()
            val durationSec = s.long("duration")
            val durMs = if (durationSec != null && durationSec > 0) durationSec * 1000 else null
            PlaylistEntry(title, artist, null, durMs)
        }
        if (entries.isEmpty()) throw IOException("No tracks found on YouTube playlist")
        return ParsedPlaylist(name, entries, entries.map { null })
    }

    // ------------------------------------------------------------------ export

    /** The track's URL if it is a public web link; never URLs that carry a login token. */
    private fun publicUrl(t: Track): String? = t.streamUrl?.takeIf { url ->
        url.startsWith("http") && listOf("api_key=", "&t=", "?t=", "token=").none { url.lowercase().contains(it) }
    }

    data class ExportRow(val title: String, val artist: String, val album: String?, val durationMs: Long?, val track: Track?, val sourceName: String?)

    fun export(format: ExportFormat, name: String, description: String, rows: List<ExportRow>): String = when (format) {
        ExportFormat.Json -> Http.json.encodeToString(
            WaveletPlaylistFile.serializer(),
            WaveletPlaylistFile(name = name, description = description, tracks = rows.map {
                // Device-only tracks (local files, downloads) can't play elsewhere; they'll be re-matched.
                val portable = it.track?.takeUnless { t -> t.sourceId == "local" || t.sourceId == "downloads" }
                WaveletPlaylistTrack(it.title, it.artist, it.album, it.durationMs, portable?.copy(streamUrl = publicUrl(portable)))
            }),
        )
        ExportFormat.M3u -> buildString {
            appendLine("#EXTM3U")
            appendLine("#PLAYLIST:$name")
            rows.forEach { r ->
                val seconds = r.durationMs?.div(1000) ?: -1
                appendLine("#EXTINF:$seconds,${if (r.artist.isNotBlank()) "${r.artist} - " else ""}${r.title}")
                appendLine(r.track?.let(::publicUrl) ?: "${r.artist} - ${r.title}")
            }
        }
        ExportFormat.Csv -> buildString {
            appendLine("Title,Artist,Album,Duration (ms),Source,Playlist name")
            rows.forEach { r ->
                appendLine(listOf(r.title, r.artist, r.album, r.durationMs?.toString(), r.sourceName, name).joinToString(",") { csvCell(it) })
            }
        }
        ExportFormat.Xspf -> buildString {
            appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
            appendLine("<playlist version=\"1\" xmlns=\"http://xspf.org/ns/0/\">")
            appendLine("  <title>${xml(name)}</title>")
            if (description.isNotBlank()) appendLine("  <annotation>${xml(description)}</annotation>")
            appendLine("  <trackList>")
            rows.forEach { r ->
                appendLine("    <track>")
                r.track?.let(::publicUrl)?.let { appendLine("      <location>${xml(it)}</location>") }
                appendLine("      <title>${xml(r.title)}</title>")
                if (r.artist.isNotBlank()) appendLine("      <creator>${xml(r.artist)}</creator>")
                r.album?.let { appendLine("      <album>${xml(it)}</album>") }
                r.durationMs?.let { appendLine("      <duration>$it</duration>") }
                appendLine("    </track>")
            }
            appendLine("  </trackList>")
            appendLine("</playlist>")
        }
    }
}
