package br.gohan.videofeed

import android.app.Application
import androidx.media3.common.util.UnstableApi
import br.gohan.videofeed.di.appModules
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

@androidx.annotation.OptIn(UnstableApi::class)
class VideoFeedApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger(Level.DEBUG)
            androidContext(this@VideoFeedApplication)
            properties(mapOf("baseUrl" to BuildConfig.BASE_URL))
            modules(appModules)
        }
    }
}
