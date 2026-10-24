package com.owari.launcher.web

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import com.owari.launcher.Palette
import com.owari.launcher.dp
import com.owari.launcher.label
import com.owari.launcher.rounded

/** Lista de sitios guardados. OK abre el sitio; mantener OK o MENÚ lo quita. */
class WebSitesActivity : Activity() {

    private lateinit var palette: Palette
    private lateinit var list: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        palette = Palette.current(this)
        window.setBackgroundDrawable(null)
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(48), dp(28), dp(48), dp(28))
        }
        column.addView(label(30f, palette.text, bold = true).apply { text = "Web" })
        column.addView(label(14f, palette.textSoft).apply {
            text = "Sitios sin ventanas emergentes ni anuncios. Dentro del sitio, mueve el cursor con las flechas y usa OK para hacer clic.\n" +
                "Mantén OK o MENÚ sobre un sitio para quitarlo."
        })
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        column.addView(list, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })
        setContentView(ScrollView(this).apply {
            setBackgroundColor(palette.background)
            addView(column)
        })
    }

    override fun onResume() {
        super.onResume()
        showSites()
    }

    private fun showSites() {
        list.removeAllViews()
        WebSites.all(this).forEach { site ->
            list.addView(row("🌐  ${site.name}", site.url, onClick = {
                startActivity(Intent(this, BrowserActivity::class.java).putExtra(BrowserActivity.EXTRA_URL, site.url))
            }, onMenu = { confirmRemove(site) }), spaced())
        }
        list.addView(row("+  Agregar sitio", "Escribe el nombre y la dirección", onClick = { addSite() }, onMenu = null), spaced())
        list.getChildAt(0)?.requestFocus()
    }

    private fun row(title: String, subtitle: String, onClick: () -> Unit, onMenu: (() -> Unit)?): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(14), dp(20), dp(14))
            isFocusable = true
            isClickable = true
            background = rounded(palette.soft, dp(14).toFloat())
            addView(label(20f, palette.text, bold = true).apply { text = title })
            addView(label(13f, palette.textSoft).apply { text = subtitle })
            setOnFocusChangeListener { v, focused ->
                (v.background as GradientDrawable).setStroke(if (focused) dp(3) else 0, palette.text)
            }
            setOnClickListener { onClick() }
            if (onMenu != null) {
                setOnLongClickListener { onMenu(); true }
                setOnKeyListener { _, keyCode, event ->
                    if (keyCode == KeyEvent.KEYCODE_MENU && event.action == KeyEvent.ACTION_UP) { onMenu(); true } else false
                }
            }
        }

    private fun addSite() {
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
        }
        val name = EditText(this).apply { hint = "Nombre"; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS }
        val url = EditText(this).apply { hint = "Dirección, por ejemplo ejemplo.com"; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI }
        form.addView(name)
        form.addView(url)
        AlertDialog.Builder(this)
            .setTitle("Agregar sitio")
            .setView(form)
            .setPositiveButton("Guardar") { _, _ ->
                var address = url.text.toString().trim()
                if (address.isEmpty()) return@setPositiveButton
                if (!address.startsWith("http://") && !address.startsWith("https://")) address = "https://$address"
                val title = name.text.toString().trim().ifEmpty { address.substringAfter("://").substringBefore('/') }
                WebSites.add(this, title, address)
                Toast.makeText(this, "Sitio guardado", Toast.LENGTH_SHORT).show()
                showSites()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun confirmRemove(site: WebSite) {
        AlertDialog.Builder(this)
            .setTitle("¿Quitar ${site.name}?")
            .setPositiveButton("Quitar") { _, _ ->
                WebSites.remove(this, site)
                showSites()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun spaced() = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) }
}
