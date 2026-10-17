package br.gohan.videofeed.config

import io.github.cdimascio.dotenv.dotenv

object Env {
    private val dotenv = dotenv { ignoreIfMissing = false }

    fun getOrDefault(key: String, default: String): String =
        dotenv.get(key)
}
