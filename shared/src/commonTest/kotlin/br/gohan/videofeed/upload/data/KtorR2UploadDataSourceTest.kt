package br.gohan.videofeed.upload.data

import br.gohan.videofeed.core.error.Result
import br.gohan.videofeed.upload.domain.UploadError
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

class KtorR2UploadDataSourceTest {

    @Test
    fun `upload emits 1_0 success on 200`() = runTest {
        val engine = MockEngine { respond(content = "", status = HttpStatusCode.OK) }
        val results = KtorR2UploadDataSource(HttpClient(engine))
            .upload("https://r2.example.com/upload", ByteArray(100), "video/mp4")
            .toList()
        val last = results.last()
        assertIs<Result.Success<Float>>(last)
        assertTrue(last.data == 1.0f)
    }

    @Test
    fun `upload emits UploadError_Upload on non-2xx`() = runTest {
        val engine = MockEngine { respond(content = "", status = HttpStatusCode.Forbidden) }
        val results = KtorR2UploadDataSource(HttpClient(engine))
            .upload("https://r2.example.com/upload", ByteArray(100), "video/mp4")
            .toList()
        val last = results.last()
        assertIs<Result.Error<UploadError>>(last)
        assertIs<UploadError.Upload>(last.error)
    }
}
