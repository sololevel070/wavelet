package app.wavelet.ytdlp

import android.content.Context
import android.util.Log
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

object YtDlp {
    /** Marks a download that must be fetched by yt-dlp itself (googlevideo URLs need its headers). */
    const val DOWNLOAD_PREFIX = "ytdlp://"

    private val initMutex = Mutex()
    @Volatile private var ready = false
    @Volatile private var cookieFile: File? = null
    @Volatile private var extraArgs: List<String> = emptyList()

    suspend fun ensureInitialized(context: Context) {
        if (ready) return
        initMutex.withLock {
            if (ready) return
            withContext(Dispatchers.IO) {
                try {
                    YoutubeDL.getInstance().init(context.applicationContext)
                } catch (e: Throwable) {
                    Log.w("Wavelet", "YoutubeDL init failed", e)
                }
                try {
                    val cls = runCatching { Class.forName("com.yausername.youtubedl_android.FFmpeg") }
                        .getOrElse { Class.forName("com.yausername.ffmpeg.FFmpeg") }
                    val inst = cls.getMethod("getInstance").invoke(null)
                    cls.getMethod("init", android.content.Context::class.java).invoke(inst, context.applicationContext)
                } catch (e: Throwable) {
                    Log.w("Wavelet", "FFmpeg init failed", e)
                }
                updateIfStale(context.applicationContext)
                ready = true
            }
        }
    }

    /**
     * The bundled yt-dlp from youtubedl-android ages quickly and YouTube changes break it
     * (SABR streaming returns HTTP 403 for old builds). Refresh it from yt-dlp's stable release
     * channel when the installed build is more than [maxAgeDays] old.
     */
    private fun updateIfStale(context: Context) {
        try {
            val current = runCatching { YoutubeDL.getInstance().version(context) }.getOrNull()
            if (!isStale(current)) return
            val status = YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel._STABLE)
            Log.i("Wavelet", "yt-dlp update from $current: $status (now ${YoutubeDL.getInstance().version(context)})")
        } catch (e: Throwable) {
            Log.w("Wavelet", "yt-dlp update failed", e)
        }
    }

    private fun isStale(version: String?, maxAgeDays: Int = 30): Boolean {
        // yt-dlp versions look like 2026.01.15 (calendar dates).
        val parts = version?.split('.')?.mapNotNull { it.trim().toIntOrNull() } ?: return true
        if (parts.size < 3) return true
        val released = java.util.Calendar.getInstance().apply {
            clear()
            set(parts[0], (parts[1] - 1).coerceIn(0, 11), parts[2].coerceIn(1, 28))
        }
        val age = System.currentTimeMillis() - released.timeInMillis
        return age > maxAgeDays.toLong() * 24 * 60 * 60 * 1000
    }

    /** Applies the user's cookie file and extra yt-dlp arguments to every request. */
    fun configure(context: Context, cookies: String, extra: String) {
        extraArgs = extra.split('\n', ' ')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        cookieFile = if (cookies.isBlank()) {
            null
        } else {
            File(context.filesDir, "yt_cookies.txt").apply {
                runCatching { writeText(cookies) }
            }
        }
    }

    fun isUrl(text: String): Boolean {
        val t = text.trim()
        return t.startsWith("http://") || t.startsWith("https://") ||
            t.startsWith("youtu.be/") || t.startsWith("youtube.com/") ||
            t.startsWith("www.youtube.com/") || t.startsWith("music.youtube.com/")
    }

    fun normalizeUrl(text: String): String {
        val t = text.trim()
        return if (t.startsWith("http://") || t.startsWith("https://")) t else "https://$t"
    }

    private fun YoutubeDLRequest.applyCommonOptions() {
        cookieFile?.let { addOption("--cookies", it.absolutePath) }
        extraArgs.forEach { addOption(it) }
    }

    /** Run yt-dlp and return stdout lines. */
    private fun execute(request: YoutubeDLRequest): String {
        request.applyCommonOptions()
        val response = YoutubeDL.getInstance().execute(request)
        return response.out.orEmpty()
    }

    data class Entry(
        val id: String,
        val url: String,
        val title: String,
        val artist: String,
        val durationSec: Long?,
        val thumbnail: String?,
        val webpageUrl: String?,
    )

    suspend fun search(query: String, limit: Int = 25): List<Entry> = withContext(Dispatchers.IO) {
        val n = limit.coerceIn(1, 50)
        val request = YoutubeDLRequest("ytsearch$n:$query")
        request.addOption("--dump-json")
        request.addOption("--flat-playlist")
        request.addOption("--skip-download")
        request.addOption("--no-warnings")
        request.addOption("--ignore-errors")
        val out = runCatching { execute(request) }.getOrElse { e ->
            Log.w("Wavelet", "yt-dlp search failed", e)
            return@withContext emptyList()
        }
        parseJsonLines(out)
    }

    suspend fun getUrlInfo(url: String): Entry? = withContext(Dispatchers.IO) {
        val request = YoutubeDLRequest(normalizeUrl(url))
        request.addOption("--dump-json")
        request.addOption("--no-playlist")
        request.addOption("--skip-download")
        request.addOption("--no-warnings")
        val out = try {
            execute(request)
        } catch (e: Throwable) {
            Log.w("Wavelet", "yt-dlp url info failed for $url", e)
            return@withContext null
        }
        parseJsonLines(out).firstOrNull() ?: parseSingleInfoJson(out) ?: run {
            Log.w("Wavelet", "yt-dlp url info had no parseable entry (${out.length} chars): ${out.take(300)}")
            null
        }
    }

    /** Direct stream URL for playback. YouTube may reject these without yt-dlp's headers, so
     *  downloads go through [downloadTo] instead. */
    suspend fun resolveAudioUrl(urlOrId: String): String? = withContext(Dispatchers.IO) {
        val target = if (urlOrId.startsWith("http")) urlOrId else "https://www.youtube.com/watch?v=$urlOrId"
        val request = YoutubeDLRequest(target)
        request.addOption("-f", "bestaudio/best")
        request.addOption("--get-url")
        request.addOption("--no-playlist")
        request.addOption("--no-warnings")
        runCatching { execute(request).lineSequence().map { it.trim() }.firstOrNull { it.isNotBlank() } }.getOrNull()
    }

    /**
     * Downloads the audio track with yt-dlp into [targetDirectory] and returns the finished file.
     * [format] is one of "best", "opus", "m4a" or "mp3".
     */
    suspend fun downloadTo(
        urlOrId: String,
        targetDirectory: File,
        baseName: String,
        format: String,
        embedMetadata: Boolean,
        embedThumbnail: Boolean,
        sponsorBlock: Boolean,
        onProgress: (Float) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val target = if (urlOrId.startsWith("http")) urlOrId else "https://www.youtube.com/watch?v=$urlOrId"
        targetDirectory.mkdirs()
        val output = File(targetDirectory, "${baseName.take(80)}.%(ext)s")

        val request = YoutubeDLRequest(target)
        request.addOption("-f", "bestaudio/best")
        request.addOption("--no-playlist")

        when (format.lowercase()) {
            "opus", "m4a", "mp3" -> {
                request.addOption("--extract-audio")
                request.addOption("--audio-format", format.lowercase())
            }
            else -> request.addOption("--extract-audio")
        }
        if (embedMetadata) request.addOption("--embed-metadata")
        if (embedThumbnail) {
            request.addOption("--embed-thumbnail")
            request.addOption("--convert-thumbnails", "jpg")
        }
        if (sponsorBlock) request.addOption("--sponsorblock-remove", "sponsor")
        request.addOption("-o", output.absolutePath)
        request.addOption("--newline")
        request.applyCommonOptions()

        // The callback signature differs between youtubedl-android versions; report indeterminate
        // progress in the repository and mark 100% once the file exists.
        YoutubeDL.getInstance().execute(request)

        onProgress(1f)
        // yt-dlp may sanitize the base name, so take the newest real media file in the temp folder.
        targetDirectory.listFiles()
            ?.filter { it.isFile }
            ?.filter { it.extension.lowercase() in setOf("m4a", "opus", "mp3", "webm", "ogg", "aac", "flac", "wav") }
            ?.maxByOrNull { it.lastModified() }
            ?: throw java.io.IOException("Download produced no file")
    }

    private fun parseJsonLines(out: String): List<Entry> {
        val results = mutableListOf<Entry>()
        out.lineSequence().forEach { line ->
            val s = line.trim()
            if (!s.startsWith("{")) return@forEach
            runCatching { JSONObject(s) }.getOrNull()?.let { obj ->
                entryFromJson(obj)?.let { results.add(it) }
            }
        }
        return results
    }

    private fun parseSingleInfoJson(out: String): Entry? {
        val s = out.trim()
        if (!s.startsWith("{")) return null
        return runCatching { JSONObject(s) }.getOrNull()?.let { entryFromJson(it) }
    }

    private fun entryFromJson(obj: JSONObject): Entry? {
        val id = obj.optString("id").ifBlank { return null }
        val title = obj.optString("title").ifBlank { obj.optString("fulltitle") }.ifBlank { return null }
        if (title == "[Private video]" || title == "[Deleted video]") return null
        val webpage = obj.optString("webpage_url").ifBlank { obj.optString("url").takeIf { it.startsWith("http") } }
        val url = webpage ?: "https://www.youtube.com/watch?v=$id"
        val artist = sequenceOf("artist", "uploader", "channel", "uploader_id", "creator")
            .map { obj.optString(it) }.firstOrNull { it.isNotBlank() }?.removeSuffix(" - Topic") ?: "Unknown"
        val dur = when {
            obj.has("duration") && !obj.isNull("duration") -> obj.optDouble("duration", -1.0).takeIf { it > 0 }?.toLong()
            else -> null
        }
        val thumb = obj.optString("thumbnail").ifBlank {
            val thumbs = obj.optJSONArray("thumbnails")
            if (thumbs != null && thumbs.length() > 0) thumbs.optJSONObject(thumbs.length() - 1)?.optString("url") else null
        }?.ifBlank { null } ?: "https://i.ytimg.com/vi/$id/hqdefault.jpg"
        return Entry(
            id = id,
            url = url,
            title = title,
            artist = artist,
            durationSec = dur,
            thumbnail = thumb,
            webpageUrl = webpage,
        )
    }
}