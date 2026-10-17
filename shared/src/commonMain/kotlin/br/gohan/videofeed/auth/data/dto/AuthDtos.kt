package br.gohan.videofeed.auth.data.dto

import kotlinx.serialization.Serializable

@Serializable
internal data class LoginRequestDto(val email: String, val password: String)

@Serializable
internal data class RegisterRequestDto(val email: String, val password: String)

@Serializable
internal data class AuthResponseDto(val token: String)
