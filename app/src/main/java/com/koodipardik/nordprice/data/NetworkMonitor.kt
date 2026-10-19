package com.koodipardik.nordprice.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

/**
 * Connectivity, the equivalent of the iOS `NWPathMonitor`. Emits on every change and always
 * has the current state as its first value, so the UI can show the "no connection" screen.
 */
class NetworkMonitor(context: Context) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    val isOnline: Flow<Boolean> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { trySend(true) }
            override fun onLost(network: Network) { trySend(false) }
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                trySend(capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
            }
        }
        trySend(currentlyOnline())
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, callback)
        awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
    }.conflate()

    /**
     * "Is there a route to the internet", which is what iOS's `path.status == .satisfied` means.
     *
     * NET_CAPABILITY_VALIDATED is deliberately not required: it means Google's connectivity probe
     * succeeded, and on a network with a TLS-intercepting proxy that can stay false while the
     * price API is perfectly reachable. Requiring it would show the "no connection" screen for a
     * user whose app is working. A failed request is reported by the fetch itself.
     */
    fun currentlyOnline(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
