package com.owari.launcher

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.Log

/**
 * "Una app a la vez": la TV tiene 1 GB de memoria, así que cada vez que cambia lo que
 * está en pantalla (otra app, la TV, el inicio, un sitio web) se cierra todo lo demás,
 * menos el launcher y sus servicios (Wi-Fi y bloqueador de anuncios).
 */
object RamCleaner {
    private const val TAG = "RamCleaner"
    private const val PREFS = "ajustes"
    private const val KEY = "una_app_a_la_vez"

    /** Marca para "lo que está en pantalla es un sitio web": no se cierra el motor web. */
    const val WEB = ":web"

    private var lastForeground: String? = null

    fun isEnabled(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, true)

    fun setEnabled(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, on).apply()
    }

    private val handler = Handler(Looper.getMainLooper())

    /**
     * Lo llama [ForegroundWatcherService] cada vez que una app pasa al frente. Se espera un
     * momento (la app anterior tarda en salir de la pantalla y mientras tanto no se puede
     * cerrar) y se repite unos segundos después por si alguna quedó.
     */
    fun onForeground(context: Context, now: String) {
        if (now == lastForeground) return
        lastForeground = now
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ freeFor(context, now) }, 1_500)
        handler.postDelayed({ freeFor(context, now) }, 5_000)
    }

    /**
     * Cierra lo que esté en segundo plano, menos [keep]: el paquete que está o va a estar
     * en pantalla, [WEB] para un sitio web, o el propio launcher.
     */
    fun freeFor(context: Context, keep: String) {
        if (!isEnabled(context)) return
        val am = context.getSystemService(ActivityManager::class.java)
        val own = context.packageName

        // Apps de terceros que quedaron abiertas (solo se cierran si están en segundo plano).
        val packages = mutableSetOf<String>()
        for (category in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            context.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), 0)
                .forEach { packages += it.activityInfo.packageName }
        }
        packages.remove(own)
        packages.remove(keep)
        packages.forEach { runCatching { am.killBackgroundProcesses(it) } }

        // El motor web (Firefox) corre en procesos propios de la app: se cierran si no se está usando.
        // Nunca mientras el navegador esté en pantalla, aunque algo más haya avisado que pasó al frente.
        var webClosed = 0
        val processes = am.runningAppProcesses.orEmpty()
        val browserVisible = processes.any {
            it.processName == "$own$WEB" && it.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE
        }
        if (keep != WEB && !browserVisible) {
            processes.forEach { info ->
                if (info.pid != Process.myPid() && info.processName.startsWith("$own:")) {
                    Process.killProcess(info.pid)
                    webClosed++
                }
            }
        }
        Log.i(TAG, "En pantalla: $keep. Se cerraron las demás apps" + if (webClosed > 0) " y el motor web" else "")
    }
}
