package com.owari.launcher.iptv

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.owari.launcher.Palette
import com.owari.launcher.dp
import com.owari.launcher.label
import com.owari.launcher.rounded

/**
 * Ajustes de un canal de TV IP. Con "id": cambiar su dirección o restaurar la de
 * fábrica (que nunca se borra). Sin "id": agregar un canal nuevo.
 */
class ChannelEditActivity : Activity() {

    private lateinit var palette: Palette
    private var channel: Channel? = null
    private lateinit var nameField: EditText
    private lateinit var urlField: EditText
    private lateinit var countryButton: TextView
    private var country = ChannelStore.HOME_COUNTRY

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        palette = Palette.current(this)
        window.setBackgroundDrawable(null)
        channel = intent.getStringExtra("id")?.let { ChannelStore.find(this, it) }
        country = channel?.country ?: intent.getStringExtra("country") ?: ChannelStore.HOME_COUNTRY
        setContentView(buildScreen())
    }

    private fun buildScreen(): View {
        val c = channel
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(48), dp(28), dp(48), dp(28))
        }
        column.addView(label(28f, palette.text, bold = true).apply {
            text = if (c == null) "Agregar canal" else "Canal: ${c.name}"
        })
        column.addView(label(14f, palette.textSoft).apply {
            text = "Presiona Atrás para salir sin cambiar nada."
        })

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(18), dp(24), dp(18))
            background = rounded(palette.soft, dp(16).toFloat())
        }
        column.addView(card, spaced(18))

        card.addView(fieldLabel("Nombre"))
        nameField = field(c?.name ?: "", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS).apply {
            // Los canales de fábrica conservan su nombre.
            isEnabled = c == null || c.isUserAdded
        }
        card.addView(nameField)

        card.addView(fieldLabel("País"), spaced(10))
        countryButton = button("") { chooseCountry() }.apply { isEnabled = c == null || c.isUserAdded }
        updateCountry()
        card.addView(countryButton)

        card.addView(fieldLabel("Dirección del canal (IP o enlace .m3u8 / YouTube)"), spaced(10))
        urlField = field(c?.url ?: "http://", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        card.addView(urlField)

        if (c?.factoryUrl != null) {
            card.addView(label(13f, palette.textSoft).apply {
                text = "Dirección de fábrica: ${c.factoryUrl}" + if (c.isEdited) "\nAhora está usando una dirección cambiada." else ""
            }, spaced(8))
        }

        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(button("Guardar") { save() })
        if (c != null && !c.isUserAdded) {
            actions.addView(button("Restaurar dirección de fábrica") { restore() }, gap())
        }
        if (c != null && c.isUserAdded) {
            actions.addView(button("Borrar este canal") { remove() }, gap())
        }
        column.addView(actions, spaced(18))

        return ScrollView(this).apply {
            setBackgroundColor(palette.background)
            addView(column)
        }
    }

    // ---------- Acciones ----------

    private fun save() {
        val url = urlField.text.toString().trim()
        if (!url.startsWith("http://") && !url.startsWith("https://") || url.length < 12) {
            toast("Escribe una dirección que empiece por http:// o https://")
            return
        }
        val c = channel
        if (c == null) {
            val name = nameField.text.toString().trim()
            if (name.isEmpty()) { toast("Escribe el nombre del canal"); return }
            ChannelStore.add(this, name, country, url)
            toast("Canal agregado en $country")
        } else {
            ChannelStore.setUrl(this, c.id, url)
            toast("Dirección guardada")
        }
        finish()
    }

    private fun restore() {
        val c = channel ?: return
        ChannelStore.restore(this, c.id)
        toast("Se restauró la dirección de fábrica")
        finish()
    }

    private fun remove() {
        val c = channel ?: return
        AlertDialog.Builder(this)
            .setTitle("¿Borrar ${c.name}?")
            .setMessage("Este canal lo agregaste tú. Los canales de fábrica no se pueden borrar.")
            .setPositiveButton("Borrar") { _, _ ->
                ChannelStore.removeAdded(this, c.id)
                toast("Canal borrado")
                finish()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun chooseCountry() {
        val countries = ChannelStore.countries(this).toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("País del canal")
            .setItems(countries) { _, which ->
                country = countries[which]
                updateCountry()
            }
            .show()
    }

    private fun updateCountry() {
        countryButton.text = "$country  ▾"
    }

    // ---------- Piezas visuales ----------

    private fun fieldLabel(text: String) = label(14f, palette.textSoft, bold = true).apply { this.text = text }

    private fun field(value: String, type: Int) = EditText(this).apply {
        setText(value)
        inputType = type
        setTextColor(Color.parseColor("#1A1A1A"))
        textSize = 16f
        setSingleLine()
        setPadding(dp(12), dp(10), dp(12), dp(10))
        background = rounded(Color.WHITE, dp(8).toFloat())
        setOnFocusChangeListener { v, focused ->
            (v.background as GradientDrawable).setStroke(if (focused) dp(3) else 0, palette.text)
        }
    }

    private fun button(text: String, onClick: () -> Unit) = label(16f, palette.onStrong, bold = true).apply {
        this.text = text
        setPadding(dp(18), dp(10), dp(18), dp(10))
        isFocusable = true
        isClickable = true
        background = rounded(palette.strong, dp(10).toFloat())
        setOnFocusChangeListener { v, focused ->
            (v.background as GradientDrawable).setStroke(if (focused) dp(3) else 0, palette.text)
        }
        setOnClickListener { onClick() }
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

    private fun spaced(top: Int) = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top) }

    private fun gap() = LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(12) }
}
