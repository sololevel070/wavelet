package app.wavelet.source

import android.content.Context
import android.util.Log
import app.wavelet.data.AppStore
import app.wavelet.download.DownloadRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Holds the live source instances built from the saved [app.wavelet.data.SourceConfig]s. */
class SourceRegistry(
    private val context: Context,
    private val store: AppStore,
    private val downloads: DownloadRepository,
) {
    private val _sources = MutableStateFlow<List<Source>>(emptyList())
    val sources: StateFlow<List<Source>> = _sources.asStateFlow()

    init {
        rebuild()
    }

    fun rebuild() {
        _sources.value = store.state.value.sources
            .filter { it.enabled && Engines.missingOptions(it).isEmpty() }
            .mapNotNull { cfg ->
                runCatching { Engines.create(cfg, context, downloads) }
                    .onFailure { Log.w("Wavelet", "Could not start source ${cfg.instanceId}", it) }
                    .getOrNull()
            }
    }

    fun get(id: String): Source? = _sources.value.firstOrNull { it.id == id }
    fun music(): List<MusicSource> = _sources.value.filterIsInstance<MusicSource>()
    fun lyrics(): List<LyricsSource> = _sources.value.filterIsInstance<LyricsSource>()
    fun links(): List<LinkSource> = _sources.value.filterIsInstance<LinkSource>()
}
