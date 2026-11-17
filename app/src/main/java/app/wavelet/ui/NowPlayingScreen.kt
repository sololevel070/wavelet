package app.wavelet.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.launch
import android.content.Intent
import android.media.audiofx.AudioEffect
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.wavelet.player.PlayerUiState
import app.wavelet.player.RepeatMode
import app.wavelet.source.LyricLine
import app.wavelet.source.Track

@Composable
fun NowPlayingScreen(
    vm: MainViewModel,
    track: Track,
    player: PlayerUiState,
    onClose: () -> Unit,
    onOpenArtist: (String) -> Unit,
    artModifier: Modifier = Modifier,
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val liked by vm.likedKeys.collectAsStateWithLifecycle()
    val lyricsState by vm.lyrics.collectAsStateWithLifecycle()
    val sleep by vm.sleep.collectAsStateWithLifecycle()
    var sheet by rememberSaveable { mutableStateOf<String?>(null) }
    var fullLyrics by rememberSaveable { mutableStateOf(false) }
    val isLiked = track.key in liked
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    val tint = if (settings.artworkColors) rememberArtworkColor(track.artworkUrl) else null
    val top by animateColorAsState(tint?.compositeOver(Color.Black) ?: Color(0xFF2E2766), label = "npTop")
    val textColor = Color.White
    val soft = Color.White.copy(alpha = 0.7f)

    // ---- Swipe down to close. Works with the scrolling content: once the page is scrolled to the
    // top, pulling further down drags the whole player; letting go past the threshold closes it.
    val dragY = remember { Animatable(0f) }
    val closeThreshold = with(density) { 140.dp.toPx() }
    val dismissConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // While the player is pulled down, an upward drag first pushes it back up.
                if (dragY.value > 0f && available.y < 0f && source == NestedScrollSource.UserInput) {
                    val used = maxOf(available.y, -dragY.value)
                    scope.launch { dragY.snapTo(dragY.value + used) }
                    return Offset(0f, used)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0f && source == NestedScrollSource.UserInput) {
                    scope.launch { dragY.snapTo(dragY.value + available.y) }
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (dragY.value <= 0f) return Velocity.Zero
                if (dragY.value > closeThreshold || available.y > 2500f) {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClose()
                } else {
                    dragY.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                }
                return available
            }
        }
    }

    // ---- Swipe the cover left/right to change song (like Spotify).
    val artDragX = remember { Animatable(0f) }
    val artSwipe = Modifier.pointerInput(track.key) {
        val threshold = size.width * 0.28f
        detectHorizontalDragGestures(
            onDragEnd = {
                scope.launch {
                    val x = artDragX.value
                    when {
                        x < -threshold && player.hasNext -> { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); vm.next() }
                        x > threshold -> { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); vm.previousSong() }
                    }
                    artDragX.animateTo(0f, tween(250, easing = FastOutSlowInEasing))
                }
            },
            onDragCancel = { scope.launch { artDragX.animateTo(0f) } },
        ) { change, amount ->
            change.consume()
            scope.launch { artDragX.snapTo(artDragX.value + amount) }
        }
    }

    val header: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Rounded.KeyboardArrowDown, "Close player", tint = textColor, modifier = Modifier.size(30.dp)) }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("PLAYING FROM", style = MaterialTheme.typography.labelSmall, color = soft)
                Text(vm.sourceName(track.sourceId), style = MaterialTheme.typography.labelLarge, color = textColor, maxLines = 1)
            }
            IconButton(onClick = { vm.showOptions(track) }) { Icon(Icons.Rounded.MoreVert, "More options", tint = textColor) }
        }
    }

    val art: @Composable (Modifier) -> Unit = { m ->
        Artwork(
            track.artworkUrl,
            artModifier
                .then(m)
                .aspectRatio(1f)
                .graphicsLayer {
                    translationX = artDragX.value
                    rotationZ = artDragX.value / 60f
                    alpha = 1f - (kotlin.math.abs(artDragX.value) / (size.width * 1.5f)).coerceIn(0f, 0.6f)
                }
                .then(artSwipe)
                .semantics { contentDescription = "Cover of ${track.title}. Swipe left for next song, right for previous." },
            live = track.isLive,
            corner = 8.dp,
        )
    }

    val details: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(track.title, style = MaterialTheme.typography.headlineSmall, color = textColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    track.artist,
                    style = MaterialTheme.typography.titleMedium, color = soft, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable(
                        enabled = !track.isLive && track.artist.isNotBlank(),
                        onClickLabel = "Open artist",
                    ) { onOpenArtist(track.artist) },
                )
            }
            if (!track.isLive) {
                LikeButton(isLiked, onToggle = { vm.toggleLike(track) }, idleTint = textColor, size = 28.dp)
            }
        }

        Spacer(Modifier.height(8.dp))
        if (track.isLive) {
            Row(Modifier.padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFFF4D4D)))
                Spacer(Modifier.width(6.dp))
                Text("LIVE", style = MaterialTheme.typography.labelLarge, color = textColor)
            }
        } else {
            SeekBar(player, vm::seekTo, textColor, soft)
        }

        // Main controls
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = vm::toggleShuffle) {
                Icon(Icons.Rounded.Shuffle, if (player.shuffle) "Shuffle on" else "Shuffle off", tint = if (player.shuffle) MaterialTheme.colorScheme.primary else textColor)
            }
            IconButton(onClick = vm::previous, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Rounded.SkipPrevious, "Previous", tint = textColor, modifier = Modifier.size(40.dp))
            }
            val playScale by animateFloatAsState(if (player.isPlaying) 1f else 0.94f, spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "playScale")
            Box(
                Modifier
                    .size(68.dp)
                    .graphicsLayer { scaleX = playScale; scaleY = playScale }
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable(onClickLabel = if (player.isPlaying) "Pause" else "Play") {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        vm.togglePlay()
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (player.isBuffering && !player.isPlaying) {
                    CircularProgressIndicator(Modifier.size(28.dp), color = Color.Black, strokeWidth = 3.dp)
                } else {
                    Crossfade(player.isPlaying, animationSpec = tween(150), label = "playIcon") { playing ->
                        Icon(
                            if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            if (playing) "Pause" else "Play",
                            tint = Color.Black, modifier = Modifier.size(38.dp),
                        )
                    }
                }
            }
            IconButton(onClick = vm::next, enabled = player.hasNext, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Rounded.SkipNext, "Next", tint = if (player.hasNext) textColor else soft.copy(alpha = 0.35f), modifier = Modifier.size(40.dp))
            }
            IconButton(onClick = vm::cycleRepeat) {
                Icon(
                    if (player.repeat == RepeatMode.One) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                    "Repeat: ${player.repeat.name}",
                    tint = if (player.repeat != RepeatMode.Off) MaterialTheme.colorScheme.primary else textColor,
                )
            }
        }

        // Secondary controls
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(onClick = { sheet = "sleep" }) {
                Icon(Icons.Rounded.Bedtime, "Sleep timer", tint = if (sleep != null) MaterialTheme.colorScheme.primary else soft)
            }
            if (!track.isLive) {
                TextButton(onClick = { sheet = "speed" }) {
                    Icon(Icons.Rounded.Speed, null, tint = if (player.speed != 1f) MaterialTheme.colorScheme.primary else soft, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("${trimSpeed(player.speed)}×", color = if (player.speed != 1f) MaterialTheme.colorScheme.primary else soft)
                }
            }
            IconButton(onClick = vm::openEqualizer) { Icon(Icons.Rounded.Equalizer, "Equalizer", tint = soft) }
            IconButton(onClick = {
                val text = if (track.isLive) track.title else "${track.title} – ${track.artist}"
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share song"))
            }) { Icon(Icons.Rounded.Share, "Share", tint = soft) }
            IconButton(onClick = { sheet = "queue" }) { Icon(Icons.AutoMirrored.Rounded.QueueMusic, "Queue", tint = soft) }
        }

        if (!track.isLive) {
            Spacer(Modifier.height(20.dp))
            LyricsCard(lyricsState, player.positionMs, tint, onMore = { fullLyrics = true }, onSeek = vm::seekTo)
        }
        Spacer(Modifier.height(24.dp))
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationY = dragY.value
                // Shrink and round a little while being pulled down, like a card.
                val p = (dragY.value / (closeThreshold * 3)).coerceIn(0f, 0.08f)
                scaleX = 1f - p
                scaleY = 1f - p
            }
            .clip(RoundedCornerShape(with(density) { (dragY.value / 6f).coerceAtMost(28.dp.toPx()).toDp() }))
            .background(Brush.verticalGradient(listOf(top, top.copy(alpha = 0.6f).compositeOver(Color(0xFF121212)), Color(0xFF121212))))
            .clickable(enabled = false) {},
    ) {
        // Wide windows (landscape, tablets): cover on the left, controls on the right.
        val wide = maxWidth >= 600.dp && maxWidth > maxHeight
        if (wide) {
            Row(
                Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(32.dp),
            ) {
                Column(Modifier.weight(0.9f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                    header()
                    Spacer(Modifier.height(12.dp))
                    Box(Modifier.weight(1f, fill = false), contentAlignment = Alignment.Center) { art(Modifier.fillMaxHeight()) }
                }
                Column(
                    Modifier.weight(1.1f).fillMaxHeight().nestedScroll(dismissConnection).verticalScroll(rememberScrollState()),
                ) {
                    Spacer(Modifier.height(24.dp))
                    details()
                }
            }
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .nestedScroll(dismissConnection)
                    .verticalScroll(rememberScrollState())
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp),
            ) {
                header()
                Spacer(Modifier.height(28.dp))
                art(Modifier.fillMaxWidth())
                Spacer(Modifier.height(28.dp))
                details()
            }
        }
    }

    if (fullLyrics) FullLyrics(vm, track, tint, onClose = { fullLyrics = false })
    when (sheet) {
        "queue" -> QueueSheet(vm, onClose = { sheet = null })
        "sleep" -> SleepSheet(vm, sleep, onClose = { sheet = null })
        "speed" -> SpeedSheet(vm, player.speed, onClose = { sheet = null })
    }
}

private fun trimSpeed(s: Float) = if (s % 1f == 0f) s.toInt().toString() else "%.2f".format(s).trimEnd('0').trimEnd('.')

@Composable
private fun SeekBar(player: PlayerUiState, onSeek: (Long) -> Unit, textColor: Color, soft: Color) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val duration = player.durationMs
    val fraction = if (duration > 0) (player.positionMs.toFloat() / duration).coerceIn(0f, 1f) else 0f
    Slider(
        value = dragging ?: fraction,
        onValueChange = { dragging = it },
        onValueChangeFinished = {
            dragging?.let { onSeek((it * duration).toLong()) }
            dragging = null
        },
        enabled = duration > 0,
        colors = SliderDefaults.colors(
            thumbColor = textColor,
            activeTrackColor = textColor,
            inactiveTrackColor = textColor.copy(alpha = 0.25f),
        ),
    )
    Row(Modifier.fillMaxWidth()) {
        val shown = dragging?.let { (it * duration).toLong() } ?: player.positionMs
        Text(formatTime(shown), style = MaterialTheme.typography.labelMedium, color = soft)
        Spacer(Modifier.weight(1f))
        Text(if (duration > 0) formatTime(duration) else "--:--", style = MaterialTheme.typography.labelMedium, color = soft)
    }
}

@Composable
private fun LyricsCard(state: LyricsState, positionMs: Long, tint: Color?, onMore: () -> Unit, onSeek: (Long) -> Unit) {
    val bg = (tint ?: Color(0xFF5B4BDB)).copy(alpha = 0.9f).compositeOver(Color(0xFF222222))
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(bg).padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Lyrics", style = MaterialTheme.typography.titleMedium, color = Color.White, modifier = Modifier.weight(1f))
            if (state.lyrics != null) {
                Text(
                    "MORE",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.35f))
                        .clickable(onClick = onMore)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        val lyrics = state.lyrics
        when {
            state.loading -> CircularProgressIndicator(Modifier.size(22.dp), color = Color.White)
            lyrics == null -> Text("No lyrics found for this song.", color = Color.White.copy(alpha = 0.8f))
            lyrics.synced.isNotEmpty() -> SyncedLyrics(lyrics.synced, positionMs, onSeek, 260)
            else -> Text(
                lyrics.plain.orEmpty().lines().take(8).joinToString("\n"),
                style = MaterialTheme.typography.titleMedium, color = Color.White,
            )
        }
    }
}

/** Full-screen lyrics on the artwork colour. */
@Composable
private fun FullLyrics(vm: MainViewModel, track: Track, tint: Color?, onClose: () -> Unit) {
    val state by vm.lyrics.collectAsStateWithLifecycle()
    val player by vm.player.collectAsStateWithLifecycle()
    val bg = (tint ?: Color(0xFF5B4BDB)).compositeOver(Color(0xFF222222))
    FullScreenOverlay(onDismiss = onClose) {
        Column(
            Modifier
                .fillMaxSize()
                .background(bg)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Rounded.KeyboardArrowDown, "Close lyrics", tint = Color.White) }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(track.title, style = MaterialTheme.typography.titleSmall, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(track.artist, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f), maxLines = 1)
                }
                Spacer(Modifier.width(48.dp))
            }
            val lyrics = state.lyrics
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
                    lyrics == null -> Text("No lyrics found for this song.", color = Color.White, modifier = Modifier.align(Alignment.Center))
                    lyrics.synced.isNotEmpty() -> SyncedLyrics(lyrics.synced, player.positionMs, vm::seekTo, 4000)
                    else -> Text(
                        lyrics.plain.orEmpty(),
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        modifier = Modifier.verticalScroll(rememberScrollState()).padding(vertical = 16.dp),
                    )
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.Center) {
                Box(
                    Modifier.size(60.dp).clip(CircleShape).background(Color.White).clickable(onClick = vm::togglePlay),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (player.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        if (player.isPlaying) "Pause" else "Play",
                        tint = Color.Black, modifier = Modifier.size(34.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SyncedLyrics(lines: List<LyricLine>, positionMs: Long, onSeek: (Long) -> Unit, heightDp: Int) {
    val listState = rememberLazyListState()
    val current = lines.indexOfLast { it.timeMs <= positionMs + 300 }
    LaunchedEffect(current) {
        if (current >= 0) listState.animateScrollToItem((current - 1).coerceAtLeast(0))
    }
    LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().heightIn(max = heightDp.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
        itemsIndexed(lines) { index, line ->
            val active = index == current
            val passed = index < current
            Text(
                line.text.ifBlank { "♪" },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = when {
                    active -> Color.White
                    passed -> Color.White.copy(alpha = 0.75f)
                    else -> Color.Black.copy(alpha = 0.55f)
                },
                modifier = Modifier.fillMaxWidth().clickable { onSeek(line.timeMs) }.padding(vertical = 5.dp),
            )
        }
    }
}

/** Full-screen queue, styled after the kit's radio/queue screen. */
@Composable
private fun QueueSheet(vm: MainViewModel, onClose: () -> Unit) {
    val queue by vm.queue.collectAsStateWithLifecycle()
    val player by vm.player.collectAsStateWithLifecycle()
    val radio by vm.radioSeed.collectAsStateWithLifecycle()
    val current = player.currentIndex
    val dim = Color(0xFFB3B3B3)
    FullScreenOverlay(onDismiss = onClose) {
        Column(Modifier.fillMaxSize().background(Color(0xFF121212)).statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Close queue", tint = Color.White) }
                Text(
                    radio?.let { "Radio based on ${it.title}" } ?: "Queue",
                    style = MaterialTheme.typography.titleSmall, color = Color.White, maxLines = 2,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                if (current + 1 < queue.size) TextButton(onClick = vm::clearUpcoming) { Text("Clear") } else Spacer(Modifier.width(48.dp))
            }
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 16.dp)) {
                itemsIndexed(queue, key = { i, q -> "$i-${q.key}" }) { i, q ->
                    if (i < current) return@itemsIndexed
                    if (i == current) {
                        Text("Now Playing", style = MaterialTheme.typography.titleMedium, color = Color.White, modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 6.dp))
                    }
                    if (i == current + 1) {
                        Text(
                            radio?.let { "Next from: ${it.title} radio" } ?: "Next in queue",
                            style = MaterialTheme.typography.titleMedium, color = Color.White,
                            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 6.dp),
                        )
                    }
                    Row(
                        Modifier.animateItem().fillMaxWidth().clickable(onClickLabel = "Play this song") { vm.playQueueItem(i) }.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (i == current) {
                            Artwork(q.artworkUri, Modifier.size(48.dp), corner = 2.dp)
                        } else {
                            Box(Modifier.size(22.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(q.title, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (i == current) MaterialTheme.colorScheme.primary else Color.White)
                            Text(q.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = dim)
                        }
                        if (i != current) {
                            IconButton(onClick = { vm.moveQueueItem(i, i - 1) }, enabled = i > current + 1) { Icon(Icons.Rounded.KeyboardArrowUp, "Move up", tint = dim) }
                            IconButton(onClick = { vm.moveQueueItem(i, i + 1) }, enabled = i < queue.lastIndex) { Icon(Icons.Rounded.KeyboardArrowDown, "Move down", tint = dim) }
                            IconButton(onClick = { vm.removeQueueItem(i) }) { Icon(Icons.Rounded.Close, "Remove from queue", tint = dim) }
                        }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = vm::toggleShuffle) { Icon(Icons.Rounded.Shuffle, "Shuffle", tint = if (player.shuffle) MaterialTheme.colorScheme.primary else Color.White) }
                IconButton(onClick = vm::previous) { Icon(Icons.Rounded.SkipPrevious, "Previous", tint = Color.White, modifier = Modifier.size(32.dp)) }
                Box(Modifier.size(56.dp).clip(CircleShape).background(Color.White).clickable(onClick = vm::togglePlay), contentAlignment = Alignment.Center) {
                    Icon(if (player.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (player.isPlaying) "Pause" else "Play", tint = Color.Black, modifier = Modifier.size(32.dp))
                }
                IconButton(onClick = vm::next, enabled = player.hasNext) { Icon(Icons.Rounded.SkipNext, "Next", tint = Color.White, modifier = Modifier.size(32.dp)) }
                IconButton(onClick = vm::cycleRepeat) {
                    Icon(
                        if (player.repeat == RepeatMode.One) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                        "Repeat",
                        tint = if (player.repeat != RepeatMode.Off) MaterialTheme.colorScheme.primary else Color.White,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SleepSheet(vm: MainViewModel, sleep: SleepTimer?, onClose: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onClose) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            Text("Sleep timer", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 16.dp))
            if (sleep != null) {
                val left = ((sleep.endsAt - System.currentTimeMillis()) / 60_000).coerceAtLeast(0) + 1
                Text("Pausing in about $left min (${sleep.label})", color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }
            listOf(5, 10, 15, 30, 45, 60).forEach { m ->
                Text("$m minutes", Modifier.fillMaxWidth().clickable { vm.setSleepTimer(m); onClose() }.padding(16.dp))
            }
            Text("End of song", Modifier.fillMaxWidth().clickable { vm.setSleepTimer(null); onClose() }.padding(16.dp))
            if (sleep != null) Text("Turn off timer", Modifier.fillMaxWidth().clickable { vm.cancelSleepTimer(); onClose() }.padding(16.dp), color = MaterialTheme.colorScheme.error)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpeedSheet(vm: MainViewModel, speed: Float, onClose: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onClose) {
        Column(Modifier.navigationBarsPadding().padding(16.dp)) {
            Text("Playback speed", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0.75f, 1f, 1.25f, 1.5f, 2f).forEach { s ->
                    FilterChip(selected = speed == s, onClick = { vm.setSpeed(s) }, label = { Text("${trimSpeed(s)}×") })
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
