package br.gohan.videofeed.feed.data

import br.gohan.videofeed.feed.domain.VideoRemoteDataSource
import org.koin.dsl.bind
import org.koin.dsl.module

val feedDataModule = module {
    single { KtorVideoDataSource(get(), getProperty("baseUrl")) } bind VideoRemoteDataSource::class
}
