package br.gohan.videofeed.auth.data

import platform.Foundation.NSUserDefaults

// Uses NSUserDefaults for simplicity in a PoC.
// In production, replace with Security framework Keychain calls.
class KeychainTokenStorage : TokenStorage {
    private val defaults = NSUserDefaults.standardUserDefaults
    private val key = "jwt_token"

    override suspend fun getToken(): String? = defaults.stringForKey(key)

    override suspend fun saveToken(token: String) {
        defaults.setObject(token, key)
    }

    override suspend fun clearToken() {
        defaults.removeObjectForKey(key)
    }
}
