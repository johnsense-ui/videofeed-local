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
    ApiConfig.baseUrl = baseUrl.trimEnd('/')
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

/** Swift 侧改服务器地址：IOSKoinHelperKt.setServerBase(url:) */
fun setServerBase(url: String) {
    ApiConfig.baseUrl = url.trim().trimEnd('/')
}

/** Swift 侧读当前地址 */
fun currentServerBase(): String = ApiConfig.baseUrl

/** 试探地址是否可用（Swift 的「测试连接」用） */
fun probeServer(url: String): Boolean {
    val clean = url.trim().trimEnd('/')
    ApiConfig.baseUrl = clean
    return clean.startsWith("http://") || clean.startsWith("https://")
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
