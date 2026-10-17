package br.gohan.videofeed.auth.presenter

import br.gohan.videofeed.auth.domain.AuthRemoteDataSource
import br.gohan.videofeed.core.error.DataError
import br.gohan.videofeed.core.error.Result
import kotlinx.coroutines.yield

class FakeAuthDataSource : AuthRemoteDataSource {
    var loginResult: Result<String, DataError.Network> = Result.Success("fake-token")
    var registerResult: Result<String, DataError.Network> = Result.Success("fake-token")

    override suspend fun login(email: String, password: String): Result<String, DataError.Network> {
        yield() // allow isLoading=true state to be collected before returning
        return loginResult
    }

    override suspend fun register(email: String, password: String): Result<String, DataError.Network> {
        yield() // allow isLoading=true state to be collected before returning
        return registerResult
    }
}
