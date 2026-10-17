package br.gohan.videofeed.feed.data

import br.gohan.videofeed.feed.domain.VideoRemoteDataSource
import org.koin.dsl.bind
import org.koin.dsl.module

val feedDataModule = module {
    // 用 provider 而不是固定值：Swift 侧随时改 ApiConfig.baseUrl，
    // 下一次请求就生效，不用重启 Koin。
    single {
        KtorVideoDataSource(get(), { br.gohan.videofeed.ApiConfig.baseUrl })
    } bind VideoRemoteDataSource::class
}
