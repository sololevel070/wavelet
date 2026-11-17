package app.wavelet.download

import android.content.Context
import android.net.Uri
import android.os.Environment
import app.wavelet.data.Settings
import app.wavelet.net.Http
import app.wavelet.net.HttpException
import app.wavelet.source.Stream
import app.wavelet.source.Track
import app.wavelet.ytdlp.YtDlp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.Request
import java.io.File
import java.io.IOException

const val DOWNLOADS_SOURCE_ID = "downloads"

/**
 * Saves tracks to the app's own music folder and keeps an index of them. A downloaded track
 * keeps its original key as its id, so the app can prefer the offline copy when playing.
 */
class DownloadRepository(
    private val context: Context,
    private val settings: () -> Settings = { Settings() },
) {
    private val dir = File(
        context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: File(context.filesDir, "music"),
        "Wavelet",
    ).apply { mkdirs() }
    private val indexFile = File(context.filesDir, "downloads.json")
    private val serializer = ListSerializer(Track.serializer())

    private val _items = MutableStateFlow(loadIndex())
    val items: StateFlow<List<Track>> = _items.asStateFlow()

    /** Track key to progress 0..1, or -1 when the size is unknown. */
    private val _progress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val progress: StateFlow<Map<String, Float>> = _progress.asStateFlow()

    private fun loadIndex(): List<Track> = runCatching {
        Http.json.decodeFromString(serializer, indexFile.readText())
    }.getOrDefault(emptyList()).filter { t ->
        t.streamUrl?.let { Uri.parse(it).path }?.let { File(it).exists() } == true
    }

    private fun saveIndex() {
        runCatching { indexFile.writeText(Http.json.encodeToString(serializer, _items.value)) }
    }

    fun find(originalKey: String): Track? = _items.value.firstOrNull { it.id == originalKey }

    fun isBusy(originalKey: String) = originalKey in _progress.value

    suspend fun download(track: Track, stream: Stream) = withContext(Dispatchers.IO) {
        val key = track.key
        _progress.update { it + (key to -1f) }
        try {
            if (stream.url.startsWith(YtDlp.DOWNLOAD_PREFIX)) {
                downloadWithYtDlp(track, stream.url.removePrefix(YtDlp.DOWNLOAD_PREFIX))
                return@withContext
            }
            Http.client.newCall(Request.Builder().url(stream.url).build()).execute().use { response ->
                if (!response.isSuccessful) throw HttpException(response.code, stream.url)
                val body = response.body ?: throw IOException("Empty response")
                val ext = extensionFor(body.contentType()?.toString(), stream.url)
                val file = uniqueFile(safeName("${track.artist} - ${track.title}"), ext)
                val partial = File(file.path + ".part")
                val total = body.contentLength()
                body.byteStream().use { input ->
                    partial.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var done = 0L
                        var lastReported = 0L
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            output.write(buffer, 0, n)
                            done += n
                            if (total > 0 && done - lastReported > 128 * 1024) {
                                lastReported = done
                                _progress.update { it + (key to done.toFloat() / total) }
                            }
                        }
                    }
                }
                if (!partial.renameTo(file)) {
                    partial.copyTo(file, overwrite = true)
                    partial.delete()
                }
                val saved = track.copy(
                    id = key,
                    sourceId = DOWNLOADS_SOURCE_ID,
                    streamUrl = Uri.fromFile(file).toString(),
                    downloadable = false,
                )
                synchronized(this@DownloadRepository) {
                    _items.update { list -> list.filterNot { it.id == key } + saved }
                    saveIndex()
                }
            }
        } finally {
            _progress.update { it - key }
        }
    }

    private suspend fun downloadWithYtDlp(track: Track, videoId: String) {
        val key = track.key
        val s = settings()
        val fmt = when (s.ytAudioFormat) {
            app.wavelet.data.YtAudioFormat.Mp3 -> "mp3"
            app.wavelet.data.YtAudioFormat.M4a -> "m4a"
            app.wavelet.data.YtAudioFormat.Opus -> "opus"
            else -> "best"
        }
        val base = safeName(
            if (track.title.contains(track.artist, ignoreCase = true)) track.title
            else "${track.artist} - ${track.title}",
        )
        val tempDir = File(context.cacheDir, "ytdl/${key.hashCode().toUInt()}").apply { mkdirs() }
        try {
            val produced = YtDlp.downloadTo(
                urlOrId = videoId,
                targetDirectory = tempDir,
                baseName = base,
                format = fmt,
                embedMetadata = s.ytEmbedMetadata,
                embedThumbnail = s.ytEmbedThumbnail,
                sponsorBlock = s.ytSponsorBlock,
                onProgress = { p -> _progress.update { it + (key to p) } },
            )
            val ext = produced.extension.ifBlank { if (fmt == "best") "m4a" else fmt }
            val file = uniqueFile(base, ext)
            if (!produced.renameTo(file)) {
                produced.copyTo(file, overwrite = true)
                produced.delete()
            }
            val saved = track.copy(
                id = key,
                sourceId = DOWNLOADS_SOURCE_ID,
                streamUrl = Uri.fromFile(file).toString(),
                downloadable = false,
            )
            synchronized(this@DownloadRepository) {
                _items.update { list -> list.filterNot { it.id == key } + saved }
                saveIndex()
            }
        } finally {
            tempDir.deleteRecursively()
        }
    }

    /** Deletes a downloaded track. Accepts either the downloaded copy or the original track. */
    fun delete(track: Track) {
        val saved = if (track.sourceId == DOWNLOADS_SOURCE_ID) track else find(track.key) ?: return
        saved.streamUrl?.let { Uri.parse(it).path }?.let { File(it).delete() }
        synchronized(this) {
            _items.update { list -> list.filterNot { it.id == saved.id } }
            saveIndex()
        }
    }

    private fun uniqueFile(base: String, ext: String): File {
        var file = File(dir, "$base.$ext")
        var n = 2
        while (file.exists()) file = File(dir, "$base ($n).${ext}").also { n++ }
        return file
    }

    private fun safeName(name: String): String =
        name.replace(Regex("""[\\/:*?"<>|\p{Cntrl}]"""), "_").trim().take(120).ifBlank { "track" }

    private fun extensionFor(contentType: String?, url: String): String {
        val type = contentType.orEmpty().lowercase()
        return when {
            "mpeg" in type || "mp3" in type -> "mp3"
            "flac" in type -> "flac"
            "ogg" in type || "vorbis" in type -> "ogg"
            "opus" in type -> "opus"
            "mp4" in type || "m4a" in type || "aac" in type -> "m4a"
            "wav" in type -> "wav"
            else -> Uri.parse(url).lastPathSegment?.substringAfterLast('.', "")
                ?.lowercase()?.takeIf { it.length in 2..4 } ?: "mp3"
        }
    }
}
