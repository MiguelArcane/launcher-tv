package com.owari.launcher.web

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.os.Message
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import com.owari.launcher.adblock.Blocklist
import java.io.ByteArrayInputStream

/**
 * Navegador a pantalla completa para la TV, sin barra de direcciones:
 * - bloquea ventanas emergentes y que el sitio te mande a otra página,
 * - bloquea anuncios con la misma lista del bloqueador,
 * - se maneja con un cursor: flechas para moverlo, OK para hacer clic, Atrás para volver.
 */
class BrowserActivity : Activity() {

    companion object {
        const val EXTRA_URL = "url"
        private const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Safari/537.36"
    }

    private lateinit var root: FrameLayout
    private lateinit var web: WebView
    private lateinit var cursor: CursorView
    private lateinit var blocklist: Blocklist
    private lateinit var homeDomain: String
    private var fullscreenView: View? = null
    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null
    private var lastBlockedToast = 0L

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val url = intent.getStringExtra(EXTRA_URL) ?: return finish()
        homeDomain = baseDomain(Uri.parse(url).host ?: "")
        blocklist = Blocklist.get(this)

        root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.userAgentString = DESKTOP_UA
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            // Las ventanas nuevas se piden a onCreateWindow, donde se rechazan.
            settings.setSupportMultipleWindows(true)
            settings.javaScriptCanOpenWindowsAutomatically = false
            webViewClient = Client()
            webChromeClient = Chrome()
            isFocusable = false
        }
        cursor = CursorView(this)
        root.addView(web, FrameLayout.LayoutParams(-1, -1))
        root.addView(cursor, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
        web.loadUrl(url)
    }

    override fun onPause() {
        web.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        web.onResume()
    }

    override fun onDestroy() {
        root.removeView(web)
        web.destroy()
        super.onDestroy()
    }

    // ---------- Bloqueos ----------

    /** "www.sitio.com" y "video.sitio.com" son el mismo sitio: se compara la parte final. */
    private fun baseDomain(host: String): String {
        val parts = host.lowercase().split('.').filter { it.isNotEmpty() }
        if (parts.size <= 2) return parts.joinToString(".")
        val secondLevel = parts[parts.size - 2]
        // Dominios como ejemplo.com.co o ejemplo.co.uk
        val takeThree = secondLevel.length <= 3 && parts.last().length == 2
        return parts.takeLast(if (takeThree) 3 else 2).joinToString(".")
    }

    private fun isSameSite(url: Uri) = baseDomain(url.host ?: "") == homeDomain

    private fun showBlocked() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastBlockedToast > 4_000) {
            lastBlockedToast = now
            Toast.makeText(this, "Se bloqueó una ventana de publicidad", Toast.LENGTH_SHORT).show()
        }
    }

    private inner class Client : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val target = request.url
            if (target.scheme != "http" && target.scheme != "https") return true // intent://, market://, etc.
            if (!request.isForMainFrame || isSameSite(target)) return false
            // El sitio intentó mandarte a otra página (casi siempre publicidad).
            showBlocked()
            return true
        }

        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            val host = request.url.host ?: return null
            if (!blocklist.isBlocked(host)) return null
            return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
        }
    }

    private inner class Chrome : WebChromeClient() {
        override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
            showBlocked() // ventana emergente
            return false
        }

        override fun onShowCustomView(view: View, callback: CustomViewCallback) {
            fullscreenView = view
            fullscreenCallback = callback
            root.addView(view, root.indexOfChild(cursor), FrameLayout.LayoutParams(-1, -1))
            web.visibility = View.GONE
        }

        override fun onHideCustomView() {
            fullscreenView?.let { root.removeView(it) }
            fullscreenView = null
            fullscreenCallback = null
            web.visibility = View.VISIBLE
        }
    }

    // ---------- Control remoto ----------

    private var lastMove = 0L
    private var speed = 1f

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        val isArrow = code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN ||
            code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT
        val isOk = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
        if (!isArrow && !isOk) return super.dispatchKeyEvent(event)

        if (isOk) {
            if (event.action == KeyEvent.ACTION_UP) click()
            return true
        }
        if (event.action != KeyEvent.ACTION_DOWN) return true
        // Si se mantiene presionada la flecha, el cursor acelera.
        val now = SystemClock.elapsedRealtime()
        speed = if (event.repeatCount > 0 && now - lastMove < 200) (speed + 0.5f).coerceAtMost(4f) else 1f
        lastMove = now
        val step = (18 * speed * resources.displayMetrics.density / 2).toInt()
        when (code) {
            KeyEvent.KEYCODE_DPAD_UP -> move(0, -step)
            KeyEvent.KEYCODE_DPAD_DOWN -> move(0, step)
            KeyEvent.KEYCODE_DPAD_LEFT -> move(-step, 0)
            KeyEvent.KEYCODE_DPAD_RIGHT -> move(step, 0)
        }
        return true
    }

    /** Mueve el cursor; en los bordes desplaza la página. */
    private fun move(dx: Int, dy: Int) {
        val margin = (12 * resources.displayMetrics.density).toInt()
        val newX = cursor.cx + dx
        val newY = cursor.cy + dy
        if (newY < margin) web.scrollBy(0, dy)
        if (newY > root.height - margin) web.scrollBy(0, dy)
        if (newX < margin || newX > root.width - margin) web.scrollBy(dx, 0)
        cursor.moveTo(newX.coerceIn(margin, root.width - margin), newY.coerceIn(margin, root.height - margin))
    }

    private fun click() {
        val target: View = fullscreenView ?: web
        val time = SystemClock.uptimeMillis()
        val x = cursor.cx.toFloat()
        val y = cursor.cy.toFloat()
        val down = MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, x, y, 0)
        val up = MotionEvent.obtain(time, time + 60, MotionEvent.ACTION_UP, x, y, 0)
        target.dispatchTouchEvent(down)
        target.dispatchTouchEvent(up)
        down.recycle()
        up.recycle()
        cursor.pulse()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when {
            fullscreenView != null -> fullscreenCallback?.onCustomViewHidden() ?: Unit
            web.canGoBack() -> web.goBack()
            else -> super.onBackPressed()
        }
    }

    /** Puntero redondo que se dibuja encima de la página. */
    private class CursorView(context: Context) : View(context) {
        var cx = 0
            private set
        var cy = 0
            private set
        private val density = context.resources.displayMetrics.density
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F6C90E") }
        private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#2B2100"); style = Paint.Style.STROKE; strokeWidth = 3 * density
        }
        private var pressed = false

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            if (cx == 0 && cy == 0) { cx = w / 2; cy = h / 2 }
        }

        fun moveTo(nx: Int, ny: Int) { cx = nx; cy = ny; invalidate() }

        fun pulse() {
            pressed = true
            invalidate()
            postDelayed({ pressed = false; invalidate() }, 150)
        }

        override fun onDraw(canvas: Canvas) {
            val r = (if (pressed) 8 else 11) * density
            canvas.drawCircle(cx.toFloat(), cy.toFloat(), r, fill)
            canvas.drawCircle(cx.toFloat(), cy.toFloat(), r, ring)
        }

        init {
            isFocusable = false
            isClickable = false
            layoutParams = ViewGroup.LayoutParams(-1, -1)
        }
    }
}
