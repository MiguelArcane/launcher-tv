package com.owari.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Arranca el vigilante de Wi-Fi y el bloqueador de anuncios al prender la TV o al actualizar la app. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        WifiGuardService.start(context)
        com.owari.launcher.adblock.AdBlockVpnService.startIfEnabled(context)
    }
}
