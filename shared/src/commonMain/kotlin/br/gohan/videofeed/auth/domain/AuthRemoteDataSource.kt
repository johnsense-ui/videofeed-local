package br.gohan.videofeed.auth.domain

import br.gohan.videofeed.core.error.DataError
import br.gohan.videofeed.core.error.Result

interface AuthRemoteDataSource {
    suspend fun login(email: String, password: String): Result<String, DataError.Network>
    suspend fun register(email: String, password: String): Result<String, DataError.Network>
}
