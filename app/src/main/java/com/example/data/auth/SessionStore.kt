package com.example.data.auth

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.UserSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.vybalDataStore by preferencesDataStore(name = "vybal_prefs")

interface SessionStorage {
    val sessionFlow: Flow<UserSession?>
    val dataSaverFlow: Flow<Boolean>
    suspend fun currentSession(): UserSession?
    suspend fun saveSession(session: UserSession)
    suspend fun clearSession()
    suspend fun setDataSaver(enabled: Boolean)
}

class SessionStore(private val context: Context) : SessionStorage {
    private object Keys {
        val ACCESS_TOKEN = stringPreferencesKey("access_token")
        val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        val USER_ID = stringPreferencesKey("user_id")
        val USER_EMAIL = stringPreferencesKey("user_email")
        val EXPIRES_AT = longPreferencesKey("expires_at_epoch_sec")
        val DATA_SAVER = booleanPreferencesKey("data_saver_enabled")
    }

    override val sessionFlow: Flow<UserSession?> = context.vybalDataStore.data.map { prefs ->
        val access = prefs[Keys.ACCESS_TOKEN]
        val refresh = prefs[Keys.REFRESH_TOKEN]
        val userId = prefs[Keys.USER_ID]
        val email = prefs[Keys.USER_EMAIL]
        val expiresAt = prefs[Keys.EXPIRES_AT]
        if (!access.isNullOrBlank() && !userId.isNullOrBlank()) {
            UserSession(
                accessToken = access,
                refreshToken = refresh.orEmpty(),
                userId = userId,
                email = email.orEmpty(),
                expiresAtEpochSec = expiresAt
            )
        } else {
            null
        }
    }

    override val dataSaverFlow: Flow<Boolean> = context.vybalDataStore.data.map { prefs ->
        prefs[Keys.DATA_SAVER] ?: false
    }

    override suspend fun currentSession(): UserSession? = sessionFlow.first()

    override suspend fun saveSession(session: UserSession) {
        context.vybalDataStore.edit { prefs ->
            prefs[Keys.ACCESS_TOKEN] = session.accessToken
            prefs[Keys.REFRESH_TOKEN] = session.refreshToken
            prefs[Keys.USER_ID] = session.userId
            prefs[Keys.USER_EMAIL] = session.email
            if (session.expiresAtEpochSec != null) {
                prefs[Keys.EXPIRES_AT] = session.expiresAtEpochSec
            } else {
                prefs.remove(Keys.EXPIRES_AT)
            }
        }
    }

    override suspend fun clearSession() {
        context.vybalDataStore.edit { prefs ->
            prefs.remove(Keys.ACCESS_TOKEN)
            prefs.remove(Keys.REFRESH_TOKEN)
            prefs.remove(Keys.USER_ID)
            prefs.remove(Keys.USER_EMAIL)
            prefs.remove(Keys.EXPIRES_AT)
        }
    }

    override suspend fun setDataSaver(enabled: Boolean) {
        context.vybalDataStore.edit { prefs ->
            prefs[Keys.DATA_SAVER] = enabled
        }
    }
}
