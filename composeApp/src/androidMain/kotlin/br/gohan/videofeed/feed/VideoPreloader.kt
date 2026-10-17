package br.gohan.videofeed.feed

import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.SimpleCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import androidx.core.net.toUri

@UnstableApi
class VideoPreloader(
    private val cache: SimpleCache,
    private val dataSourceFactory: CacheDataSource.Factory
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Pre-caches the first 2 MB of the video — enough for a smooth start
    fun prefetch(url: String) {
        scope.launch {
            try {
                val dataSpec = DataSpec(url.toUri(), 0, 2 * 1024 * 1024)
                CacheWriter(
                    dataSourceFactory.createDataSource(),
                    dataSpec,
                    null,
                    null
                ).cache()
            } catch (_: Exception) {
                // Prefetch failure is non-fatal — video will stream normally
            }
        }
    }
}
