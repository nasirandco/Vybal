package com.example.core.di

import android.content.Context
import coil.ImageLoader
import com.example.core.network.SupabaseClientBundle
import com.example.core.network.SupabaseClientFactory
import com.example.core.performance.NetworkMonitor
import com.example.core.performance.VybalImageLoader
import com.example.data.auth.SessionStore
import com.example.data.auth.SupabaseAuthRepository
import com.example.data.social.VybalRepository

/**
 * Lightweight manual dependency container (constructor injection) — avoids heavy reflection
 * frameworks to keep startup fast and memory usage low on 2GB RAM Android phones.
 */
class VybalAppContainer(context: Context) {
    private val appContext = context.applicationContext

    val sessionStore: SessionStore by lazy { SessionStore(appContext) }
    val networkMonitor: NetworkMonitor by lazy { NetworkMonitor(appContext) }
    val supabaseClients: SupabaseClientBundle by lazy { SupabaseClientFactory.create(appContext) }
    val imageLoader: ImageLoader by lazy {
        VybalImageLoader.create(appContext, supabaseClients.okHttpClient)
    }
    val authRepository: SupabaseAuthRepository by lazy {
        SupabaseAuthRepository(supabaseClients, sessionStore)
    }
    val vybalRepository: VybalRepository by lazy {
        VybalRepository(
            clients = supabaseClients,
            sessionStore = sessionStore,
            validSessionProvider = { force -> authRepository.ensureValidSession(force) }
        )
    }
    val engagementRepository: com.example.data.social.EngagementRepository by lazy {
        com.example.data.social.EngagementRepository(
            clients = supabaseClients,
            sessionStore = sessionStore,
            validSessionProvider = { force -> authRepository.ensureValidSession(force) }
        )
    }
    val adminRepository: com.example.data.admin.AdminRepository by lazy {
        com.example.data.admin.AdminRepository(
            clients = supabaseClients,
            sessionStore = sessionStore,
            validSessionProvider = { force -> authRepository.ensureValidSession(force) }
        )
    }
}
