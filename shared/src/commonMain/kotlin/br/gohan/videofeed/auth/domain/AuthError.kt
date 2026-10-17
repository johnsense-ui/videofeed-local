package br.gohan.videofeed.auth.domain

import br.gohan.videofeed.core.error.Error

enum class AuthError : Error {
    INVALID_CREDENTIALS,
    EMAIL_ALREADY_EXISTS,
    INVALID_EMAIL,
    PASSWORD_TOO_SHORT
}
