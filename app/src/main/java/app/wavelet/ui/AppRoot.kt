package app.wavelet.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.wavelet.player.PlayerUiState
import app.wavelet.source.Track

object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val LIBRARY = "library"
    const val SETTINGS = "settings"
    const val SOURCES = "sources"
    const val LIKED = "liked"
    const val DOWNLOADS = "downloads"
    const val DEVICE = "device"
    const val HISTORY = "history"
    const val STATS = "stats"
    const val IMPORT = "import"
    fun playlist(id: Long) = "playlist/$id"
    fun album(name: String) = "album/${Uri.encode(name)}"
    fun artist(name: String) = "artist/${Uri.encode(name)}"
}

private data class TopTab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    TopTab(Routes.HOME, "Home", Icons.Rounded.Home),
    TopTab(Routes.SEARCH, "Search", Icons.Rounded.Search),
    TopTab(Routes.LIBRARY, "Your Library", Icons.Rounded.LibraryMusic),
)

@Composable
fun AppRoot(vm: MainViewModel, onRequestPermission: () -> Unit) {
    val nav = rememberNavController()
    var nowPlayingOpen by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val player by vm.player.collectAsStateWithLifecycle()
    val current by vm.currentTrack.collectAsStateWithLifecycle()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route

    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }
    BackHandler(enabled = nowPlayingOpen) { nowPlayingOpen = false }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                Column {
                    current?.let { track -> MiniPlayer(vm, track, player, onOpen = { nowPlayingOpen = true }) }
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest, tonalElevation = 0.dp) {
                        tabs.forEach { t ->
                            val selected = route == t.route || (t.route == Routes.LIBRARY && route != null &&
                                tabs.none { it.route == route } && route != Routes.SEARCH)
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    nav.navigate(t.route) {
                                        popUpTo(Routes.HOME) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(t.icon, contentDescription = null) },
                                label = { Text(t.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    indicatorColor = Color.Transparent,
                                    selectedIconColor = MaterialTheme.colorScheme.onSurface,
                                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                            )
                        }
                    }
                }
            },
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                AppNavHost(vm, nav, onRequestPermission)
            }
        }

        AnimatedVisibility(
            visible = nowPlayingOpen && current != null,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
        ) {
            current?.let {
                NowPlayingScreen(
                    vm, it, player,
                    onClose = { nowPlayingOpen = false },
                    onOpenArtist = { name ->
                        nowPlayingOpen = false
                        nav.navigate(Routes.artist(name))
                    },
                )
            }
        }

        GlobalSheets(vm, nav)
    }
}

@Composable
private fun AppNavHost(vm: MainViewModel, nav: NavHostController, onRequestPermission: () -> Unit) {
    NavHost(nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) { HomeScreen(vm, nav) }
        composable(Routes.SEARCH) { SearchScreen(vm, nav) }
        composable(Routes.LIBRARY) { LibraryScreen(vm, nav, onRequestPermission) }
        composable(Routes.SETTINGS) { SettingsScreen(vm, nav) }
        composable(Routes.SOURCES) { SourcesScreen(vm, onBack = { nav.popBackStack() }) }
        composable(Routes.LIKED) { LikedScreen(vm, nav) }
        composable(Routes.DOWNLOADS) { DownloadsScreen(vm, nav) }
        composable(Routes.DEVICE) { DeviceSongsScreen(vm, nav, onRequestPermission) }
        composable(Routes.HISTORY) { HistoryScreen(vm, nav) }
        composable(Routes.STATS) { StatsScreen(vm, nav) }
        composable(Routes.IMPORT) { ImportScreen(vm, nav) }
        composable("playlist/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
            PlaylistScreen(vm, nav, entry.arguments?.getLong("id") ?: 0L)
        }
        composable("album/{name}") { entry ->
            AlbumScreen(vm, nav, Uri.decode(entry.arguments?.getString("name").orEmpty()))
        }
        composable("artist/{name}") { entry ->
            ArtistScreen(vm, nav, Uri.decode(entry.arguments?.getString("name").orEmpty()))
        }
    }
}

@Composable
private fun MiniPlayer(vm: MainViewModel, track: Track, player: PlayerUiState, onOpen: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val liked by vm.likedKeys.collectAsStateWithLifecycle()
    val tint = if (settings.artworkColors) rememberArtworkColor(track.artworkUrl) else null
    val base = MaterialTheme.colorScheme.surfaceContainerHigh
    val bg by animateColorAsState(tint?.copy(alpha = 0.85f)?.compositeOver(Color.Black) ?: base, label = "miniBg")
    val onBg = if (tint != null) Color.White else MaterialTheme.colorScheme.onSurface
    val isLiked = track.key in liked

    Column(
        Modifier
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .clickable(onClick = onOpen),
    ) {
        Row(Modifier.fillMaxWidth().padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Artwork(track.artworkUrl, Modifier.size(42.dp), live = track.isLive, corner = 4.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall, color = onBg)
                Text(track.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = onBg.copy(alpha = 0.75f))
            }
            if (!track.isLive) {
                IconButton(onClick = { vm.toggleLike(track) }) {
                    Icon(
                        if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = if (isLiked) "Remove from Liked Songs" else "Add to Liked Songs",
                        tint = if (isLiked) MaterialTheme.colorScheme.primary else onBg,
                    )
                }
            }
            IconButton(onClick = vm::togglePlay) {
                Icon(
                    if (player.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (player.isPlaying) "Pause" else "Play",
                    tint = onBg,
                    modifier = Modifier.size(30.dp),
                )
            }
        }
        if (player.durationMs > 0) {
            LinearProgressIndicator(
                progress = { (player.positionMs.toFloat() / player.durationMs).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(2.dp).padding(horizontal = 8.dp),
                color = onBg,
                trackColor = onBg.copy(alpha = 0.2f),
                drawStopIndicator = {},
            )
        } else {
            Spacer(Modifier.height(2.dp))
        }
    }
}
