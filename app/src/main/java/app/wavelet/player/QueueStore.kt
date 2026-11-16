package app.wavelet.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import app.wavelet.net.Http
import kotlinx.serialization.Serializable
import java.io.File

@Serializable
data class SavedItem(
    val key: String,
    val uri: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val artwork: String? = null,
)

@Serializable
data class SavedQueue(val items: List<SavedItem>, val index: Int, val positionMs: Long)

/** Saves the player's queue so playback can resume after the app is closed. */
object QueueStore {
    private fun file(context: Context) = File(context.filesDir, "queue.json")

    fun save(context: Context, player: Player) {
        val items = (0 until player.mediaItemCount).mapNotNull { i ->
            val item = player.getMediaItemAt(i)
            val uri = item.localConfiguration?.uri ?: item.requestMetadata.mediaUri ?: return@mapNotNull null
            val md = item.mediaMetadata
            SavedItem(
                item.mediaId, uri.toString(), md.title?.toString().orEmpty(), md.artist?.toString().orEmpty(),
                md.albumTitle?.toString(), md.artworkUri?.toString(),
            )
        }
        if (items.isEmpty()) return
        val q = SavedQueue(items, player.currentMediaItemIndex.coerceIn(items.indices), player.currentPosition.coerceAtLeast(0))
        runCatching { file(context).writeText(Http.json.encodeToString(SavedQueue.serializer(), q)) }
    }

    fun load(context: Context): SavedQueue? = runCatching {
        Http.json.decodeFromString(SavedQueue.serializer(), file(context).readText())
    }.getOrNull()?.takeIf { it.items.isNotEmpty() }

    fun SavedItem.toMediaItem(): MediaItem {
        val u = Uri.parse(uri)
        return MediaItem.Builder()
            .setMediaId(key)
            .setUri(u)
            .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(u).build())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title).setArtist(artist).setAlbumTitle(album)
                    .setArtworkUri(artwork?.let(Uri::parse)).setIsPlayable(true)
                    .build(),
            )
            .build()
    }
}
