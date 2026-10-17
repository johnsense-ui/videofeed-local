package br.gohan.videofeed.auth.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "auth_prefs")

class DataStoreTokenStorage(private val context: Context) : TokenStorage {
    private val tokenKey = stringPreferencesKey("jwt_token")

    override suspend fun getToken(): String? =
        context.dataStore.data.map { it[tokenKey] }.firstOrNull()

    override suspend fun saveToken(token: String) {
        context.dataStore.edit { it[tokenKey] = token }
    }

    override suspend fun clearToken() {
        context.dataStore.edit { it.remove(tokenKey) }
    }
}
