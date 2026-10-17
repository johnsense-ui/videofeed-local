package br.gohan.videofeed.di

import br.gohan.videofeed.auth.data.KeychainTokenStorage
import br.gohan.videofeed.auth.data.TokenStorage
import br.gohan.videofeed.core.network.HttpClientFactory
import io.ktor.client.engine.darwin.Darwin
import org.koin.dsl.module

val iosCoreModule = module {
    single<TokenStorage> { KeychainTokenStorage() }
    single { HttpClientFactory.create(Darwin.create(), get()) }
}
