package br.gohan.videofeed.di

import androidx.media3.common.util.UnstableApi
import br.gohan.videofeed.auth.data.DataStoreTokenStorage
import br.gohan.videofeed.auth.data.TokenStorage
import br.gohan.videofeed.auth.data.authDataModule
import br.gohan.videofeed.auth.presenter.LoginViewModel
import br.gohan.videofeed.auth.presenter.RegisterViewModel
import br.gohan.videofeed.core.network.HttpClientFactory
import br.gohan.videofeed.feed.data.feedDataModule
import br.gohan.videofeed.feed.presenter.FeedViewModel
import br.gohan.videofeed.upload.data.uploadDataModule
import br.gohan.videofeed.upload.presenter.UploadViewModel
import io.ktor.client.engine.android.Android
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val coreAndroidModule = module {
    single<TokenStorage> { DataStoreTokenStorage(androidContext()) }
    single { HttpClientFactory.create(Android.create(), get()) }
}

val authPresentationModule = module {
    viewModelOf(::LoginViewModel)
    viewModelOf(::RegisterViewModel)
}

val feedPresentationAndroidModule = module {
    viewModelOf(::FeedViewModel)
}

val uploadPresentationAndroidModule = module {
    viewModelOf(::UploadViewModel)
}

@UnstableApi
val appModules = listOf(
    coreAndroidModule,
    authDataModule,
    authPresentationModule,
    feedDataModule,
    feedPresentationAndroidModule,
    uploadDataModule,
    uploadPresentationAndroidModule
)
