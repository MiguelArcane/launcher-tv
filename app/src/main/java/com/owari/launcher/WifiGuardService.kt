package com.owari.launcher

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log

/** Estado del Wi-Fi que muestra la pantalla de inicio. */
sealed class WifiState {
    data class Connected(val name: String?) : WifiState()
    data class Reconnecting(val attempt: Int, val max: Int) : WifiState()
    object GaveUp : WifiState()
}

object WifiStatus {
    @Volatile var current: WifiState = WifiState.Connected(null)
        private set
    var listener: ((WifiState) -> Unit)? = null
    private val main = Handler(Looper.getMainLooper())

    fun publish(state: WifiState) {
        if (state == current) return
        current = state
        main.post { listener?.invoke(state) }
    }
}

/**
 * Vigila la conexión y, si la TV se queda sin red, intenta volver a conectarse sola:
 * primero pide reconectar, después apaga y prende el Wi-Fi (así Android vuelve a
 * conectarse a la red guardada). Si tras varios intentos no lo logra, espera y reintenta.
 */
class WifiGuardService : Service() {

    companion object {
        private const val TAG = "WifiGuard"
        private const val CHANNEL = "wifi_guard"
        private const val CHECK_EVERY_MS = 15_000L
        private const val GRACE_MS = 10_000L
        private const val WAIT_AFTER_ATTEMPT_MS = 25_000L
        private const val RETRY_AFTER_GIVING_UP_MS = 5 * 60_000L
        const val MAX_ATTEMPTS = 5
        const val ACTION_RECONNECT_NOW = "com.owari.launcher.RECONNECT_NOW"

        fun start(context: Context) {
            val intent = Intent(context, WifiGuardService::class.java)
            context.startForegroundService(intent)
        }

        fun reconnectNow(context: Context) {
            val intent = Intent(context, WifiGuardService::class.java).setAction(ACTION_RECONNECT_NOW)
            context.startForegroundService(intent)
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var wifi: WifiManager
    private lateinit var connectivity: ConnectivityManager

    private var offlineSince = 0L
    private var attempts = 0
    private var nextAttemptAt = 0L

    private val tick = object : Runnable {
        override fun run() {
            check()
            handler.postDelayed(this, CHECK_EVERY_MS)
        }
    }

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { handler.post { check() } }
        override fun onLost(network: Network) { handler.post { check() } }
    }

    override fun onCreate() {
        super.onCreate()
        wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        connectivity = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        startForeground(1, buildNotification())
        connectivity.registerNetworkCallback(
            NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(),
            callback
        )
        handler.post(tick)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_RECONNECT_NOW && !isOnline()) {
            attempts = 0
            nextAttemptAt = 0
            offlineSince = SystemClock.elapsedRealtime() - GRACE_MS
            handler.post { check() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        runCatching { connectivity.unregisterNetworkCallback(callback) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /** Hay red si hay Wi-Fi o cable conectado. */
    private fun isOnline(): Boolean = connectivity.allNetworks.any { network ->
        val caps = connectivity.getNetworkCapabilities(network) ?: return@any false
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    @Suppress("DEPRECATION")
    private fun currentSsid(): String? {
        val ssid = wifi.connectionInfo?.ssid ?: return null
        if (ssid.isBlank() || ssid.contains("unknown ssid")) return null
        return ssid.trim('"')
    }

    private fun check() {
        val now = SystemClock.elapsedRealtime()
        if (isOnline()) {
            if (attempts > 0) Log.i(TAG, "Conectado otra vez tras $attempts intento(s)")
            offlineSince = 0
            attempts = 0
            nextAttemptAt = 0
            WifiStatus.publish(WifiState.Connected(currentSsid()))
            return
        }

        if (offlineSince == 0L) offlineSince = now
        if (now - offlineSince < GRACE_MS || now < nextAttemptAt) return

        if (attempts >= MAX_ATTEMPTS) {
            // Se cansó: avisa y vuelve a probar dentro de un rato.
            WifiStatus.publish(WifiState.GaveUp)
            attempts = 0
            nextAttemptAt = now + RETRY_AFTER_GIVING_UP_MS
            return
        }

        attempts++
        WifiStatus.publish(WifiState.Reconnecting(attempts, MAX_ATTEMPTS))
        tryReconnect(attempts)
        nextAttemptAt = now + WAIT_AFTER_ATTEMPT_MS
    }

    @Suppress("DEPRECATION")
    private fun tryReconnect(attempt: Int) {
        if (!wifi.isWifiEnabled) {
            Log.i(TAG, "Intento $attempt: el Wi-Fi estaba apagado, lo prendo")
            wifi.isWifiEnabled = true
            return
        }
        if (attempt % 2 == 1) {
            Log.i(TAG, "Intento $attempt: pido reconectar")
            wifi.reconnect()
        } else {
            Log.i(TAG, "Intento $attempt: apago y prendo el Wi-Fi")
            wifi.isWifiEnabled = false
            handler.postDelayed({ wifi.isWifiEnabled = true }, 3_000)
        }
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Vigilante de Wi-Fi", NotificationManager.IMPORTANCE_MIN)
        )
        return Notification.Builder(this, CHANNEL)
            .setContentTitle("Vigilando el Wi-Fi")
            .setSmallIcon(R.drawable.ic_wifi)
            .setOngoing(true)
            .build()
    }
}
