package com.owari.launcher.adblock

import android.content.Context

/** Dominios de anuncios y rastreadores (assets/anuncios.txt, de StevenBlack/hosts). */
class Blocklist private constructor(private val domains: HashSet<String>) {

    val size get() = domains.size

    /** Bloquea el dominio y todos sus subdominios: si está "ads.ejemplo.com", cae "x.ads.ejemplo.com". */
    fun isBlocked(name: String): Boolean {
        var host = name.lowercase().trimEnd('.')
        while (true) {
            if (host in domains) return true
            val dot = host.indexOf('.')
            if (dot < 0 || dot == host.length - 1) return false
            host = host.substring(dot + 1)
        }
    }

    companion object {
        @Volatile private var cached: Blocklist? = null

        /** Se carga una sola vez y la comparten el bloqueador y el navegador. */
        fun get(context: Context): Blocklist = cached ?: synchronized(this) {
            cached ?: load(context.applicationContext).also { cached = it }
        }

        private fun load(context: Context): Blocklist {
            val set = HashSet<String>(80_000)
            context.assets.open("anuncios.txt").bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    val d = line.trim()
                    if (d.isNotEmpty() && !d.startsWith("#")) set += d
                }
            }
            return Blocklist(set)
        }
    }
}
