package br.gohan.videofeed.feed

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import br.gohan.videofeed.auth.ObserveAsEvents
import br.gohan.videofeed.feed.presenter.FeedAction
import br.gohan.videofeed.feed.presenter.FeedEvent
import br.gohan.videofeed.feed.presenter.FeedState
import br.gohan.videofeed.feed.presenter.FeedViewModel
import coil3.compose.AsyncImage
import org.koin.compose.viewmodel.koinViewModel
import java.io.File

@UnstableApi
@Composable
fun FeedRoot(
    onNavigateToUpload: () -> Unit,
    onNavigateToLogin: () -> Unit,
    viewModel: FeedViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()

    val simpleCache = remember {
        SimpleCache(
            File(context.cacheDir, "media_cache"),
            LeastRecentlyUsedCacheEvictor(100 * 1024 * 1024L),
            StandaloneDatabaseProvider(context)
        )
    }

    val cacheDataSourceFactory = remember {
        CacheDataSource.Factory()
            .setCache(simpleCache)
            .setUpstreamDataSourceFactory(DefaultHttpDataSource.Factory())
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    val exoPlayer = remember {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(ProgressiveMediaSource.Factory(cacheDataSourceFactory))
            .build()
            .apply { repeatMode = Player.REPEAT_MODE_ONE }
    }

    val preloader = remember { VideoPreloader(simpleCache, cacheDataSourceFactory) }

    LaunchedEffect(state.currentIndex, state.videos.size) {
        state.videos
            .drop(state.currentIndex + 1)
            .take(2)
            .forEach { preloader.prefetch(it.cdnUrl) }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
            simpleCache.release()
        }
    }

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            FeedEvent.NavigateToUpload -> onNavigateToUpload()
            FeedEvent.NavigateToLogin -> onNavigateToLogin()
        }
    }

    FeedScreen(
        state = state,
        exoPlayer = exoPlayer,
        onAction = viewModel::onAction
    )
}

@UnstableApi
@Composable
fun FeedScreen(
    state: FeedState,
    exoPlayer: ExoPlayer,
    onAction: (FeedAction) -> Unit
) {
    if (state.videos.isEmpty()) return

    val pagerState = rememberPagerState(pageCount = { state.videos.size })

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            if (state.videos.isNotEmpty()) {
                val video = state.videos[page]
                exoPlayer.setMediaItem(MediaItem.fromUri(video.cdnUrl))
                exoPlayer.prepare()
                exoPlayer.play()
                onAction(FeedAction.OnVideoVisible(page))
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        VerticalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            VideoPageItem(
                video = state.videos[page],
                isCurrentPage = pagerState.currentPage == page,
                exoPlayer = exoPlayer
            )
        }

        FloatingActionButton(
            onClick = { onAction(FeedAction.OnUploadClick) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Text("+")
        }
    }
}

@UnstableApi
@Composable
private fun VideoPageItem(
    video: br.gohan.videofeed.feed.presenter.VideoUi,
    isCurrentPage: Boolean,
    exoPlayer: ExoPlayer
) {
    var isPlaying by remember { mutableStateOf(true) }
    var showIcon by remember { mutableStateOf(false) }

    LaunchedEffect(showIcon) {
        if (showIcon) {
            delay(800)
            showIcon = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                if (isCurrentPage) {
                    if (exoPlayer.isPlaying) {
                        exoPlayer.pause()
                        isPlaying = false
                    } else {
                        exoPlayer.play()
                        isPlaying = true
                    }
                    showIcon = true
                }
            }
    ) {
        if (isCurrentPage) {
            VideoPlayerView(
                exoPlayer = exoPlayer,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            AsyncImage(
                model = video.thumbnailUrl,
                contentDescription = video.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        AnimatedVisibility(
            visible = showIcon,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.5f),
                modifier = Modifier.size(72.dp)
            ) {
                Text(
                    text = if (isPlaying) "▶" else "⏸",
                    color = Color.White,
                    fontSize = 28.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 20.dp)
                )
            }
        }

        VideoInfoOverlay(
            video = video,
            modifier = Modifier.align(Alignment.BottomStart)
        )
    }
}
