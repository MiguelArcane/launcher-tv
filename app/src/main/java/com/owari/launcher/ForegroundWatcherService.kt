package com.owari.launcher

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * Avisa cuál app pasa al frente (otra app, la TV, el inicio, un sitio web) para que
 * "Una app a la vez" cierre las demás. Solo mira el nombre de la app; no lee el
 * contenido de las pantallas. Se activa una vez desde el computador (ver README).
 */
class ForegroundWatcherService : AccessibilityService() {

    /** Ventanas que aparecen encima de una app sin reemplazarla (teclado, barra del sistema). */
    private val ignored = setOf("android", "com.android.systemui", "com.toptech.keyboard", "com.android.inputmethod.latin")

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg in ignored) return
        val cls = event.className?.toString() ?: ""
        val now = if (pkg == packageName && cls.endsWith(".BrowserActivity")) RamCleaner.WEB else pkg
        RamCleaner.onForeground(this, now)
    }

    override fun onInterrupt() {}
}
