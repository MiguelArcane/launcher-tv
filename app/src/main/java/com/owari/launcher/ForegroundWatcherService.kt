package com.owari.launcher

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.view.accessibility.AccessibilityEvent

/**
 * Avisa cuál app pasa al frente (otra app, la TV, el inicio, un sitio web) para que
 * "Una app a la vez" cierre las demás. Solo mira el nombre de la app; no lee el
 * contenido de las pantallas. Se activa una vez desde el computador (ver README).
 */
class ForegroundWatcherService : AccessibilityService() {

    /** Ventanas que aparecen encima de una app sin reemplazarla (teclado, barra del sistema). */
    private val ignored = setOf("android", "com.android.systemui", "com.toptech.keyboard", "com.android.inputmethod.latin")

    /** Cache de "¿esta clase es una pantalla de verdad?" para no preguntarle a Android cada vez. */
    private val isActivity = HashMap<String, Boolean>()

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        val cls = event.className?.toString() ?: return
        if (pkg in ignored) return
        // Los avisos (Toast), diálogos y ventanitas también generan este evento; solo
        // cuentan las pantallas de verdad (Activity), si no un aviso cerraría lo que estás viendo.
        if (!isRealScreen(pkg, cls)) return
        val now = if (pkg == packageName && cls.endsWith(".BrowserActivity")) RamCleaner.WEB else pkg
        RamCleaner.onForeground(this, now)
    }

    private fun isRealScreen(pkg: String, cls: String): Boolean = isActivity.getOrPut("$pkg/$cls") {
        runCatching { packageManager.getActivityInfo(ComponentName(pkg, cls), 0) }.isSuccess
    }

    override fun onInterrupt() {}
}
