package app.wavelet.io

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import app.wavelet.source.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.coroutineContext

/** Source id for songs that come from a folder the user picked. */
const val FOLDER_SOURCE_ID = "folder"

/**
 * Lists the audio files in a folder the user picked with the system folder picker and reads
 * their tags. Uses [DocumentsContract] directly, which Android's docs recommend over
 * DocumentFile for speed on large folders.
 */
class FolderScanner(private val context: Context) {
    private val audioExtensions = setOf("mp3", "m4a", "aac", "flac", "ogg", "oga", "opus", "wav", "wma", "alac", "aiff", "mka")
    private val artDir = File(context.cacheDir, "folder-art").apply { mkdirs() }

    private data class Entry(val docId: String, val name: String, val mime: String)

    /** Folder name as the system shows it, for naming the playlist. */
    fun folderName(treeUri: Uri): String {
        val docId = DocumentsContract.getTreeDocumentId(treeUri)
        val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
        context.contentResolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) return it.getString(0)
        }
        return docId.substringAfterLast(':').substringAfterLast('/').ifBlank { "Music folder" }
    }

    /** Finds every audio file under [treeUri] (sub-folders included) and returns them as tracks. */
    suspend fun scan(treeUri: Uri, onProgress: (found: Int) -> Unit = {}): List<Track> = withContext(Dispatchers.IO) {
        val files = mutableListOf<Entry>()
        val pending = ArrayDeque<String>()
        pending.add(DocumentsContract.getTreeDocumentId(treeUri))
        while (pending.isNotEmpty()) {
            coroutineContext.ensureActive()
            val parent = pending.removeFirst()
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parent)
            runCatching {
                context.contentResolver.query(
                    children,
                    arrayOf(
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        DocumentsContract.Document.COLUMN_MIME_TYPE,
                    ),
                    null, null, null,
                )?.use { c ->
                    while (c.moveToNext()) {
                        val id = c.getString(0) ?: continue
                        val name = c.getString(1) ?: continue
                        val mime = c.getString(2).orEmpty()
                        when {
                            mime == DocumentsContract.Document.MIME_TYPE_DIR -> pending.add(id)
                            mime.startsWith("audio/") || name.substringAfterLast('.', "").lowercase() in audioExtensions ->
                                files += Entry(id, name, mime)
                        }
                    }
                }
            }
        }
        files.sortWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

        val tracks = ArrayList<Track>(files.size)
        files.forEachIndexed { i, e ->
            coroutineContext.ensureActive()
            tracks += readTrack(treeUri, e)
            onProgress(i + 1)
        }
        tracks
    }

    private fun readTrack(treeUri: Uri, e: Entry): Track {
        val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, e.docId)
        var title: String? = null
        var artist: String? = null
        var album: String? = null
        var duration: Long? = null
        var artwork: String? = null
        val retriever = MediaMetadataRetriever()
        runCatching {
            retriever.setDataSource(context, uri)
            title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
            album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            retriever.embeddedPicture?.let { bytes ->
                // One cover file per album, so a big album doesn't write the same image many times.
                val key = ((album ?: "") + "|" + (artist ?: "")).hashCode().toUInt().toString(16)
                val f = File(artDir, "$key.jpg")
                if (!f.exists()) f.writeBytes(bytes)
                artwork = Uri.fromFile(f).toString()
            }
        }
        runCatching { retriever.release() }

        // Files without tags: use "Artist - Title" from the file name.
        val stem = e.name.substringBeforeLast('.')
        val (nameArtist, nameTitle) = if (stem.contains(" - ")) stem.substringBefore(" - ").trim() to stem.substringAfter(" - ").trim()
        else null to stem
        return Track(
            id = uri.toString(),
            sourceId = FOLDER_SOURCE_ID,
            title = title?.takeIf { it.isNotBlank() } ?: nameTitle,
            artist = artist?.takeIf { it.isNotBlank() } ?: nameArtist ?: "Unknown artist",
            album = album?.takeIf { it.isNotBlank() },
            durationMs = duration,
            artworkUrl = artwork,
            streamUrl = uri.toString(),
        )
    }
}
