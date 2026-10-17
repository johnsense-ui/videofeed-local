package br.gohan.videofeed.core.network

import br.gohan.videofeed.core.error.DataError
import br.gohan.videofeed.core.error.Result
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.util.network.UnresolvedAddressException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException

suspend inline fun <reified T> safeCall(execute: () -> HttpResponse): Result<T, DataError.Network> {
    val response = try {
        execute()
    } catch (e: UnresolvedAddressException) {
        return Result.Error(DataError.Network.NO_INTERNET)
    } catch (e: SerializationException) {
        return Result.Error(DataError.Network.SERIALIZATION)
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        return Result.Error(DataError.Network.UNKNOWN)
    }
    return responseToResult(response)
}

suspend inline fun <reified T> responseToResult(response: HttpResponse): Result<T, DataError.Network> =
    when (response.status.value) {
        in 200..299 -> Result.Success(response.body<T>())
        401 -> Result.Error(DataError.Network.UNAUTHORIZED)
        408 -> Result.Error(DataError.Network.REQUEST_TIMEOUT)
        409 -> Result.Error(DataError.Network.CONFLICT)
        413 -> Result.Error(DataError.Network.PAYLOAD_TOO_LARGE)
        429 -> Result.Error(DataError.Network.TOO_MANY_REQUESTS)
        in 500..599 -> Result.Error(DataError.Network.SERVER_ERROR)
        else -> Result.Error(DataError.Network.UNKNOWN)
    }

suspend inline fun <reified Response : Any> HttpClient.get(
    route: String,
    baseUrl: String,
    queryParameters: Map<String, Any?> = emptyMap()
): Result<Response, DataError.Network> = safeCall {
    get {
        url("$baseUrl$route")
        queryParameters.forEach { (k, v) -> parameter(k, v) }
    }
}

suspend inline fun <reified Request, reified Response : Any> HttpClient.post(
    route: String,
    baseUrl: String,
    body: Request
): Result<Response, DataError.Network> = safeCall {
    post {
        url("$baseUrl$route")
        contentType(ContentType.Application.Json)
        setBody(body)
    }
}
