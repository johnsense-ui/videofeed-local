package br.gohan.videofeed

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform