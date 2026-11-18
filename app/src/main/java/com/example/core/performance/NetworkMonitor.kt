package com.example.core.performance

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

data class NetworkStatus(
    val isOnline: Boolean = true,
    val isMetered: Boolean = false
)

/**
 * Lightweight network observer built on Android ConnectivityManager so VYBAL
 * adapts gracefully on slow or intermittent mobile connections.
 */
class NetworkMonitor(context: Context) {
    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    fun observeStatus(): Flow<NetworkStatus> = callbackFlow {
        fun emitCurrent() {
            val activeNetwork = connectivityManager.activeNetwork
            val caps = activeNetwork?.let { connectivityManager.getNetworkCapabilities(it) }
            val online = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
            val metered = connectivityManager.isActiveNetworkMetered
            trySend(NetworkStatus(isOnline = online, isMetered = metered))
        }

        emitCurrent()

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = emitCurrent()
            override fun onLost(network: Network) = emitCurrent()
            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) = emitCurrent()
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        runCatching { connectivityManager.registerNetworkCallback(request, callback) }

        awaitClose {
            runCatching { connectivityManager.unregisterNetworkCallback(callback) }
        }
    }.distinctUntilChanged()
}
