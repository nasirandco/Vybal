package com.example.data.auth

import com.example.core.config.VybalSupabaseConfig
import com.example.core.network.SupabaseAuthApi
import com.example.core.network.SupabaseAuthRequest
import com.example.core.network.SupabaseAuthResponse
import com.example.core.network.SupabaseClientBundle
import com.example.core.network.SupabaseErrorDto
import com.example.core.network.SupabaseRecoverRequest
import com.example.core.network.SupabaseRefreshRequest
import com.example.core.network.SupabaseSignUpMetadata
import com.example.core.network.SupabaseSignUpRequest
import com.example.data.model.UserSession
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import retrofit2.Response

sealed interface AuthResult {
    data class Authenticated(val session: UserSession) : AuthResult
    data object EmailConfirmationRequired : AuthResult
    data class Failure(val message: String) : AuthResult
}

sealed interface PasswordResetResult {
    data object Sent : PasswordResetResult
    data class Failure(val message: String) : PasswordResetResult
}

class SupabaseAuthRepository(
    private val authApi: SupabaseAuthApi,
    private val sessionStore: SessionStorage,
    private val moshi: Moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build(),
    private val isConfiguredProvider: () -> Boolean = { VybalSupabaseConfig.isConfigured }
) {
    constructor(
        clients: SupabaseClientBundle,
        sessionStore: SessionStorage
    ) : this(
        authApi = clients.authApi,
        sessionStore = sessionStore,
        moshi = clients.moshi
    )

    val sessionFlow: Flow<UserSession?> = sessionStore.sessionFlow

    suspend fun signIn(email: String, password: String): AuthResult = withContext(Dispatchers.IO) {
        if (!isConfiguredProvider()) {
            return@withContext AuthResult.Failure(
                "Supabase environment is not configured. Check your AI Studio Secrets."
            )
        }
        val cleanEmail = email.trim()
        val isDesignatedSuperAdmin = cleanEmail.equals(
            com.example.core.config.VybalSchemaContract.PERMANENT_SUPER_ADMIN_EMAIL,
            ignoreCase = true
        )
        runCatching {
            val response = authApi.signInWithPassword(
                request = SupabaseAuthRequest(email = cleanEmail, password = password)
            )
            if (response.isSuccessful) {
                val session = response.body()?.toUserSession(cleanEmail)
                if (session != null) {
                    sessionStore.saveSession(session)
                    return@withContext AuthResult.Authenticated(session)
                }
                return@withContext AuthResult.Failure("Authentication succeeded without a session token.")
            }

            val rawErr = runCatching { response.errorBody()?.string() }.getOrNull().orEmpty()

            // First-time Super Admin auto-sign-in flow:
            // If nasirandco001@gmail.com signs in before ever signing up, provision via standard
            // authApi.signUp (server-side trigger auto-confirms email_confirmed_at & assigns super_admin).
            // Password authentication is strictly preserved: if the account already exists with a
            // different password, signUp returns "User already registered" and we return the normal
            // invalid-credentials error.
            if (isDesignatedSuperAdmin) {
                val isFirstTimeOrUnconfirmed = rawErr.contains("invalid_credentials", ignoreCase = true) ||
                    rawErr.contains("Invalid login credentials", ignoreCase = true) ||
                    rawErr.contains("email not confirmed", ignoreCase = true)

                if (isFirstTimeOrUnconfirmed) {
                    val bootstrapResp = authApi.signUp(
                        request = SupabaseSignUpRequest(
                            email = cleanEmail,
                            password = password,
                            data = SupabaseSignUpMetadata(
                                username = "vybal_super_admin",
                                displayName = "VYBAL Super Admin"
                            )
                        )
                    )
                    if (bootstrapResp.isSuccessful) {
                        val directSession = bootstrapResp.body()?.toUserSession(cleanEmail)
                        if (directSession != null) {
                            sessionStore.saveSession(directSession)
                            return@withContext AuthResult.Authenticated(directSession)
                        }
                        // Server-side trigger confirmed email_confirmed_at; complete password sign-in
                        val retryResp = authApi.signInWithPassword(
                            request = SupabaseAuthRequest(email = cleanEmail, password = password)
                        )
                        if (retryResp.isSuccessful) {
                            val retrySession = retryResp.body()?.toUserSession(cleanEmail)
                            if (retrySession != null) {
                                sessionStore.saveSession(retrySession)
                                return@withContext AuthResult.Authenticated(retrySession)
                            }
                        }
                        return@withContext AuthResult.Failure(
                            "Super Admin account initialized. Run super_admin_dashboard.sql in Supabase to enable password sign-in without email confirmation."
                        )
                    }
                    // If already registered, fall through to normal password error below
                    val bootErr = runCatching { bootstrapResp.errorBody()?.string() }.getOrNull().orEmpty()
                    if (bootErr.contains("already registered", ignoreCase = true) &&
                        rawErr.contains("email not confirmed", ignoreCase = true)
                    ) {
                        return@withContext AuthResult.Failure(
                            "Run super_admin_dashboard.sql in Supabase SQL Editor to auto-confirm the existing Super Admin account."
                        )
                    }
                }
            }

            AuthResult.Failure(parseAuthErrorRaw(rawErr, response.code()))
        }.getOrElse { err ->
            AuthResult.Failure(err.localizedMessage ?: "Unable to connect to VYBAL authentication.")
        }
    }

    suspend fun signUp(
        email: String,
        password: String,
        username: String,
        displayName: String = ""
    ): AuthResult = withContext(Dispatchers.IO) {
        if (!isConfiguredProvider()) {
            return@withContext AuthResult.Failure(
                "Supabase environment is not configured. Check your AI Studio Secrets."
            )
        }
        val cleanEmail = email.trim()
        val cleanUsername = username.trim().lowercase()
        val cleanDisplay = displayName.trim().ifBlank { cleanUsername }
        runCatching {
            val response = authApi.signUp(
                request = SupabaseSignUpRequest(
                    email = cleanEmail,
                    password = password,
                    data = SupabaseSignUpMetadata(
                        username = cleanUsername,
                        displayName = cleanDisplay
                    )
                )
            )
            if (response.isSuccessful) {
                val session = response.body()?.toUserSession(cleanEmail)
                if (session != null) {
                    sessionStore.saveSession(session)
                    AuthResult.Authenticated(session)
                } else {
                    AuthResult.EmailConfirmationRequired
                }
            } else {
                AuthResult.Failure(parseAuthError(response))
            }
        }.getOrElse { err ->
            AuthResult.Failure(err.localizedMessage ?: "Unable to create your VYBAL account.")
        }
    }

    suspend fun sendPasswordReset(email: String): PasswordResetResult = withContext(Dispatchers.IO) {
        if (!isConfiguredProvider()) {
            return@withContext PasswordResetResult.Failure(
                "Supabase environment is not configured. Check your AI Studio Secrets."
            )
        }
        val cleanEmail = email.trim()
        runCatching {
            val response = authApi.recoverPassword(
                request = SupabaseRecoverRequest(email = cleanEmail)
            )
            if (response.isSuccessful) {
                PasswordResetResult.Sent
            } else {
                PasswordResetResult.Failure(parseAuthError(response))
            }
        }.getOrElse { err ->
            PasswordResetResult.Failure(err.localizedMessage ?: "Unable to send password reset email.")
        }
    }

    /**
     * Restores a persisted session on launch, refreshing the JWT if expired and clearing
     * revoked/invalid sessions so protected routes remain secure.
     */
    suspend fun restoreOrRefreshSession(): UserSession? = withContext(Dispatchers.IO) {
        val current = sessionStore.currentSession() ?: return@withContext null
        if (!isConfiguredProvider()) return@withContext current

        if (!current.isExpiredOrExpiring()) {
            val verified = runCatching {
                authApi.getCurrentUser("Bearer ${current.accessToken}")
            }.getOrNull()
            if (verified != null && verified.isSuccessful) {
                return@withContext current
            }
            if (verified != null && verified.code() != 401 && verified.code() != 403) {
                // Transient server issue: keep persisted session until online/recovered
                return@withContext current
            }
        }

        if (current.refreshToken.isBlank()) {
            sessionStore.clearSession()
            return@withContext null
        }

        val refreshResult = runCatching {
            authApi.refreshSession(request = SupabaseRefreshRequest(current.refreshToken))
        }.getOrNull()

        return@withContext when {
            refreshResult == null -> current // Offline; keep cached session until online
            refreshResult.isSuccessful -> {
                val refreshed = refreshResult.body()?.toUserSession(current.email)
                if (refreshed != null) {
                    sessionStore.saveSession(refreshed)
                    refreshed
                } else {
                    current
                }
            }
            refreshResult.code() in listOf(400, 401, 403) -> {
                sessionStore.clearSession()
                null
            }
            else -> current
        }
    }

    /**
     * Ensures the active session has a non-expired access token before making authenticated
     * PostgREST or Storage requests. Refreshes via refresh_token if expired or forced.
     */
    suspend fun ensureValidSession(forceRefresh: Boolean = false): UserSession? =
        withContext(Dispatchers.IO) {
            val current = sessionStore.currentSession() ?: return@withContext null
            if (!isConfiguredProvider()) return@withContext current
            if (!forceRefresh && !current.isExpiredOrExpiring()) {
                return@withContext current
            }
            if (current.refreshToken.isBlank()) {
                if (forceRefresh) {
                    sessionStore.clearSession()
                    return@withContext null
                }
                return@withContext current
            }
            val refreshResult = runCatching {
                authApi.refreshSession(request = SupabaseRefreshRequest(current.refreshToken))
            }.getOrNull()
            return@withContext when {
                refreshResult == null -> current
                refreshResult.isSuccessful -> {
                    val refreshed = refreshResult.body()?.toUserSession(current.email)
                    if (refreshed != null) {
                        sessionStore.saveSession(refreshed)
                        refreshed
                    } else {
                        current
                    }
                }
                refreshResult.code() in listOf(400, 401, 403) -> {
                    sessionStore.clearSession()
                    null
                }
                else -> current
            }
        }

    suspend fun signOut() = withContext(Dispatchers.IO) {
        val current = sessionStore.currentSession()
        if (isConfiguredProvider() && current != null) {
            runCatching {
                authApi.signOut("Bearer ${current.accessToken}")
            }
        }
        sessionStore.clearSession()
    }

    private fun SupabaseAuthResponse.toUserSession(fallbackEmail: String): UserSession? {
        val token = accessToken?.takeIf { it.isNotBlank() } ?: return null
        val activeUser = user ?: return null
        val computedExpiry = expiresAt ?: expiresIn?.let {
            (System.currentTimeMillis() / 1000L) + it
        }
        return UserSession(
            accessToken = token,
            refreshToken = refreshToken.orEmpty(),
            userId = activeUser.id,
            email = activeUser.email ?: fallbackEmail,
            expiresAtEpochSec = computedExpiry
        )
    }

    private fun parseAuthError(response: Response<*>): String {
        val raw = runCatching { response.errorBody()?.string() }.getOrNull()
        return parseAuthErrorRaw(raw, response.code())
    }

    private fun parseAuthErrorRaw(raw: String?, statusCode: Int): String {
        var extracted: String? = null
        if (!raw.isNullOrBlank()) {
            val adapter = moshi.adapter(SupabaseErrorDto::class.java)
            val parsed = runCatching { adapter.fromJson(raw) }.getOrNull()
            extracted = parsed?.errorDescription ?: parsed?.msg ?: parsed?.message ?: parsed?.error
        }
        return AuthValidator.mapSupabaseAuthError(extracted, statusCode)
    }
}
