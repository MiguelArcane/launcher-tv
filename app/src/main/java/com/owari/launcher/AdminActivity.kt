package com.owari.launcher

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.wifi.ScanResult
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
 * Pantalla de administración: prender/apagar el ADB por red, ver la señal de las
 * redes Wi-Fi cercanas y cambiar el color de fondo de la pantalla de inicio.
 */
class AdminActivity : Activity() {

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
