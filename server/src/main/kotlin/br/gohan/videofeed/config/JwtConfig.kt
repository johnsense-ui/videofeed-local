package br.gohan.videofeed.config

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import java.util.Date

object JwtConfig {
    private val secret = Env.getOrDefault("JWT_SECRET", "dev-secret-change-in-production")
    private val algorithm = Algorithm.HMAC256(secret)
    const val ISSUER = "videofeed"
    const val AUDIENCE = "videofeed-users"
    const val REALM = "VideoFeed"
    private const val EXPIRY_MS = 86_400_000L // 24 hours

    fun generateToken(userId: String): String = JWT.create()
        .withIssuer(ISSUER)
        .withAudience(AUDIENCE)
        .withClaim("userId", userId)
        .withExpiresAt(Date(System.currentTimeMillis() + EXPIRY_MS))
        .sign(algorithm)

    fun verifier() = JWT.require(algorithm)
        .withIssuer(ISSUER)
        .withAudience(AUDIENCE)
        .build()
}
