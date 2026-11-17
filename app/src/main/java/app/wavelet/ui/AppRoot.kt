package app.wavelet.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.launch
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.Surface
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
    const val ABOUT = "about"
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
private val tabRoutes = tabs.map { it.route }.toSet()

// Motion. Material's own docs give 300 ms and the FastOutSlowIn curve (cubic-bezier 0.4, 0, 0.2, 1)
// for shared-axis and fade-through transitions, and name "tapping a bottom navigation destination"
// as the fade-through use case.
private const val MOTION_MS = 300
private val MotionEasing = FastOutSlowInEasing

@Composable
fun AppRoot(vm: MainViewModel, onRequestPermission: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    // Surface sets the default text colour for everything below it (without it, text is black).
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
    AnimatedContent(
        targetState = settings.onboarded,
        transitionSpec = {
            (fadeIn(tween(MOTION_MS, easing = MotionEasing)) + scaleIn(tween(MOTION_MS, easing = MotionEasing), initialScale = 0.96f)) togetherWith
                fadeOut(tween(150, easing = LinearEasing))
        },
        label = "onboarding",
    ) { done ->
        if (done) MainShell(vm, onRequestPermission) else OnboardingScreen(vm, onRequestPermission)
    }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun MainShell(vm: MainViewModel, onRequestPermission: () -> Unit) {
    val nav = rememberNavController()
    var nowPlayingOpen by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val player by vm.player.collectAsStateWithLifecycle()
    val current by vm.currentTrack.collectAsStateWithLifecycle()
    val starting by vm.starting.collectAsStateWithLifecycle()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route

    // The tab to highlight. Screens opened from a tab (Settings, a playlist...) keep that tab lit.
    var activeTab by rememberSaveable { mutableStateOf(Routes.HOME) }
    LaunchedEffect(route) { if (route != null && route in tabRoutes) activeTab = route }

    // While a tapped song is still loading, show it straight away.
    val shown = starting ?: current
    val loading = starting != null || (player.isBuffering && !player.isPlaying)
    var lastShown by remember { mutableStateOf<Track?>(null) }
    if (shown != null) lastShown = shown

    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }
    BackHandler(enabled = nowPlayingOpen) { nowPlayingOpen = false }

    val onTab: (String) -> Unit = { target ->
        when {
            // Already on this tab's main screen: nothing to do.
            route == target -> Unit
            // Home is the start of the back stack: go back to it.
            target == Routes.HOME -> nav.popBackStack(Routes.HOME, inclusive = false)
            // Other tabs always open on their main screen, never on a screen
            // (Settings, a playlist...) that was open before.
            else -> nav.navigate(target) {
                popUpTo(Routes.HOME) { saveState = false }
                launchSingleTop = true
                restoreState = false
            }
        }
    }

    // The cover art is one "shared element": it flies from the mini player into Now Playing and back.
    SharedTransitionLayout {
        BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            // Material's guidance: a navigation bar on phones, a navigation rail on wider windows.
            val wide = maxWidth >= 600.dp
            Scaffold(
                snackbarHost = { SnackbarHost(snackbar) },
                containerColor = MaterialTheme.colorScheme.background,
                // Each screen pads for the status bar itself; letting Scaffold add it too doubled the gap.
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                bottomBar = {
                    Column {
                        AnimatedVisibility(
                            visible = shown != null && !nowPlayingOpen,
                            enter = slideInVertically(tween(MOTION_MS, easing = MotionEasing)) { it } + fadeIn(tween(MOTION_MS, easing = MotionEasing)),
                            exit = slideOutVertically(tween(200, easing = MotionEasing)) { it } + fadeOut(tween(150)),
                        ) {
                            lastShown?.let { track ->
                                MiniPlayer(
                                    vm, track, player, loading,
                                    onOpen = { nowPlayingOpen = true },
                                    artModifier = Modifier.sharedElement(rememberSharedContentState("cover"), this@AnimatedVisibility),
                                )
                            }
                        }
                        if (!wide) {
                            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest, tonalElevation = 0.dp) {
                                tabs.forEach { t ->
                                    NavigationBarItem(
                                        selected = activeTab == t.route,
                                        onClick = { onTab(t.route) },
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
                    }
                },
            ) { padding ->
                Row(Modifier.padding(padding).fillMaxSize()) {
                    if (wide) {
                        NavigationRail(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest) {
                            Spacer(Modifier.height(12.dp))
                            tabs.forEach { t ->
                                NavigationRailItem(
                                    selected = activeTab == t.route,
                                    onClick = { onTab(t.route) },
                                    icon = { Icon(t.icon, contentDescription = null) },
                                    label = { Text(t.label) },
                                )
                            }
                        }
                    }
                    Box(Modifier.weight(1f).fillMaxSize()) {
                        AppNavHost(vm, nav, onRequestPermission)
                    }
                }
            }

            AnimatedVisibility(
                visible = nowPlayingOpen && shown != null,
                enter = slideInVertically(tween(MOTION_MS, easing = MotionEasing)) { it / 3 } + fadeIn(tween(MOTION_MS)),
                exit = slideOutVertically(tween(250, easing = MotionEasing)) { it / 3 } + fadeOut(tween(200)),
            ) {
                lastShown?.let {
                    NowPlayingScreen(
                        vm, it,
                        if (starting != null) player.copy(isBuffering = true, isPlaying = false) else player,
                        onClose = { nowPlayingOpen = false },
                        onOpenArtist = { name ->
                            nowPlayingOpen = false
                            nav.navigate(Routes.artist(name))
                        },
                        artModifier = Modifier.sharedElement(rememberSharedContentState("cover"), this@AnimatedVisibility),
                    )
                }
            }

            GlobalSheets(vm, nav)
        }
    }
}

private fun isTab(route: String?) = route != null && route in tabRoutes

@Composable
private fun AppNavHost(vm: MainViewModel, nav: NavHostController, onRequestPermission: () -> Unit) {
    val slideFraction = 8 // screens slide by 1/8 of the width, with a fade (shared-axis style)
    NavHost(
        nav,
        startDestination = Routes.HOME,
        // Switching tabs: fade through. Going deeper or back: shared axis along X.
        enterTransition = {
            if (isTab(initialState.destination.route) && isTab(targetState.destination.route)) {
                fadeIn(tween(210, delayMillis = 90, easing = MotionEasing)) +
                    scaleIn(tween(210, delayMillis = 90, easing = MotionEasing), initialScale = 0.92f)
            } else {
                slideInHorizontally(tween(MOTION_MS, easing = MotionEasing)) { it / slideFraction } +
                    fadeIn(tween(MOTION_MS, easing = MotionEasing))
            }
        },
        exitTransition = {
            if (isTab(initialState.destination.route) && isTab(targetState.destination.route)) {
                fadeOut(tween(90, easing = LinearEasing))
            } else {
                slideOutHorizontally(tween(MOTION_MS, easing = MotionEasing)) { -it / slideFraction } +
                    fadeOut(tween(150, easing = LinearEasing))
            }
        },
        popEnterTransition = {
            if (isTab(initialState.destination.route) && isTab(targetState.destination.route)) {
                fadeIn(tween(210, delayMillis = 90, easing = MotionEasing)) +
                    scaleIn(tween(210, delayMillis = 90, easing = MotionEasing), initialScale = 0.92f)
            } else {
                slideInHorizontally(tween(MOTION_MS, easing = MotionEasing)) { -it / slideFraction } +
                    fadeIn(tween(MOTION_MS, easing = MotionEasing))
            }
        },
        popExitTransition = {
            if (isTab(initialState.destination.route) && isTab(targetState.destination.route)) {
                fadeOut(tween(90, easing = LinearEasing))
            } else {
                slideOutHorizontally(tween(MOTION_MS, easing = MotionEasing)) { it / slideFraction } +
                    fadeOut(tween(150, easing = LinearEasing))
            }
        },
    ) {
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
        composable(Routes.ABOUT) { AboutScreen(vm, nav) }
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
private fun MiniPlayer(
    vm: MainViewModel,
    track: Track,
    player: PlayerUiState,
    loading: Boolean,
    onOpen: () -> Unit,
    artModifier: Modifier = Modifier,
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val liked by vm.likedKeys.collectAsStateWithLifecycle()
    val tint = if (settings.artworkColors) rememberArtworkColor(track.artworkUrl) else null
    val base = MaterialTheme.colorScheme.surfaceContainerHigh
    val bg by animateColorAsState(tint?.copy(alpha = 0.85f)?.compositeOver(Color.Black) ?: base, label = "miniBg")
    val onBg = if (tint != null) Color.White else MaterialTheme.colorScheme.onSurface
    val isLiked = track.key in liked
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val dragX = remember { Animatable(0f) }

    Column(
        Modifier
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .clickable(onClickLabel = "Open player", onClick = onOpen)
            // Swipe left for the next song, right for the previous one (like Spotify's mini player).
            .pointerInput(track.key) {
                val threshold = size.width * 0.25f
                detectHorizontalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            val x = dragX.value
                            when {
                                x < -threshold && player.hasNext -> { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); vm.next() }
                                x > threshold -> { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); vm.previousSong() }
                            }
                            dragX.animateTo(0f, tween(250, easing = MotionEasing))
                        }
                    },
                    onDragCancel = { scope.launch { dragX.animateTo(0f) } },
                ) { change, amount ->
                    change.consume()
                    scope.launch { dragX.snapTo(dragX.value + amount) }
                }
            }
            // Screen readers can't swipe the bar, so offer the same actions in their menu.
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction("Next song") { vm.next(); true },
                    CustomAccessibilityAction("Previous song") { vm.previousSong(); true },
                )
            },
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(6.dp)
                .graphicsLayer {
                    translationX = dragX.value
                    alpha = 1f - (kotlin.math.abs(dragX.value) / (size.width * 1.2f)).coerceIn(0f, 0.7f)
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Artwork(track.artworkUrl, artModifier.size(42.dp), live = track.isLive, corner = 4.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall, color = onBg)
                Text(
                    if (loading) "Loading…" else track.artist,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall, color = onBg.copy(alpha = 0.75f),
                )
            }
            if (!track.isLive && !loading) {
                LikeButton(isLiked, onToggle = { vm.toggleLike(track) }, idleTint = onBg)
            }
            IconButton(onClick = vm::togglePlay, enabled = !loading) {
                if (loading) {
                    CircularProgressIndicator(Modifier.size(24.dp), color = onBg, strokeWidth = 2.5.dp)
                } else {
                    Crossfade(player.isPlaying, animationSpec = tween(150), label = "miniPlay") { playing ->
                        Icon(
                            if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (playing) "Pause" else "Play",
                            tint = onBg,
                            modifier = Modifier.size(30.dp),
                        )
                    }
                }
            }
        }
        when {
            // Unknown length (loading, or a live station): a moving bar tells the user something is happening.
            loading -> LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(2.dp).padding(horizontal = 8.dp),
                color = onBg, trackColor = onBg.copy(alpha = 0.2f),
            )
            player.durationMs > 0 -> LinearProgressIndicator(
                progress = { (player.positionMs.toFloat() / player.durationMs).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(2.dp).padding(horizontal = 8.dp),
                color = onBg,
                trackColor = onBg.copy(alpha = 0.2f),
                drawStopIndicator = {},
            )
            else -> Spacer(Modifier.height(2.dp))
        }
    }
}
