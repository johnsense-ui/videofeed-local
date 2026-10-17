package br.gohan.videofeed.upload.domain

data class PresignResult(
    val uploadUrl: String,
    val videoKey: String
)
