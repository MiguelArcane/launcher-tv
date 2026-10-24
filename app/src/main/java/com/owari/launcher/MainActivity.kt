package com.owari.launcher

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Outline
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var palette: Palette

    private val spanish = Locale("es", "CO")
    private val timeFormat = SimpleDateFormat("H:mm", spanish)
    private val dateFormat = SimpleDateFormat("EEEE d 'de' MMMM", spanish)

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var clock: TextView
    private lateinit var date: TextView
    private lateinit var wifiChip: TextView
    private lateinit var overlay: LinearLayout
    private lateinit var overlayTitle: TextView
    private lateinit var overlayText: TextView
    private lateinit var favoritesRow: LinearLayout
    private lateinit var appsRow: LinearLayout

    private val clockTick = object : Runnable {
        override fun run() {
            val now = Date()
            clock.text = timeFormat.format(now)
            date.text = dateFormat.format(now).replaceFirstChar { it.titlecase(spanish) }
            handler.postDelayed(this, 10_000)
        }
    }

    private val packagesChanged = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = loadApps()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        palette = Palette.current(this)
        window.setBackgroundDrawable(null)
        setContentView(buildScreen())
        WifiGuardService.start(this)
        registerReceiver(packagesChanged, IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        })
        loadFavorites()
        loadApps()
        favoritesRow.getChildAt(0)?.requestFocus()
    }

    override fun onResume() {
        super.onResume()
        // Si cambiaron el color en ADMIN, se vuelve a dibujar la pantalla.
        if (Palette.current(this) != palette) {
            recreate()
            return
        }
        handler.post(clockTick)
        WifiStatus.listener = ::showWifiState
        showWifiState(WifiStatus.current)
    }

    override fun onPause() {
        handler.removeCallbacks(clockTick)
        WifiStatus.listener = null
        super.onPause()
    }

    override fun onDestroy() {
        unregisterReceiver(packagesChanged)
        super.onDestroy()
    }

    // Es la pantalla de inicio: "Atrás" no la cierra.
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {}

    // ---------- Pantalla ----------

    private fun buildScreen(): View {
        val root = FrameLayout(this).apply { setBackgroundColor(palette.background) }

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(48), dp(28), dp(48), dp(16))
        }
        root.addView(column, FrameLayout.LayoutParams(-1, -1))

        clock = label(64f, palette.text, bold = true).apply { includeFontPadding = false }
        date = label(18f, palette.textSoft)
        column.addView(clock)
        column.addView(date)

        column.addView(sectionTitle("Favoritos"), marginTop(20))
        favoritesRow = row()
        column.addView(scroller(favoritesRow))

        column.addView(sectionTitle("Todas las apps"), marginTop(4))
        appsRow = row()
        column.addView(scroller(appsRow))

        wifiChip = label(14f, palette.onStrong).apply {
            setPadding(dp(14), dp(6), dp(14), dp(6))
        }
        root.addView(wifiChip, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply {
            setMargins(0, dp(32), dp(48), 0)
        })

        overlay = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(28), dp(20), dp(28), dp(20))
            background = rounded(Color.parseColor("#2B2100"), dp(16).toFloat())
            visibility = View.GONE
            elevation = dp(8).toFloat()
        }
        overlayTitle = label(20f, Color.parseColor("#F6C90E"), bold = true).apply { gravity = Gravity.CENTER }
        overlayText = label(14f, Color.parseColor("#FFF3B0")).apply { gravity = Gravity.CENTER }
        overlay.addView(overlayTitle)
        overlay.addView(overlayText, marginTop(6))
        root.addView(overlay, FrameLayout.LayoutParams(dp(460), -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
            topMargin = dp(150)
        })
        return root
    }

    private fun showWifiState(state: WifiState) {
        when (state) {
            is WifiState.Connected -> {
                wifiChip.text = "●  " + (state.name ?: "Wi-Fi conectado")
                wifiChip.setTextColor(palette.onStrong)
                wifiChip.background = rounded(palette.strong, dp(20).toFloat())
                overlay.visibility = View.GONE
            }
            is WifiState.Reconnecting -> {
                showOffline()
                overlayTitle.text = "Reconectando el Wi-Fi…"
                overlayText.text = "Intento ${state.attempt} de ${state.max}. No hace falta tocar nada."
            }
            WifiState.GaveUp -> {
                showOffline()
                overlayTitle.text = "No se pudo reconectar"
                overlayText.text = "Lo vuelvo a intentar en 5 minutos.\nTambién puedes entrar al botón Wi-Fi de Favoritos."
            }
        }
    }

    private fun showOffline() {
        wifiChip.text = "●  Sin Wi-Fi"
        wifiChip.setTextColor(Color.WHITE)
        wifiChip.background = rounded(palette.alert, dp(20).toFloat())
        overlay.visibility = View.VISIBLE
    }

    // ---------- Apps ----------

    private fun loadFavorites() {
        favoritesRow.removeAllViews()
        addAppTile(favoritesRow, "Netflix", "com.netflix.mediaclient", big = true)
        addAppTile(favoritesRow, "YouTube", "com.google.android.youtube.tv", big = true)
        favoritesRow.addView(tile("TV", big = true, background = palette.strong,
            icon = tinted(R.drawable.ic_tv, palette.onStrong)) {
            open(ComponentName("com.toptech.tvmenu", "com.toptech.tvmenu.TvActivity"))
        })
        favoritesRow.addView(tile("TV por internet", big = true, background = palette.strong, badge = "TV IP") {
            open(Intent(this, com.owari.launcher.iptv.IptvActivity::class.java))
        })
        favoritesRow.addView(tile("Sitios web", big = true, background = palette.strong, badge = "WEB") {
            open(Intent(this, com.owari.launcher.web.WebSitesActivity::class.java))
        })
        favoritesRow.addView(tile("Entradas HDMI", big = true, background = palette.strong, badge = "HDMI") {
            open(Intent("com.toptech.tv.action.TV_INPUTS"))
        })
        favoritesRow.addView(tile("Wi-Fi", big = true, background = palette.soft,
            icon = tinted(R.drawable.ic_wifi, palette.text)) {
            WifiGuardService.reconnectNow(this)
            open(Intent(Settings.ACTION_WIFI_SETTINGS))
        })
        favoritesRow.addView(tile("Administración", big = true, background = palette.strong, badge = "ADMIN") {
            open(Intent(this, AdminActivity::class.java))
        })
    }

    private fun tinted(res: Int, color: Int): Drawable? =
        getDrawable(res)?.mutate()?.apply { setColorFilter(color, PorterDuff.Mode.SRC_IN) }

    private fun addAppTile(row: LinearLayout, name: String, pkg: String, big: Boolean) {
        val icon = runCatching { packageManager.getApplicationIcon(pkg) }.getOrNull() ?: return
        row.addView(tile(name, big, Color.WHITE, icon = icon) { openPackage(pkg) })
    }

    private fun loadApps() {
        appsRow.removeAllViews()
        val hidden = setOf(
            packageName,
            "com.zeasn.toptech.titanos2.launcher",
            "com.spocky.projengmenu",
        )
        val found = linkedMapOf<String, Pair<String, Drawable>>()
        for (category in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            val query = Intent(Intent.ACTION_MAIN).addCategory(category)
            for (info in packageManager.queryIntentActivities(query, 0)) {
                val pkg = info.activityInfo.packageName
                if (pkg in hidden || pkg in found) continue
                found[pkg] = info.loadLabel(packageManager).toString() to info.loadIcon(packageManager)
            }
        }
        found.entries.sortedBy { it.value.first.lowercase() }.forEach { (pkg, value) ->
            appsRow.addView(tile(value.first, big = false, background = Color.WHITE, icon = value.second) {
                openPackage(pkg)
            })
        }
    }

    private fun openPackage(pkg: String): Boolean {
        val intent = packageManager.getLeanbackLaunchIntentForPackage(pkg)
            ?: packageManager.getLaunchIntentForPackage(pkg)
            ?: return false
        return open(intent)
    }

    private fun open(component: ComponentName): Boolean =
        open(Intent(Intent.ACTION_MAIN).setComponent(component))

    private fun open(intent: Intent): Boolean = try {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: Exception) {
        Toast.makeText(this, "No se pudo abrir", Toast.LENGTH_SHORT).show()
        false
    }

    // ---------- Piezas visuales ----------

    /** Círculo enfocable con el ícono de la app y su nombre debajo al seleccionarla. */
    private fun tile(
        name: String,
        big: Boolean,
        background: Int,
        icon: Drawable? = null,
        badge: String? = null,
        onClick: () -> Boolean,
    ): View {
        val size = dp(if (big) 78 else 52)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            clipChildren = false
            clipToPadding = false
            setPadding(dp(6), dp(8), dp(6), dp(2))
            isFocusable = true
            isClickable = true
            contentDescription = name
        }
        val circle = FrameLayout(this).apply {
            this.background = oval(background)
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) =
                    outline.setOval(0, 0, view.width, view.height)
            }
            clipToOutline = true
            elevation = dp(2).toFloat()
        }
        if (icon != null) {
            val pad = (size * 0.18f).toInt()
            circle.addView(ImageView(this).apply {
                setImageDrawable(icon)
                scaleType = ImageView.ScaleType.FIT_CENTER
                setPadding(pad, pad, pad, pad)
            }, FrameLayout.LayoutParams(-1, -1))
        } else {
            val text = badge ?: name
            circle.addView(label(if (text.length > 4) 15f else 20f, palette.onStrong, bold = true).apply {
                this.text = text
                gravity = Gravity.CENTER
            }, FrameLayout.LayoutParams(-1, -1))
        }
        val caption = label(if (big) 13f else 11f, palette.text, bold = true).apply {
            text = name
            gravity = Gravity.CENTER
            maxLines = 1
            visibility = View.INVISIBLE
        }
        box.addView(circle, LinearLayout.LayoutParams(size, size))
        box.addView(caption, LinearLayout.LayoutParams(size + dp(40), -2).apply { topMargin = dp(6) })

        box.setOnFocusChangeListener { _, focused ->
            val ring = circle.background as GradientDrawable
            if (focused) ring.setStroke(dp(4), palette.text) else ring.setStroke(0, Color.TRANSPARENT)
            circle.animate().scaleX(if (focused) 1.12f else 1f).scaleY(if (focused) 1.12f else 1f)
                .setDuration(120).start()
            caption.visibility = if (focused) View.VISIBLE else View.INVISIBLE
        }
        box.setOnClickListener { onClick() }
        return box
    }

    // El relleno vertical deja espacio para que el círculo agrandado no se corte.
    private fun row() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        clipChildren = false
        clipToPadding = false
        setPadding(dp(4), dp(10), dp(4), dp(4))
    }

    private fun scroller(content: LinearLayout) = HorizontalScrollView(this).apply {
        isHorizontalScrollBarEnabled = false
        clipChildren = false
        clipToPadding = false
        isFocusable = false
        addView(content)
    }

    private fun sectionTitle(value: String) = label(16f, palette.textSoft, bold = true).apply { text = value }

    private fun marginTop(value: Int) =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = dp(value) }
}
