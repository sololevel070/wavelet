package app.wavelet.player

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.media3.common.Player
import app.wavelet.source.LyricLine
import app.wavelet.source.Lyrics
import app.wavelet.source.indexAt
import app.wavelet.ui.MainActivity
import kotlin.math.abs

/**
 * A small draggable bubble, drawn over other apps, that shows the current and next line of
 * synced lyrics. Built with plain Views because it lives in a Service (no Activity/Compose host).
 */
class LyricsOverlay(private val context: Context, private val onClose: () -> Unit) {
    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val prefs = context.getSharedPreferences("lyrics_overlay", Context.MODE_PRIVATE)
    private var root: FrameLayout? = null
    private lateinit var current: TextView
    private lateinit var next: TextView
    private lateinit var title: TextView
    private var params: WindowManager.LayoutParams? = null
    private var lines: List<LyricLine> = emptyList()
    private var plainFallback: String? = null
    private var lastIndex = -2
    private var size = 1
    private var label = ""

    val isShowing get() = root != null

    fun canShow() = Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(context)

    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, context.resources.displayMetrics)

    fun show(textSize: Int) {
        if (root != null) {
            if (textSize != size) { size = textSize; applySize() }
            return
        }
        if (!canShow()) return
        size = textSize
        val pad = dp(14f).toInt()
        val container = FrameLayout(context)
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, dp(10f).toInt(), pad, dp(12f).toInt())
            background = GradientDrawable().apply {
                cornerRadius = dp(18f)
                setColor(Color.argb(215, 18, 18, 24))
                setStroke(dp(1f).toInt(), Color.argb(60, 255, 255, 255))
            }
        }
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        title = TextView(context).apply {
            setTextColor(Color.argb(170, 200, 190, 255))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val close = TextView(context).apply {
            text = "✕"
            setTextColor(Color.argb(200, 255, 255, 255))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(dp(10f).toInt(), 0, 0, 0)
            contentDescription = "Close floating lyrics"
            setOnClickListener { onClose() }
        }
        header.addView(title)
        header.addView(close)
        current = TextView(context).apply {
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 3
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, dp(4f).toInt(), 0, 0)
        }
        next = TextView(context).apply {
            setTextColor(Color.argb(150, 255, 255, 255))
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, dp(2f).toInt(), 0, 0)
        }
        column.addView(header)
        column.addView(current)
        column.addView(next)
        val maxWidth = (context.resources.displayMetrics.widthPixels * 0.88f).toInt()
        container.addView(column, FrameLayout.LayoutParams(maxWidth, FrameLayout.LayoutParams.WRAP_CONTENT))
        applySize()
        title.text = label

        val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.getInt("x", ((context.resources.displayMetrics.widthPixels - maxWidth) / 2))
            y = prefs.getInt("y", (context.resources.displayMetrics.heightPixels * 0.62f).toInt())
        }
        enableDrag(column, lp)
        runCatching { wm.addView(container, lp) }.onSuccess {
            root = container
            params = lp
            render(force = true, positionMs = 0)
        }
    }

    private fun applySize() {
        if (root == null && !::current.isInitialized) return
        val (big, small) = when (size) {
            0 -> 15f to 12f
            2 -> 23f to 16f
            else -> 18f to 14f
        }
        current.setTextSize(TypedValue.COMPLEX_UNIT_SP, big)
        next.setTextSize(TypedValue.COMPLEX_UNIT_SP, small)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun enableDrag(view: View, lp: WindowManager.LayoutParams) {
        val slop = ViewConfiguration.get(context).scaledTouchSlop
        var startX = 0f
        var startY = 0f
        var originX = 0
        var originY = 0
        var dragging = false
        view.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = e.rawX; startY = e.rawY; originX = lp.x; originY = lp.y; dragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - startX
                    val dy = e.rawY - startY
                    if (!dragging && (abs(dx) > slop || abs(dy) > slop)) dragging = true
                    if (dragging) {
                        lp.x = (originX + dx).toInt()
                        lp.y = (originY + dy).toInt()
                        root?.let { runCatching { wm.updateViewLayout(it, lp) } }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (dragging) {
                        prefs.edit().putInt("x", lp.x).putInt("y", lp.y).apply()
                    } else {
                        // A tap opens Wavelet.
                        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                    }
                    true
                }
                else -> false
            }
        }
    }

    fun setTrack(label: String, lyrics: Lyrics?, loading: Boolean) {
        this.label = label
        lines = lyrics?.synced.orEmpty()
        plainFallback = when {
            loading -> "Loading lyrics…"
            lyrics == null -> "No lyrics for this song"
            lines.isEmpty() -> "Only unsynced lyrics are available"
            else -> null
        }
        if (root == null) return
        title.text = label
        lastIndex = -2
        render(force = true, positionMs = 0)
    }

    fun update(player: Player) = render(force = false, positionMs = player.currentPosition)

    private fun render(force: Boolean, positionMs: Long) {
        if (root == null) return
        val fallback = plainFallback
        if (fallback != null) {
            if (force) {
                current.text = fallback
                next.text = ""
            }
            return
        }
        val i = lines.indexAt(positionMs)
        if (!force && i == lastIndex) return
        lastIndex = i
        current.text = lines.getOrNull(i)?.text?.ifBlank { "♪" } ?: "♪"
        next.text = lines.getOrNull(i + 1)?.text.orEmpty()
    }

    fun hide() {
        root?.let { runCatching { wm.removeView(it) } }
        root = null
        params = null
    }
}
