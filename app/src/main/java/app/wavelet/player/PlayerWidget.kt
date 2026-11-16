package app.wavelet.player

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.view.KeyEvent
import android.widget.RemoteViews
import androidx.media3.session.MediaButtonReceiver
import app.wavelet.R
import app.wavelet.ui.MainActivity

/** Home-screen widget: artwork, song, and previous / play-pause / next. */
class PlayerWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        // Show the last saved song until the player sends fresh details.
        val last = QueueStore.load(context)?.let { q -> q.items.getOrNull(q.index) }
        WidgetUpdater.push(
            context,
            WidgetState(title = last?.title ?: "Wavelet", artist = last?.artist ?: "Tap play to resume", playing = false, artwork = null),
        )
    }
}

data class WidgetState(val title: String, val artist: String, val playing: Boolean, val artwork: Bitmap?)

object WidgetUpdater {
    fun hasWidgets(context: Context): Boolean =
        AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, PlayerWidget::class.java)).isNotEmpty()

    fun push(context: Context, state: WidgetState) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, PlayerWidget::class.java))
        if (ids.isEmpty()) return
        val views = RemoteViews(context.packageName, R.layout.widget_player).apply {
            setTextViewText(R.id.widget_title, state.title)
            setTextViewText(R.id.widget_artist, state.artist)
            if (state.artwork != null) setImageViewBitmap(R.id.widget_art, state.artwork)
            else setImageViewResource(R.id.widget_art, R.drawable.ic_w_note)
            setImageViewResource(R.id.widget_play, if (state.playing) R.drawable.ic_w_pause else R.drawable.ic_w_play)
            setContentDescription(R.id.widget_play, if (state.playing) "Pause" else "Play")
            setOnClickPendingIntent(R.id.widget_play, mediaButton(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
            setOnClickPendingIntent(R.id.widget_next, mediaButton(context, KeyEvent.KEYCODE_MEDIA_NEXT))
            setOnClickPendingIntent(R.id.widget_prev, mediaButton(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS))
            setOnClickPendingIntent(
                R.id.widget_root,
                PendingIntent.getActivity(
                    context, 0,
                    Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
        }
        manager.updateAppWidget(ids, views)
    }

    /** Sends a media key to Media3's receiver, which starts/resumes the playback service. */
    private fun mediaButton(context: Context, keyCode: Int): PendingIntent {
        val intent = Intent(Intent.ACTION_MEDIA_BUTTON)
            .setComponent(ComponentName(context, MediaButtonReceiver::class.java))
            .putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        return PendingIntent.getBroadcast(context, keyCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}
