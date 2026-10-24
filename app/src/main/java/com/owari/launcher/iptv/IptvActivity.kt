package com.owari.launcher.iptv

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.KeyEvent
import android.view.TextureView
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.owari.launcher.Palette
import com.owari.launcher.dp
import com.owari.launcher.label
import com.owari.launcher.rounded

/**
 * TV IP: países a la izquierda, canales a la derecha con una miniatura de lo que
 * están pasando (se renueva cada minuto para los canales que se ven en pantalla).
 */
class IptvActivity : Activity() {

    companion object {
        private const val COLUMNS = 4
        private const val REFRESH_MS = 60_000L
    }

    private lateinit var palette: Palette
    private lateinit var countryList: LinearLayout
    private lateinit var title: TextView
    private lateinit var gridScroll: ScrollView
    private lateinit var grid: LinearLayout
    private lateinit var surface: TextureView

    private var grabber: ThumbnailGrabber? = null
    private val handler = Handler(Looper.getMainLooper())
    private var country = ChannelStore.HOME_COUNTRY
    private var channels: List<Channel> = emptyList()
    private var lastChannelId: String? = null

    private class Card(val channel: Channel, val view: View, val image: ImageView, val status: TextView) {
        var updatedAt = 0L
    }
    private val cards = mutableListOf<Card>()
    private val thumbnails = HashMap<String, Pair<Bitmap, Long>>()

    private val refreshTick = object : Runnable {
        override fun run() {
            refreshNextThumbnail()
            handler.postDelayed(this, 1_500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        palette = Palette.current(this)
        window.setBackgroundDrawable(null)
        country = savedInstanceState?.getString("country") ?: ChannelStore.HOME_COUNTRY
        setContentView(buildScreen())
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("country", country)
    }

    override fun onResume() {
        super.onResume()
        // Se recarga por si cambiaron una dirección o agregaron canales en ajustes,
        // y se vuelve a seleccionar el canal en el que estaba.
        val focusedId = cards.firstOrNull { it.view.hasFocus() }?.channel?.id ?: lastChannelId
        showCountries()
        showCountry(country, focusFirst = true, focusId = focusedId)
        grabber = ThumbnailGrabber(this, surface)
        handler.post(refreshTick)
    }

    override fun onPause() {
        handler.removeCallbacks(refreshTick)
        grabber?.release()
        grabber = null
        super.onPause()
    }

    // ---------- Pantalla ----------

    private fun buildScreen(): View {
        val root = FrameLayout(this)
        // Superficie oculta detrás de todo, donde el reproductor de miniaturas dibuja.
        surface = TextureView(this)
        root.addView(surface, FrameLayout.LayoutParams(320, 180))

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(palette.background)
        }
        root.addView(content, FrameLayout.LayoutParams(-1, -1))

        // Columna de países.
        val side = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(12), dp(16))
            setBackgroundColor(palette.soft)
        }
        side.addView(label(24f, palette.text, bold = true).apply { text = "TV IP" })
        side.addView(label(12f, palette.textSoft).apply { text = "Televisión por internet" })
        countryList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        side.addView(ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            addView(countryList)
        }, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(12) })
        content.addView(side, LinearLayout.LayoutParams(dp(230), -1))

        // Canales.
        val main = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(8))
        }
        title = label(26f, palette.text, bold = true)
        main.addView(title)
        main.addView(label(13f, palette.textSoft).apply {
            text = "OK para ver · Mantén OK o MENÚ sobre un canal para cambiar su dirección"
        })
        grid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            setPadding(dp(4), dp(8), dp(4), dp(24))
        }
        gridScroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            clipChildren = false
            addView(grid)
        }
        main.addView(gridScroll, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(10) })
        content.addView(main, LinearLayout.LayoutParams(0, -1, 1f))
        return root
    }

    private fun showCountries() {
        countryList.removeAllViews()
        countryList.addView(sideButton("+  Agregar canal", selected = false) {
            startActivity(Intent(this, ChannelEditActivity::class.java).putExtra("country", country))
        })
        for (name in ChannelStore.countries(this)) {
            val count = ChannelStore.byCountry(this, name).size
            countryList.addView(sideButton("$name  ($count)", selected = name == country) {
                showCountry(name, focusFirst = false)
                showCountries()
            })
        }
    }

    private fun sideButton(text: String, selected: Boolean, onClick: () -> Unit): View =
        label(15f, if (selected) palette.onStrong else palette.text, bold = selected).apply {
            this.text = text
            setPadding(dp(12), dp(8), dp(12), dp(8))
            isFocusable = true
            isClickable = true
            background = rounded(if (selected) palette.strong else Color.TRANSPARENT, dp(10).toFloat())
            setOnFocusChangeListener { v, focused ->
                (v.background as GradientDrawable).setStroke(if (focused) dp(3) else 0, palette.text)
            }
            setOnClickListener { onClick() }
        }

    private fun showCountry(name: String, focusFirst: Boolean, focusId: String? = null) {
        country = name
        channels = ChannelStore.byCountry(this, name)
        title.text = "$name  ·  ${channels.size} canales"
        grid.removeAllViews()
        cards.clear()
        var row: LinearLayout? = null
        channels.forEachIndexed { index, channel ->
            if (index % COLUMNS == 0) {
                row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; clipChildren = false }
                grid.addView(row)
            }
            row!!.addView(card(channel, index))
        }
        gridScroll.scrollTo(0, 0)
        if (focusFirst) {
            val target = cards.firstOrNull { it.channel.id == focusId } ?: cards.firstOrNull()
            target?.view?.post { target.view.requestFocus() }
        }
    }

    private fun card(channel: Channel, index: Int): View {
        val width = dp(158)
        val height = dp(89)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(6), dp(6), dp(6), dp(6))
            isFocusable = true
            isClickable = true
            isLongClickable = true
            background = rounded(Color.TRANSPARENT, dp(12).toFloat())
        }
        val frame = FrameLayout(this).apply {
            background = rounded(Color.parseColor("#1A1A1A"), dp(8).toFloat())
            clipToOutline = true
        }
        val image = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        frame.addView(image, FrameLayout.LayoutParams(-1, -1))
        val status = label(11f, Color.WHITE, bold = true).apply {
            setPadding(dp(6), dp(2), dp(6), dp(2))
            background = rounded(Color.parseColor("#99000000"), dp(6).toFloat())
            visibility = View.GONE
        }
        frame.addView(status, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.START).apply {
            setMargins(dp(4), 0, 0, dp(4))
        })
        if (channel.isYouTube || channel.isEdited || channel.isUserAdded) {
            val tag = when {
                channel.isUserAdded -> "MÍO"
                channel.isEdited -> "EDITADO"
                else -> "OFICIAL"
            }
            frame.addView(label(10f, Color.WHITE, bold = true).apply {
                text = tag
                setPadding(dp(5), dp(1), dp(5), dp(1))
                background = rounded(Color.parseColor("#CC2B2100"), dp(6).toFloat())
            }, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply { setMargins(0, dp(4), dp(4), 0) })
        }
        box.addView(frame, LinearLayout.LayoutParams(width, height))
        box.addView(label(13f, palette.text).apply {
            text = channel.name
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }, LinearLayout.LayoutParams(width, -2).apply { topMargin = dp(4) })

        box.setOnFocusChangeListener { v, focused ->
            (v.background as GradientDrawable).setStroke(if (focused) dp(3) else 0, palette.text)
        }
        box.setOnClickListener { play(index) }
        box.setOnLongClickListener { edit(channel); true }
        box.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_MENU && event.action == KeyEvent.ACTION_UP) { edit(channel); true } else false
        }

        val card = Card(channel, box, image, status)
        cards += card
        // Mientras no hay miniatura se muestra el logo del canal.
        thumbnails[channel.id]?.let { (bitmap, time) ->
            image.setImageBitmap(bitmap)
            card.updatedAt = time
        } ?: Net.loadImage(channel.logo) { logo ->
            if (logo != null && card.updatedAt == 0L) {
                image.scaleType = ImageView.ScaleType.FIT_CENTER
                image.setPadding(dp(16), dp(10), dp(16), dp(10))
                image.setImageBitmap(logo)
            }
        }
        return box
    }

    // ---------- Miniaturas ----------

    private fun isOnScreen(view: View): Boolean {
        val rect = Rect()
        return view.isShown && view.getGlobalVisibleRect(rect) && rect.height() > view.height / 2
    }

    /** Toma el canal visible con la miniatura más vieja (o sin ninguna) y la renueva. */
    private fun refreshNextThumbnail() {
        val g = grabber ?: return
        if (g.busy) return
        val now = SystemClock.elapsedRealtime()
        val next = cards.filter { isOnScreen(it.view) && now - it.updatedAt > REFRESH_MS }
            .minByOrNull { it.updatedAt } ?: return
        next.updatedAt = now // así no se repite mientras carga
        if (next.channel.isYouTube) {
            Net.youTubeLiveId(next.channel.url) { id ->
                if (id == null) { showStatus(next, "Sin transmisión"); return@youTubeLiveId }
                Net.loadImage(Net.youTubeThumbnail(id), skipCache = true) { bitmap -> setThumbnail(next, bitmap) }
            }
        } else {
            g.grab(next.channel) { bitmap -> setThumbnail(next, bitmap) }
        }
    }

    private fun setThumbnail(card: Card, bitmap: Bitmap?) {
        if (bitmap == null) { showStatus(card, "Sin señal"); return }
        thumbnails[card.channel.id] = bitmap to SystemClock.elapsedRealtime()
        card.image.scaleType = ImageView.ScaleType.CENTER_CROP
        card.image.setPadding(0, 0, 0, 0)
        card.image.setImageBitmap(bitmap)
        card.status.visibility = View.GONE
    }

    private fun showStatus(card: Card, text: String) {
        card.status.text = text
        card.status.visibility = View.VISIBLE
    }

    // ---------- Acciones ----------

    private fun play(index: Int) {
        val channel = channels.getOrNull(index) ?: return
        lastChannelId = channel.id
        if (channel.isYouTube) {
            openYouTube(channel)
            return
        }
        startActivity(
            Intent(this, PlayerActivity::class.java)
                .putExtra(PlayerActivity.EXTRA_COUNTRY, country)
                .putExtra(PlayerActivity.EXTRA_INDEX, index)
        )
    }

    private fun openYouTube(channel: Channel) {
        Toast.makeText(this, "Abriendo ${channel.name} en YouTube…", Toast.LENGTH_SHORT).show()
        Net.youTubeLiveId(channel.url) { id ->
            val uri = Uri.parse(if (id != null) "https://www.youtube.com/watch?v=$id" else channel.url)
            val intent = Intent(Intent.ACTION_VIEW, uri).setPackage("com.google.android.youtube.tv")
            runCatching { startActivity(intent) }.onFailure {
                runCatching { startActivity(Intent(Intent.ACTION_VIEW, uri)) }
            }
        }
    }

    private fun edit(channel: Channel) {
        lastChannelId = channel.id
        startActivity(Intent(this, ChannelEditActivity::class.java).putExtra("id", channel.id))
    }
}
