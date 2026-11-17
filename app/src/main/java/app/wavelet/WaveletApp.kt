package app.wavelet

import android.app.Application
import app.wavelet.data.AppState
import app.wavelet.data.AppStore
import app.wavelet.data.LibraryRepository
import app.wavelet.data.db.WaveletDatabase
import app.wavelet.download.DownloadRepository
import app.wavelet.io.TrackMatcher
import app.wavelet.source.LyricsRepository
import app.wavelet.source.RepoManager
import app.wavelet.source.SourceRegistry
import app.wavelet.ytdlp.YtDlp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class WaveletApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Simple manual dependency container shared by the whole app. */
class AppContainer(app: Application) {
    /** Scope for work that should outlive a screen, such as downloads and imports. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val store = AppStore(app)
    val downloads = DownloadRepository(app) { store.state.value.settings }
    val sources = SourceRegistry(app, store, downloads)

    init {
        // Warm up yt-dlp and apply the user's cookie/argument settings.
        appScope.launch(Dispatchers.IO) {
            val settings = store.state.value.settings
            YtDlp.configure(app, settings.ytCookies, settings.ytExtraArgs)
            YtDlp.ensureInitialized(app)
        }
    }
    val repos = RepoManager()
    val database = WaveletDatabase.create(app)
    val library = LibraryRepository(database.dao())
    val matcher = TrackMatcher { sources.music() }
    val lyrics = LyricsRepository { sources.lyrics() }

    /** True while a Wavelet screen is on display (used to hide the floating lyrics). */
    val appVisible = MutableStateFlow(false)

    /** Changes saved settings and rebuilds the active sources from them. */
    fun updateState(transform: (AppState) -> AppState) {
        store.update(transform)
        sources.rebuild()
    }
}
