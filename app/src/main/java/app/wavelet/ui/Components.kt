package app.wavelet.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.palette.graphics.Palette
import app.wavelet.download.DOWNLOADS_SOURCE_ID
import app.wavelet.source.Track
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.remember

// ---------------------------------------------------------------- artwork

@Composable
fun Artwork(
    url: String?,
    modifier: Modifier = Modifier,
    live: Boolean = false,
    corner: Dp = 6.dp,
    icon: ImageVector? = null,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(corner))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        // The note icon only shows when there is no picture (or it failed), so covers don't flash
        // an icon while they load; a plain tile shows instead.
        var failed by remember(url) { mutableStateOf(false) }
        if (url == null || failed) {
            Icon(
                icon ?: if (live) Icons.Rounded.Radio else Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxSize(0.45f),
            )
        }
        if (url != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(url)
                    .crossfade(300)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onError = { failed = true },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

// ---------------------------------------------------------------- like button

/** Heart that pops when tapped, with a light vibration (like Spotify's). */
@Composable
fun LikeButton(isLiked: Boolean, onToggle: () -> Unit, idleTint: Color, size: Dp = 24.dp) {
    val haptics = LocalHapticFeedback.current
    val scale = remember { Animatable(1f) }
    var previous by remember { mutableStateOf(isLiked) }
    LaunchedEffect(isLiked) {
        if (previous != isLiked) {
            previous = isLiked
            if (isLiked) {
                scale.snapTo(0.6f)
                scale.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMediumLow))
            } else {
                scale.snapTo(0.85f)
                scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioNoBouncy))
            }
        }
    }
    IconButton(onClick = {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        onToggle()
    }) {
        Icon(
            if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            contentDescription = if (isLiked) "Remove from Liked Songs" else "Add to Liked Songs",
            tint = if (isLiked) MaterialTheme.colorScheme.primary else idleTint,
            modifier = Modifier.size(size).graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            },
        )
    }
}

// ---------------------------------------------------------------- loading placeholders

/** A soft moving highlight, drawn on placeholder shapes while content loads. */
fun Modifier.shimmer(): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val x by transition.animateFloat(
        initialValue = -1f, targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmerX",
    )
    val base = MaterialTheme.colorScheme.surfaceContainerHigh
    val highlight = MaterialTheme.colorScheme.surfaceContainerHighest
    this.drawWithCache {
        val brush = Brush.linearGradient(
            colors = listOf(base, highlight, base),
            start = Offset(size.width * x, 0f),
            end = Offset(size.width * x + size.width * 0.6f, size.height * 0.2f),
        )
        onDrawBehind { drawRect(brush) }
    }
}

/** Placeholder for a shelf of cards while it loads. */
@Composable
fun ShelfSkeleton() {
    Column {
        Box(Modifier.padding(start = 16.dp, top = 24.dp, bottom = 14.dp).size(width = 180.dp, height = 22.dp).clip(RoundedCornerShape(6.dp)).shimmer())
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            repeat(3) {
                Column(Modifier.width(148.dp)) {
                    Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(6.dp)).shimmer())
                    Spacer(Modifier.height(10.dp))
                    Box(Modifier.size(width = 110.dp, height = 14.dp).clip(RoundedCornerShape(4.dp)).shimmer())
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.size(width = 70.dp, height = 12.dp).clip(RoundedCornerShape(4.dp)).shimmer())
                }
            }
        }
    }
}

/** Placeholder for song rows while search results load. */
@Composable
fun SongRowSkeleton(count: Int = 3) {
    Column {
        repeat(count) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(50.dp).clip(RoundedCornerShape(4.dp)).shimmer())
                Spacer(Modifier.width(12.dp))
                Column {
                    Box(Modifier.size(width = 170.dp, height = 14.dp).clip(RoundedCornerShape(4.dp)).shimmer())
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.size(width = 110.dp, height = 12.dp).clip(RoundedCornerShape(4.dp)).shimmer())
                }
            }
        }
    }
}

/** 2×2 mosaic of the first four covers, like a playlist cover. */
@Composable
fun MosaicCover(urls: List<String>, modifier: Modifier = Modifier, corner: Dp = 6.dp) {
    if (urls.size < 4) {
        Artwork(urls.firstOrNull(), modifier, corner = corner)
        return
    }
    Column(modifier.clip(RoundedCornerShape(corner))) {
        Row(Modifier.weight(1f)) {
            Artwork(urls[0], Modifier.weight(1f).fillMaxSize(), corner = 0.dp)
            Artwork(urls[1], Modifier.weight(1f).fillMaxSize(), corner = 0.dp)
        }
        Row(Modifier.weight(1f)) {
            Artwork(urls[2], Modifier.weight(1f).fillMaxSize(), corner = 0.dp)
            Artwork(urls[3], Modifier.weight(1f).fillMaxSize(), corner = 0.dp)
        }
    }
}

/** Gradient tile used for "Liked Songs". */
@Composable
fun LikedCover(modifier: Modifier = Modifier, corner: Dp = 6.dp) {
    Box(
        modifier
            .clip(RoundedCornerShape(corner))
            .background(Brush.linearGradient(listOf(Color(0xFF4B2BD6), Color(0xFFB7A9FF)))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Favorite, contentDescription = null, tint = Color.White, modifier = Modifier.fillMaxSize(0.42f))
    }
}

/** Dominant colour of an artwork image, for tinted backgrounds. Null until known. */
@Composable
fun rememberArtworkColor(url: String?): Color? {
    val context = LocalContext.current
    val color by produceState<Color?>(null, url) {
        value = null
        if (url == null) return@produceState
        val request = ImageRequest.Builder(context).data(url).allowHardware(false).size(128).build()
        val result = context.imageLoader.execute(request)
        val bitmap = ((result as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap ?: return@produceState
        val palette = Palette.from(bitmap).generate()
        val swatch = palette.darkVibrantSwatch ?: palette.vibrantSwatch ?: palette.darkMutedSwatch ?: palette.dominantSwatch
        value = swatch?.rgb?.let { Color(it) }
    }
    return color
}

// ---------------------------------------------------------------- rows & cards

/** Standard song row, wired to the view model (tap plays, ⋮ opens options). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongRow(
    vm: MainViewModel,
    track: Track,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    index: Int? = null,
    playlistId: Long? = null,
    itemId: Long? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val player by vm.player.collectAsStateWithLifecycle()
    val liked by vm.likedKeys.collectAsStateWithLifecycle()
    val downloads by vm.downloads.collectAsStateWithLifecycle()
    val progress by vm.downloadProgress.collectAsStateWithLifecycle()
    val starting by vm.starting.collectAsStateWithLifecycle()
    val isStarting = starting?.key == track.key
    val haptics = LocalHapticFeedback.current

    // Optimized derived states to reduce recomposition
    val isCurrent by remember(track.key, track.id, player.currentKey) {
        derivedStateOf { player.currentKey == track.key || player.currentKey == track.id }
    }
    val downloaded by remember(track.sourceId, downloads, track.key) {
        derivedStateOf { track.sourceId == DOWNLOADS_SOURCE_ID || downloads.any { it.id == track.key } }
    }
    val isLiked by remember(track.key, liked, track.id) {
        derivedStateOf { track.key in liked || track.id in liked }
    }

    Row(
        modifier
            .fillMaxWidth()
            .combinedClickable(
                onClickLabel = "Play",
                onLongClickLabel = "More options",
                onClick = onClick,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    vm.showOptions(track, playlistId, itemId)
                },
            )
            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (index != null) {
            Text(
                "$index",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(28.dp),
            )
        }
        Box(contentAlignment = Alignment.Center) {
            Artwork(track.artworkUrl, Modifier.size(50.dp), live = track.isLive, corner = 4.dp)
            if (isStarting) {
                // The song was just tapped and is still loading.
                Box(Modifier.size(50.dp).clip(RoundedCornerShape(4.dp)).background(Color.Black.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isCurrent && player.isPlaying && !isStarting) {
                    Icon(Icons.Rounded.GraphicEq, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp).padding(end = 4.dp))
                }
                Text(
                    track.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isCurrent || isStarting) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                val p = progress[track.key]
                when {
                    p != null -> CircularProgressIndicator(Modifier.size(12.dp).padding(end = 2.dp), strokeWidth = 1.5.dp)
                    downloaded -> Icon(Icons.Rounded.DownloadForOffline, "Downloaded", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                }
                if (isLiked) Icon(Icons.Rounded.Favorite, "Liked", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                if (p != null || downloaded || isLiked) Spacer(Modifier.width(4.dp))
                Text(
                    if (isStarting) "Loading…" else subtitle ?: track.artist,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (trailing != null) trailing() else {
            if (track.downloadable && !downloaded && progress[track.key] == null && !track.isLive) {
                IconButton(onClick = { vm.download(track) }) {
                    Icon(
                        Icons.Rounded.Download,
                        contentDescription = "Download ${track.title}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            IconButton(onClick = { vm.showOptions(track, playlistId, itemId) }) {
                Icon(Icons.Rounded.MoreVert, contentDescription = "Options for ${track.title}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Square card with artwork, title and subtitle, used in horizontal shelves. */
@Composable
fun SquareCard(
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 148.dp,
    circle: Boolean = false,
    cover: @Composable BoxScope.() -> Unit,
) {
    Column(
        modifier
            .width(width)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(bottom = 4.dp),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(if (circle) CircleShape else RoundedCornerShape(6.dp)), content = cover)
        Spacer(Modifier.height(8.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (!subtitle.isNullOrBlank()) {
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Horizontal shelf of song cards. */
@Composable
fun TrackShelf(vm: MainViewModel, title: String, tracks: List<Track>, subtitle: (Track) -> String = { it.artist }) {
    if (tracks.isEmpty()) return
    SectionTitle(title)
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        items(tracks, key = { it.key }) { t ->
            SquareCard(t.title, subtitle(t), onClick = { vm.play(t, tracks) }) {
                Artwork(t.artworkUrl, Modifier.fillMaxSize(), live = t.isLive, corner = 0.dp)
            }
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 24.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier, actions: @Composable () -> Unit = {}) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        actions()
    }
}

fun formatTime(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** "1 hr 12 min" style total length. */
fun formatLength(ms: Long): String {
    val minutes = ms / 60_000
    return if (minutes >= 60) "${minutes / 60} hr ${minutes % 60} min" else "$minutes min"
}
