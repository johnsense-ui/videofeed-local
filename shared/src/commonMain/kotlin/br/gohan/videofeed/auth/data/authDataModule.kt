package br.gohan.videofeed.auth.data

import br.gohan.videofeed.auth.domain.AuthRemoteDataSource
import org.koin.dsl.bind
import org.koin.dsl.module

val authDataModule = module {
    single { KtorAuthDataSource(get(), getProperty("baseUrl"), get()) } bind AuthRemoteDataSource::class
}
