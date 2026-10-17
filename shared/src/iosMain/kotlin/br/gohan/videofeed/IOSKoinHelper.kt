package br.gohan.videofeed

import br.gohan.videofeed.auth.data.authDataModule
import br.gohan.videofeed.auth.domain.AuthRemoteDataSource
import br.gohan.videofeed.auth.presenter.LoginViewModel
import br.gohan.videofeed.auth.presenter.RegisterViewModel
import br.gohan.videofeed.di.iosCoreModule
import br.gohan.videofeed.feed.data.feedDataModule
import br.gohan.videofeed.feed.domain.VideoRemoteDataSource
import br.gohan.videofeed.feed.presenter.FeedViewModel
import br.gohan.videofeed.upload.data.uploadDataModule
import br.gohan.videofeed.upload.domain.R2UploadDataSource
import br.gohan.videofeed.upload.domain.UploadRemoteDataSource
import br.gohan.videofeed.upload.presenter.UploadViewModel
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.context.startKoin

fun initKoin(baseUrl: String) {
    startKoin {
        properties(mapOf("baseUrl" to baseUrl))
        modules(
            iosCoreModule,
            authDataModule,
            feedDataModule,
            uploadDataModule,
        )
    }
}

private object ViewModelFactory : KoinComponent {
    fun loginViewModel() = LoginViewModel(get<AuthRemoteDataSource>())
    fun registerViewModel() = RegisterViewModel(get<AuthRemoteDataSource>())
    fun feedViewModel() = FeedViewModel(get<VideoRemoteDataSource>())
    fun uploadViewModel() = UploadViewModel(get<UploadRemoteDataSource>(), get<R2UploadDataSource>())
}

fun loginViewModel(): LoginViewModel = ViewModelFactory.loginViewModel()
fun registerViewModel(): RegisterViewModel = ViewModelFactory.registerViewModel()
fun feedViewModel(): FeedViewModel = ViewModelFactory.feedViewModel()
fun uploadViewModel(): UploadViewModel = ViewModelFactory.uploadViewModel()
