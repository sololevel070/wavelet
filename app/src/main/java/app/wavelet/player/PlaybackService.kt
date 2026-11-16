package app.wavelet.player

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.BitmapDrawable
import android.media.audiofx.AudioEffect
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import app.wavelet.WaveletApp
import app.wavelet.data.Settings
import app.wavelet.net.Http
import app.wavelet.player.QueueStore.toMediaItem
import app.wavelet.source.Track
import app.wavelet.ui.MainActivity
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Background playback. Owns the ExoPlayer and exposes it through a MediaSession (notification,
 * lock screen, headset and widget controls). Also drives the home-screen widget, the floating
 * lyrics bubble, and saves the queue so playback can resume later.
 */
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val container get() = (application as WaveletApp).container
    private lateinit var overlay: LyricsOverlay
    private var overlayWanted = false
    private var overlaySize = 1
    private var lyricsJob: Job? = null
    private var lyricsKey: String? = null
    private var artworkUri: String? = null
    private var artworkBitmap: android.graphics.Bitmap? = null

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(Http.USER_AGENT)
            .setAllowCrossProtocolRedirects(true) // many radio streams redirect http <-> https
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(this, http)))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, player)
            .setSessionActivity(openApp)
            .setCallback(object : MediaSession.Callback {
                // Controllers send items with the URI in requestMetadata; restore it here.
                override fun onAddMediaItems(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    mediaItems: MutableList<MediaItem>,
                ): ListenableFuture<MutableList<MediaItem>> = Futures.immediateFuture(
                    mediaItems.map { item ->
                        val uri = item.requestMetadata.mediaUri ?: item.localConfiguration?.uri
                        item.buildUpon().setUri(uri).build()
                    }.toMutableList(),
                )

                // Play from the widget, a headset or Bluetooth after the app was closed.
                override fun onPlaybackResumption(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo,
                ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
                    val q = QueueStore.load(this@PlaybackService)
                        ?: return Futures.immediateFailedFuture(UnsupportedOperationException("Nothing to resume"))
                    return Futures.immediateFuture(
                        MediaSession.MediaItemsWithStartPosition(q.items.map { it.toMediaItem() }, q.index, q.positionMs),
                    )
                }
            })
            .setSessionExtras(Bundle().apply { putInt(EXTRA_AUDIO_SESSION, player.audioSessionId) })
            .build()

        // Let system/third-party equalizers attach to our audio session.
        sendBroadcast(Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION).apply {
            putExtra(AudioEffect.EXTRA_AUDIO_SESSION, player.audioSessionId)
            putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
            putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
        })

        overlay = LyricsOverlay(this, onClose = {
            container.updateState { it.copy(settings = it.settings.copy(floatingLyrics = false)) }
        })

        player.addListener(object : Player.Listener {
            override fun onEvents(p: Player, events: Player.Events) {
                if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) {
                    p.volume = 1f
                }
                if (events.containsAny(Player.EVENT_TIMELINE_CHANGED, Player.EVENT_MEDIA_ITEM_TRANSITION)) {
                    QueueStore.save(this@PlaybackService, p)
                }
                if (events.contains(Player.EVENT_IS_PLAYING_CHANGED) && !p.isPlaying) QueueStore.save(this@PlaybackService, p)
                if (events.containsAny(
                        Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_MEDIA_METADATA_CHANGED,
                        Player.EVENT_IS_PLAYING_CHANGED, Player.EVENT_TIMELINE_CHANGED,
                    )
                ) {
                    updateWidget(p)
                    applyOverlay()
                }
            }
        })

        // Show or hide the floating lyrics as settings and app visibility change.
        scope.launch {
            combine(container.store.state.map { it.settings }, container.appVisible) { s: Settings, visible: Boolean ->
                Triple(s.floatingLyrics && !(s.floatingLyricsHideInApp && visible), s.floatingLyricsSize, Unit)
            }.distinctUntilChanged().collect { (wanted, size, _) ->
                overlayWanted = wanted
                overlaySize = size
                applyOverlay()
            }
        }

        // Ticker: move the lyrics along, apply crossfade, and save the position now and then.
        scope.launch {
            var ticks = 0
            while (isActive) {
                delay(250)
                val p = session?.player ?: continue
                if (overlay.isShowing && p.isPlaying) overlay.update(p)
                if (p.isPlaying) {
                    val crossfade = container.store.state.value.settings.crossfadeSeconds
                    if (crossfade > 0 && p.duration > 0 && p.hasNextMediaItem()) {
                        val remaining = p.duration - p.currentPosition
                        val fadeMs = crossfade * 1000L
                        if (remaining in 0..fadeMs) {
                            val factor = (remaining.toFloat() / fadeMs).coerceIn(0.15f, 1f)
                            p.volume = factor
                        } else if (p.volume < 1f) {
                            p.volume = 1f
                        }
                    }
                    if (++ticks % 60 == 0) QueueStore.save(this@PlaybackService, p)
                }
            }
        }
    }

    private fun applyOverlay() {
        val p = session?.player ?: return
        val show = overlayWanted && p.mediaItemCount > 0 && overlay.canShow()
        if (!show) {
            overlay.hide()
            return
        }
        overlay.show(overlaySize)
        val item = p.currentMediaItem ?: return
        if (item.mediaId == lyricsKey) return
        lyricsKey = item.mediaId
        val md = item.mediaMetadata
        val label = listOfNotNull(md.title?.toString(), md.artist?.toString()?.ifBlank { null }).joinToString(" · ")
        overlay.setTrack(label, null, loading = true)
        lyricsJob?.cancel()
        lyricsJob = scope.launch {
            val key = item.mediaId
            val track = container.library.track(key) ?: Track(
                id = key.substringAfter(':'), sourceId = key.substringBefore(':'),
                title = md.title?.toString().orEmpty(), artist = md.artist?.toString().orEmpty(),
                album = md.albumTitle?.toString(), isLive = key.startsWith("radio_browser:"),
            )
            val lyrics = container.lyrics.get(track)
            if (lyricsKey == key) overlay.setTrack(label, lyrics, loading = false)
        }
    }

    private fun updateWidget(p: Player) {
        if (!WidgetUpdater.hasWidgets(this)) return
        val md = p.mediaMetadata
        val title = md.title?.toString() ?: "Wavelet"
        val artist = md.artist?.toString() ?: if (p.mediaItemCount == 0) "Tap play to resume" else ""
        val uri = md.artworkUri?.toString()
        val cached = if (uri == artworkUri) artworkBitmap else null
        WidgetUpdater.push(this, WidgetState(title, artist, p.isPlaying, cached))
        if (uri != null && uri != artworkUri) {
            artworkUri = uri
            artworkBitmap = null
            scope.launch {
                val result = imageLoader.execute(
                    ImageRequest.Builder(this@PlaybackService).data(uri).size(256).allowHardware(false).build(),
                )
                val bmp = ((result as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap
                if (artworkUri == uri && bmp != null) {
                    artworkBitmap = bmp
                    val cur = session?.player ?: return@launch
                    WidgetUpdater.push(this@PlaybackService, WidgetState(title, artist, cur.isPlaying, bmp))
                }
            }
        } else if (uri == null) {
            artworkUri = null
            artworkBitmap = null
        }
    }

    companion object {
        const val EXTRA_AUDIO_SESSION = "audioSessionId"
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    @OptIn(UnstableApi::class)
    override fun onDestroy() {
        session?.player?.let { p ->
            QueueStore.save(this, p)
            sendBroadcast(Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION).apply {
                putExtra(AudioEffect.EXTRA_AUDIO_SESSION, (p as? ExoPlayer)?.audioSessionId ?: 0)
                putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
            })
            WidgetUpdater.push(this, WidgetState(p.mediaMetadata.title?.toString() ?: "Wavelet", p.mediaMetadata.artist?.toString().orEmpty(), false, artworkBitmap))
        }
        overlay.hide()
        scope.cancel()
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }
}
