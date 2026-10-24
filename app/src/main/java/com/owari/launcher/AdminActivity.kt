package com.owari.launcher

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.VpnService
import android.net.wifi.ScanResult
import com.owari.launcher.adblock.AdBlockVpnService
import android.net.wifi.WifiManager
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

/**
 * Pantalla de administración: prender/apagar el ADB por red y el bloqueo de anuncios,
 * ver la señal de las redes Wi-Fi cercanas y cambiar el color de fondo del inicio.
 */
class AdminActivity : Activity() {

    companion object {
        private const val REQUEST_VPN = 2
    }

    private lateinit var palette: Palette
    private lateinit var wifi: WifiManager

    private lateinit var adbSwitch: Switch
    private lateinit var adbStatus: TextView
    private lateinit var scanStatus: TextView
    private lateinit var networkList: LinearLayout

    private val scanDone = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = showNetworks()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        palette = Palette.current(this)
        wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        window.setBackgroundDrawable(null)
        setContentView(buildScreen())
    }

    override fun onResume() {
        super.onResume()
        refreshAdb()
        AdBlockVpnService.startIfEnabled(this)
        refreshAdBlock()
        adBlockStatus.postDelayed({ refreshAdBlock() }, 2_000)
        registerReceiver(scanDone, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))
        scan()
    }

    override fun onPause() {
        unregisterReceiver(scanDone)
        super.onPause()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) scan()
    }

    // ---------- Pantalla ----------

    private fun buildScreen(): View {
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(48), dp(28), dp(48), dp(28))
        }
        column.addView(label(30f, palette.text, bold = true).apply { text = "Administración" })
        column.addView(label(15f, palette.textSoft).apply {
            text = "Presiona Atrás en el control para volver al inicio."
        })

        column.addView(adbSection(), spaced(20))
        column.addView(adBlockSection(), spaced(16))
        column.addView(oneAppSection(), spaced(16))
        column.addView(sitesSection(), spaced(16))
        column.addView(wifiSection(), spaced(16))
        column.addView(colorSection(), spaced(16))

        return FrameLayout(this).apply {
            setBackgroundColor(palette.background)
            addView(ScrollView(this@AdminActivity).apply {
                isVerticalScrollBarEnabled = false
                addView(column)
            })
        }
    }

    private fun card(title: String): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(24), dp(18), dp(24), dp(18))
        background = rounded(palette.soft, dp(16).toFloat())
        addView(label(20f, palette.text, bold = true).apply { text = title })
    }

    /** Fila que se puede seleccionar con el control y muestra un borde al estar seleccionada. */
    private fun selectable(view: View, onClick: () -> Unit): View = view.apply {
        isFocusable = true
        isClickable = true
        background = rounded(Color.TRANSPARENT, dp(12).toFloat())
        setOnFocusChangeListener { v, focused ->
            (v.background as GradientDrawable).setStroke(if (focused) dp(3) else 0, palette.text)
        }
        setOnClickListener { onClick() }
    }

    // ---------- ADB por red ----------

    private fun adbSection(): View = card("Conexión ADB por red").apply {
        addView(label(14f, palette.textSoft).apply {
            text = "Permite controlar la TV desde el computador. Apágalo cuando no lo estés usando: " +
                "mientras está prendido, cualquier equipo de tu Wi-Fi puede conectarse a la TV."
        }, spaced(4))

        val row = LinearLayout(this@AdminActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        row.addView(label(18f, palette.text, bold = true).apply { text = "ADB por red" },
            LinearLayout.LayoutParams(0, -2, 1f))
        adbSwitch = Switch(this@AdminActivity).apply {
            isFocusable = false
            isClickable = false
            scaleX = 1.3f
            scaleY = 1.3f
        }
        row.addView(adbSwitch, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(12) })
        addView(selectable(row) { setAdb(!adbSwitch.isChecked) }, spaced(10))

        adbStatus = label(14f, palette.textSoft)
        addView(adbStatus, spaced(6))
    }

    private fun adbOn(): Boolean =
        Settings.Global.getInt(contentResolver, Settings.Global.ADB_ENABLED, 0) == 1

    private fun setAdb(on: Boolean) {
        try {
            Settings.Global.putInt(contentResolver, Settings.Global.ADB_ENABLED, if (on) 1 else 0)
        } catch (e: SecurityException) {
            adbStatus.text = "La app no tiene permiso para cambiar esto. Hay que dárselo una vez desde el computador."
            return
        }
        refreshAdb()
    }

    @Suppress("DEPRECATION")
    private fun refreshAdb() {
        val on = adbOn()
        adbSwitch.isChecked = on
        adbStatus.text = if (on) {
            val ip = wifi.connectionInfo?.ipAddress ?: 0
            val address = if (ip == 0) "la IP de la TV" else
                "${ip and 0xff}.${ip shr 8 and 0xff}.${ip shr 16 and 0xff}.${ip shr 24 and 0xff}"
            "Prendido: se puede conectar a $address:5555"
        } else {
            "Apagado: nadie puede conectarse a la TV por la red."
        }
    }

    // ---------- Bloqueo de anuncios ----------

    private lateinit var adBlockSwitch: Switch
    private lateinit var adBlockStatus: TextView

    private fun adBlockSection(): View = card("Bloqueo de anuncios").apply {
        addView(label(14f, palette.textSoft).apply {
            text = "Bloqueador propio con una lista de más de 70.000 dominios de anuncios y rastreadores, " +
                "para toda la TV. Funciona como una VPN que no sale de la TV. " +
                "No quita los anuncios de YouTube: esos vienen del mismo lugar que los videos."
        }, spaced(4))

        val row = LinearLayout(this@AdminActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        row.addView(label(18f, palette.text, bold = true).apply { text = "Bloquear anuncios" },
            LinearLayout.LayoutParams(0, -2, 1f))
        adBlockSwitch = Switch(this@AdminActivity).apply {
            isFocusable = false
            isClickable = false
            scaleX = 1.3f
            scaleY = 1.3f
        }
        row.addView(adBlockSwitch, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(12) })
        addView(selectable(row) { setAdBlock(!adBlockOn()) }, spaced(10))

        adBlockStatus = label(14f, palette.textSoft)
        addView(adBlockStatus, spaced(6))
    }

    private fun adBlockOn(): Boolean = AdBlockVpnService.isEnabled(this)

    private fun setAdBlock(on: Boolean) {
        if (!on) {
            AdBlockVpnService.setEnabled(this, false)
            AdBlockVpnService.stop(this)
            refreshAdBlock()
            return
        }
        // La primera vez Android pregunta si se permite crear la VPN.
        val consent = VpnService.prepare(this)
        if (consent != null) {
            try {
                adBlockStatus.text = "Acepta el aviso de Android para activar el bloqueador."
                startActivityForResult(consent, REQUEST_VPN)
            } catch (e: ActivityNotFoundException) {
                // Algunas TV no traen la ventana de permiso de VPN. El permiso se da una vez con:
                // adb shell appops set com.owari.launcher ACTIVATE_VPN allow
                adBlockStatus.text = "Esta TV no tiene la ventana de permiso de VPN. " +
                    "Hay que dar el permiso una vez desde el computador."
            }
        } else {
            enableAdBlock()
        }
    }

    private fun enableAdBlock() {
        AdBlockVpnService.setEnabled(this, true)
        AdBlockVpnService.startIfEnabled(this)
        adBlockStatus.postDelayed({ refreshAdBlock() }, 1_500)
        refreshAdBlock()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_VPN) {
            if (resultCode == RESULT_OK) enableAdBlock()
            else adBlockStatus.text = "No se activó: hay que aceptar el aviso de Android."
        }
    }

    private fun refreshAdBlock() {
        val on = adBlockOn()
        adBlockSwitch.isChecked = on
        adBlockStatus.text = when {
            on && AdBlockVpnService.running ->
                "Prendido: ${AdBlockVpnService.blockedCount.get()} anuncios bloqueados desde que se prendió la TV."
            on -> "Prendido, arrancando…"
            else -> "Apagado: los anuncios no se bloquean."
        }
    }

    // ---------- Una app a la vez ----------

    private lateinit var oneAppSwitch: Switch

    private fun oneAppSection(): View = card("Una app a la vez").apply {
        addView(label(14f, palette.textSoft).apply {
            text = "Cada vez que cambias de lugar (otra app, la TV, el inicio o un sitio web) y al prender o " +
                "apagar la TV, se cierra todo lo demás menos el launcher, para que la TV tenga más memoria libre."
        }, spaced(4))
        val row = LinearLayout(this@AdminActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        row.addView(label(18f, palette.text, bold = true).apply { text = "Cerrar las otras apps" },
            LinearLayout.LayoutParams(0, -2, 1f))
        oneAppSwitch = Switch(this@AdminActivity).apply {
            isFocusable = false
            isClickable = false
            scaleX = 1.3f
            scaleY = 1.3f
            isChecked = RamCleaner.isEnabled(this@AdminActivity)
        }
        row.addView(oneAppSwitch, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(12) })
        addView(selectable(row) {
            RamCleaner.setEnabled(this@AdminActivity, !RamCleaner.isEnabled(this@AdminActivity))
            oneAppSwitch.isChecked = RamCleaner.isEnabled(this@AdminActivity)
        }, spaced(10))
    }

    // ---------- Sitios web ----------

    private fun sitesSection(): View = card("Sitios web en Favoritos").apply {
        addView(label(14f, palette.textSoft).apply {
            text = "Cada sitio guardado aparece en Favoritos con su ícono y se abre sin ventanas emergentes ni anuncios."
        }, spaced(4))
        val open = label(16f, palette.text, bold = true).apply {
            text = "🌐  Agregar o quitar sitios"
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        addView(selectable(open) {
            startActivity(Intent(this@AdminActivity, com.owari.launcher.web.WebSitesActivity::class.java))
        }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(8) })
    }

    // ---------- Redes Wi-Fi ----------

    private fun wifiSection(): View = card("Redes Wi-Fi cercanas").apply {
        addView(label(14f, palette.textSoft).apply {
            text = "Entre más barras, más cerca o más fuerte llega esa red a la TV."
        }, spaced(4))

        val refresh = label(16f, palette.text, bold = true).apply {
            text = "↻  Buscar de nuevo"
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        addView(selectable(refresh) { scan() }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(8) })

        scanStatus = label(14f, palette.textSoft)
        addView(scanStatus, spaced(4))
        networkList = LinearLayout(this@AdminActivity).apply { orientation = LinearLayout.VERTICAL }
        addView(networkList, spaced(4))
    }

    @Suppress("DEPRECATION")
    private fun scan() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            scanStatus.text = "Hace falta el permiso de ubicación para ver las redes."
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
            return
        }
        if (!wifi.isWifiEnabled) {
            scanStatus.text = "El Wi-Fi está apagado."
            return
        }
        scanStatus.text = "Buscando redes…"
        if (!wifi.startScan()) showNetworks()
    }

    @Suppress("DEPRECATION")
    private fun showNetworks() {
        val results = runCatching { wifi.scanResults }.getOrDefault(emptyList())
        val connected = wifi.connectionInfo?.bssid
        // Una fila por nombre de red, quedándose con la señal más fuerte.
        val best = results
            .groupBy { it.SSID.ifBlank { "(red oculta)" } + " " + band(it) }
            .map { (_, list) -> list.maxByOrNull { it.level }!! }
            .sortedByDescending { it.level }

        networkList.removeAllViews()
        scanStatus.text = if (best.isEmpty()) "No se encontraron redes." else "${best.size} redes encontradas"
        best.take(15).forEach { networkList.addView(networkRow(it, it.BSSID == connected)) }
    }

    private fun band(result: ScanResult) = if (result.frequency > 4900) "5 GHz" else "2.4 GHz"

    @Suppress("DEPRECATION")
    private fun networkRow(result: ScanResult, isCurrent: Boolean): View {
        val bars = WifiManager.calculateSignalLevel(result.level, 5) // 0..4
        val quality = when {
            result.level >= -55 -> "Excelente"
            result.level >= -67 -> "Buena"
            result.level >= -75 -> "Regular"
            else -> "Débil"
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        val name = result.SSID.ifBlank { "(red oculta)" }
        row.addView(label(16f, palette.text, bold = isCurrent).apply {
            text = if (isCurrent) "$name  ·  conectada" else name
            maxLines = 1
        }, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(label(13f, palette.textSoft).apply {
            text = band(result)
            setPadding(dp(12), 0, dp(12), 0)
        })
        row.addView(signalBars(bars + 1), LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(14) })
        row.addView(label(13f, palette.textSoft).apply {
            text = "$quality  ${result.level} dBm"
        }, LinearLayout.LayoutParams(dp(130), -2))
        return selectable(row) {}
    }

    /** Cinco barritas de altura creciente; las que no llegan se ven tenues. */
    private fun signalBars(filled: Int): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.BOTTOM
        for (i in 1..5) {
            addView(View(this@AdminActivity).apply {
                background = rounded(palette.text, dp(1).toFloat())
                alpha = if (i <= filled) 1f else 0.25f
            }, LinearLayout.LayoutParams(dp(5), dp(4 + i * 3)).apply { leftMargin = dp(3) })
        }
    }

    // ---------- Color de fondo ----------

    private fun colorSection(): View = card("Color de fondo").apply {
        val row = LinearLayout(this@AdminActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(10), 0, 0)
        }
        Palette.ALL.forEach { option -> row.addView(colorChoice(option)) }
        addView(row)
    }

    private fun colorChoice(option: Palette): View {
        val selected = option.key == palette.key
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
        }
        val circle = TextView(this).apply {
            background = oval(option.background).apply {
                setStroke(dp(2), if (selected) palette.text else Color.parseColor("#33000000"))
            }
            gravity = Gravity.CENTER
            text = if (selected) "✓" else ""
            setTextColor(option.text)
            textSize = 20f
        }
        box.addView(circle, LinearLayout.LayoutParams(dp(56), dp(56)))
        box.addView(label(13f, palette.text, bold = selected).apply {
            text = option.name
            gravity = Gravity.CENTER
        }, spaced(6))
        return selectable(box) {
            Palette.save(this, option)
            recreate()
        }
    }

    private fun spaced(top: Int) = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top) }
}
