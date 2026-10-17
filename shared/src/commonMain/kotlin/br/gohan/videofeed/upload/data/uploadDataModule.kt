package br.gohan.videofeed.upload.data

import br.gohan.videofeed.upload.domain.R2UploadDataSource
import br.gohan.videofeed.upload.domain.UploadRemoteDataSource
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

val uploadDataModule = module {
    single(named("plain")) {
        HttpClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
    }
    single {
        KtorUploadDataSource(
            httpClient = get(),
            baseUrl = getProperty("baseUrl")
        )
    } bind UploadRemoteDataSource::class

    single {
        KtorR2UploadDataSource(httpClient = get(named("plain")))
    } bind R2UploadDataSource::class
}
