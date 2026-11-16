package app.wavelet.player

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import android.util.Log

data class BandInfo(
    val index: Short,
    val centerFreqHz: Int,
    val minMillibels: Short,
    val maxMillibels: Short,
    val levelMillibels: Short,
)

data class AudioFxState(
    val available: Boolean = false,
    val enabled: Boolean = false,
    val bands: List<BandInfo> = emptyList(),
    val presets: List<String> = emptyList(),
    val currentPreset: Short = -1,
    val bassBoost: Short = 0,
    val virtualizer: Short = 0,
)

/**
 * In-app audio effects controller managing Android's Equalizer, BassBoost, and Virtualizer.
 * Attaches directly to the ExoPlayer audioSessionId.
 */
class AudioFxController {
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var currentSessionId: Int = 0

    var onStateChanged: ((AudioFxState) -> Unit)? = null

    fun attach(sessionId: Int, initialEnabled: Boolean = false) {
        if (sessionId <= 0 || sessionId == currentSessionId) return
        release()
        currentSessionId = sessionId
        runCatching {
            equalizer = Equalizer(0, sessionId).apply {
                enabled = initialEnabled
            }
            bassBoost = BassBoost(0, sessionId).apply {
                enabled = initialEnabled
            }
            virtualizer = Virtualizer(0, sessionId).apply {
                enabled = initialEnabled
            }
        }.onFailure {
            Log.w("Wavelet", "Could not initialize AudioFX for session $sessionId", it)
        }
        notifyState()
    }

    fun release() {
        runCatching { equalizer?.release() }
        runCatching { bassBoost?.release() }
        runCatching { virtualizer?.release() }
        equalizer = null
        bassBoost = null
        virtualizer = null
        currentSessionId = 0
    }

    fun setEnabled(enabled: Boolean) {
        runCatching { equalizer?.enabled = enabled }
        runCatching { bassBoost?.enabled = enabled }
        runCatching { virtualizer?.enabled = enabled }
        notifyState()
    }

    fun setBandLevel(band: Short, levelMillibels: Short) {
        val eq = equalizer ?: return
        runCatching {
            val range = eq.bandLevelRange
            val clamped = levelMillibels.coerceIn(range[0], range[1])
            eq.setBandLevel(band, clamped)
        }
        notifyState()
    }

    fun usePreset(preset: Short) {
        val eq = equalizer ?: return
        runCatching {
            if (preset in 0 until eq.numberOfPresets) {
                eq.usePreset(preset)
            }
        }
        notifyState()
    }

    fun setBassBoost(strength: Short) {
        val bb = bassBoost ?: return
        runCatching {
            bb.setStrength(strength.coerceIn(0, 1000))
        }
        notifyState()
    }

    fun setVirtualizer(strength: Short) {
        val vz = virtualizer ?: return
        runCatching {
            vz.setStrength(strength.coerceIn(0, 1000))
        }
        notifyState()
    }

    fun getState(): AudioFxState {
        val eq = equalizer ?: return AudioFxState(available = false)
        return runCatching {
            val range = eq.bandLevelRange
            val numBands = eq.numberOfBands
            val bands = (0 until numBands).map { b ->
                val band = b.toShort()
                BandInfo(
                    index = band,
                    centerFreqHz = eq.getCenterFreq(band) / 1000,
                    minMillibels = range[0],
                    maxMillibels = range[1],
                    levelMillibels = eq.getBandLevel(band),
                )
            }
            val numPresets = eq.numberOfPresets
            val presets = (0 until numPresets).map { p ->
                eq.getPresetName(p.toShort()) ?: "Preset $p"
            }
            val currentPreset = eq.currentPreset
            val bbStrength = bassBoost?.roundedStrength ?: 0
            val vzStrength = virtualizer?.roundedStrength ?: 0

            AudioFxState(
                available = true,
                enabled = eq.enabled,
                bands = bands,
                presets = presets,
                currentPreset = currentPreset,
                bassBoost = bbStrength,
                virtualizer = vzStrength,
            )
        }.getOrDefault(AudioFxState(available = false))
    }

    private fun notifyState() {
        onStateChanged?.invoke(getState())
    }
}
