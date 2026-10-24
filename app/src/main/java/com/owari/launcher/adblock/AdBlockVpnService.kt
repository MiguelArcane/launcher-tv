package com.owari.launcher.adblock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.system.OsConstants
import android.util.Log
import com.owari.launcher.R
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Bloqueador de anuncios propio. Crea una VPN que NO sale de la TV y que solo recibe
 * las consultas de DNS. Si el dominio está en la lista de anuncios responde "no existe";
 * si no, pregunta al DNS de tu internet. El resto del tráfico no pasa por aquí.
 */
class AdBlockVpnService : VpnService() {

    companion object {
        private const val TAG = "AdBlock"
        private const val CHANNEL = "adblock"
        private const val VPN_ADDRESS = "10.111.222.1"
        private const val VPN_DNS = "10.111.222.2"
        private const val PREFS = "ajustes"
        private const val KEY_ON = "bloqueo_anuncios"
        const val ACTION_STOP = "com.owari.launcher.ADBLOCK_STOP"

        /** Cuántas consultas de anuncios se han bloqueado desde que arrancó. */
        val blockedCount = AtomicInteger(0)
        @Volatile var running = false
            private set

        fun isEnabled(context: Context) =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ON, false)

        fun setEnabled(context: Context, on: Boolean) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ON, on).apply()
        }

        /** Arranca solo si está prendido y Android ya dio permiso para la VPN. */
        fun startIfEnabled(context: Context) {
            if (!isEnabled(context) || prepare(context) != null) return
            context.startForegroundService(Intent(context, AdBlockVpnService::class.java))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, AdBlockVpnService::class.java).setAction(ACTION_STOP))
        }
    }

    private var tun: ParcelFileDescriptor? = null
    private var reader: Thread? = null
    private val upstreamPool = Executors.newFixedThreadPool(4)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            shutdown()
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(2, notification())
        if (tun == null) start()
        return START_STICKY
    }

    override fun onRevoke() {
        // Otra VPN tomó el lugar o la quitaron desde los ajustes de Android.
        setEnabled(this, false)
        shutdown()
        stopSelf()
    }

    override fun onDestroy() {
        shutdown()
        upstreamPool.shutdownNow()
        super.onDestroy()
    }

    private fun start() {
        val blocklist = Blocklist.get(this)
        val upstream = upstreamDns()
        val fd = Builder()
            .setSession("Bloqueo de anuncios")
            .addAddress(VPN_ADDRESS, 32)
            .addRoute(VPN_DNS, 32)
            .addDnsServer(VPN_DNS)
            // Sin esto Android corta todo el IPv6 mientras la VPN está activa.
            .allowFamily(OsConstants.AF_INET6)
            .setBlocking(true)
            .establish() ?: return
        tun = fd
        running = true
        Log.i(TAG, "Bloqueador activo: ${blocklist.size} dominios, DNS de salida $upstream")
        reader = Thread({ readLoop(fd, blocklist, upstream) }, "adblock-dns").apply { start() }
    }

    private fun shutdown() {
        running = false
        reader?.interrupt()
        reader = null
        runCatching { tun?.close() }
        tun = null
        stopForeground(true)
    }

    /** El DNS que usa tu internet (el del router), o uno público si no se encuentra. */
    private fun upstreamDns(): InetAddress {
        val cm = getSystemService(ConnectivityManager::class.java)
        for (network in cm.allNetworks) {
            val caps = cm.getNetworkCapabilities(network) ?: continue
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) continue
            cm.getLinkProperties(network)?.dnsServers?.firstOrNull { it is Inet4Address }?.let { return it }
        }
        return InetAddress.getByName("1.1.1.1")
    }

    private fun readLoop(fd: ParcelFileDescriptor, blocklist: Blocklist, upstream: InetAddress) {
        val input = FileInputStream(fd.fileDescriptor)
        val output = FileOutputStream(fd.fileDescriptor)
        val buffer = ByteArray(32_767)
        while (!Thread.currentThread().isInterrupted) {
            val length = try { input.read(buffer) } catch (e: Exception) { break }
            if (length <= 0) continue
            val query = DnsPacket.parse(buffer, length) ?: continue
            if (blocklist.isBlocked(query.name)) {
                blockedCount.incrementAndGet()
                write(output, query.reply(DnsPacket.blockedAnswer(query.dns)))
            } else {
                upstreamPool.execute { forward(output, query, upstream) }
            }
        }
    }

    private fun forward(output: FileOutputStream, query: DnsPacket, upstream: InetAddress) {
        runCatching {
            DatagramSocket().use { socket ->
                protect(socket) // la consulta de salida no vuelve a entrar a la VPN
                socket.soTimeout = 5_000
                socket.send(DatagramPacket(query.dns, query.dns.size, upstream, 53))
                val answer = ByteArray(4_096)
                val packet = DatagramPacket(answer, answer.size)
                socket.receive(packet)
                write(output, query.reply(answer.copyOf(packet.length)))
            }
        }
    }

    private fun write(output: FileOutputStream, packet: ByteArray) {
        synchronized(output) { runCatching { output.write(packet) } }
    }

    private fun notification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Bloqueo de anuncios", NotificationManager.IMPORTANCE_MIN))
        return Notification.Builder(this, CHANNEL)
            .setContentTitle("Bloqueando anuncios")
            .setSmallIcon(R.drawable.ic_wifi)
            .setOngoing(true)
            .build()
    }
}
