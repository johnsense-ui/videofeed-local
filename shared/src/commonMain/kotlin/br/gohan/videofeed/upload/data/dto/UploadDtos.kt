package br.gohan.videofeed.upload.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class PresignRequestDto(val filename: String)

@Serializable
data class PresignResponseDto(val uploadUrl: String, val videoKey: String)

@Serializable
data class RegisterVideoRequestDto(val videoKey: String, val title: String)

@Serializable
data class RegisterVideoResponseDto(
    val id: String,
    val title: String,
    val cdnUrl: String,
    val thumbnailUrl: String?,
    val uploaderName: String
)
