package app.wavelet.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import app.wavelet.source.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class RepeatMode { Off, All, One }

data class PlayerUiState(
    val connected: Boolean = false,
    val currentKey: String? = null,
    val currentIndex: Int = -1,
    val title: String? = null,
    val artist: String? = null,
    val artworkUri: String? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    val shuffle: Boolean = false,
    val repeat: RepeatMode = RepeatMode.Off,
    val speed: Float = 1f,
)

data class QueueEntry(val index: Int, val key: String, val title: String, val artist: String, val artworkUri: String?)

/** Connects the UI to [PlaybackService] through a MediaController. Use on the main thread. */
class PlayerConnection(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onError: (String) -> Unit,
) {
    private var controller: MediaController? = null
    private var pending: Deferred<MediaController>? = null

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private val _queue = MutableStateFlow<List<QueueEntry>>(emptyList())
    val queue: StateFlow<List<QueueEntry>> = _queue.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            refresh()
            if (events.containsAny(Player.EVENT_TIMELINE_CHANGED, Player.EVENT_MEDIA_ITEM_TRANSITION)) refreshQueue()
        }

        override fun onPlayerError(error: PlaybackException) {
            onError("Playback error: ${error.errorCodeName.removePrefix("ERROR_CODE_").lowercase().replace('_', ' ')}")
        }
    }

    init {
        // Keep the position fresh for the seek bar and synced lyrics.
        scope.launch {
            while (isActive) {
                delay(400)
                if (controller?.isPlaying == true) refresh()
            }
        }
    }

    val now: MediaController? get() = controller

    /** Audio session of the player, published by the service for the system equalizer. */
    val audioSessionId: Int
        get() = controller?.sessionExtras?.getInt(PlaybackService.EXTRA_AUDIO_SESSION, 0) ?: 0

    suspend fun connect(): MediaController {
        controller?.let { return it }
        val job = pending ?: scope.async {
            val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
            MediaController.Builder(context, token).buildAsync().await().also {
                it.addListener(listener)
                controller = it
                refresh()
                refreshQueue()
            }
        }.also { pending = it }
        return job.await()
    }

    fun refresh() {
        val p = controller ?: return
        val md = p.mediaMetadata
        _state.value = PlayerUiState(
            connected = true,
            currentKey = p.currentMediaItem?.mediaId,
            currentIndex = p.currentMediaItemIndex,
            title = md.title?.toString(),
            artist = md.artist?.toString(),
            artworkUri = md.artworkUri?.toString(),
            isPlaying = p.isPlaying,
            isBuffering = p.playbackState == Player.STATE_BUFFERING,
            positionMs = p.currentPosition.coerceAtLeast(0),
            durationMs = p.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0,
            hasNext = p.hasNextMediaItem(),
            hasPrevious = p.hasPreviousMediaItem(),
            shuffle = p.shuffleModeEnabled,
            repeat = when (p.repeatMode) {
                Player.REPEAT_MODE_ALL -> RepeatMode.All
                Player.REPEAT_MODE_ONE -> RepeatMode.One
                else -> RepeatMode.Off
            },
            speed = p.playbackParameters.speed,
        )
    }

    fun refreshQueue() {
        val p = controller ?: return
        _queue.value = (0 until p.mediaItemCount).map { i ->
            val item = p.getMediaItemAt(i)
            QueueEntry(
                i, item.mediaId,
                item.mediaMetadata.title?.toString() ?: "Unknown title",
                item.mediaMetadata.artist?.toString().orEmpty(),
                item.mediaMetadata.artworkUri?.toString(),
            )
        }
    }

    fun setRepeat(mode: RepeatMode) {
        controller?.repeatMode = when (mode) {
            RepeatMode.Off -> Player.REPEAT_MODE_OFF
            RepeatMode.All -> Player.REPEAT_MODE_ALL
            RepeatMode.One -> Player.REPEAT_MODE_ONE
        }
    }

    fun release() {
        controller?.removeListener(listener)
        controller?.release()
        controller = null
        pending = null
    }
}

fun Track.toMediaItem(url: String): MediaItem {
    val uri = Uri.parse(url)
    return MediaItem.Builder()
        .setMediaId(key)
        .setUri(uri)
        .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri).build())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setArtworkUri(artworkUrl?.let(Uri::parse))
                .setIsPlayable(true)
                .build(),
        )
        .build()
}
