package com.owari.launcher.web

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.ScreenLength

/**
 * Navegador a pantalla completa para la TV, con el motor de Firefox (GeckoView),
 * porque el motor web de la TV es de 2021 y los sitios modernos se ven desarmados.
 * - Bloquea ventanas emergentes y que el sitio te mande a otra página.
 * - Trae uBlock Origin de fábrica y bloquea rastreadores (además del bloqueador de ADMIN).
 * - Corre en su propio proceso (":web") para poder cerrarlo entero y liberar memoria.
 * - Se maneja con un cursor: flechas para moverlo, OK para hacer clic, Atrás para volver.
 */
class BrowserActivity : Activity() {

    companion object {
        const val EXTRA_URL = "url"

        @Volatile private var runtime: GeckoRuntime? = null

        /** Firefox se arranca una sola vez por app: es pesado para la memoria de la TV. */
        private fun runtime(context: Context): GeckoRuntime = runtime ?: synchronized(this) {
            runtime ?: GeckoRuntime.create(
                context.applicationContext,
                GeckoRuntimeSettings.Builder()
                    .contentBlocking(
                        ContentBlocking.Settings.Builder()
                            .antiTracking(
                                ContentBlocking.AntiTracking.DEFAULT or ContentBlocking.AntiTracking.AD or
                                    ContentBlocking.AntiTracking.CRYPTOMINING or ContentBlocking.AntiTracking.FINGERPRINTING
                            )
                            .build()
                    )
                    .consoleOutput(false)
                    .build()
            ).also {
                runtime = it
                // uBlock Origin (oficial de addons.mozilla.org) viene dentro de la app y queda
                // activo para todos los sitios: quita anuncios dentro de las páginas y los videos.
                it.webExtensionController.ensureBuiltIn(UBLOCK_PATH, UBLOCK_ID)
            }
        }

        private const val UBLOCK_PATH = "resource://android/assets/ublock/"
        private const val UBLOCK_ID = "uBlock0@raymondhill.net"
    }

    private lateinit var root: FrameLayout
    private lateinit var view: GeckoView
    private lateinit var session: GeckoSession
    private lateinit var cursor: CursorView
    private lateinit var homeDomain: String
    private var canGoBack = false
    private var fullscreen = false
    private var lastBlockedToast = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val url = intent.getStringExtra(EXTRA_URL) ?: return finish()
        homeDomain = baseDomain(Uri.parse(url).host ?: "")

        session = GeckoSession(
            GeckoSessionSettings.Builder()
                .userAgentMode(GeckoSessionSettings.USER_AGENT_MODE_DESKTOP)
                .viewportMode(GeckoSessionSettings.VIEWPORT_MODE_DESKTOP)
                .build()
        )
        session.navigationDelegate = Navigation()
        session.contentDelegate = Content()

        root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        view = GeckoView(this).apply { isFocusable = false }
        cursor = CursorView(this)
        root.addView(view, FrameLayout.LayoutParams(-1, -1))
        root.addView(cursor, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)

        session.open(runtime(this))
        view.setSession(session)
        session.loadUri(url)
    }

    override fun onPause() {
        session.setActive(false)
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        session.setActive(true)
    }

    override fun onDestroy() {
        view.releaseSession()
        session.close()
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

    private fun showBlocked() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastBlockedToast > 4_000) {
            lastBlockedToast = now
            Toast.makeText(this, "Se bloqueó una ventana de publicidad", Toast.LENGTH_SHORT).show()
        }
    }

    private inner class Navigation : GeckoSession.NavigationDelegate {
        override fun onCanGoBack(session: GeckoSession, value: Boolean) {
            canGoBack = value
        }

        override fun onLoadRequest(
            session: GeckoSession,
            request: GeckoSession.NavigationDelegate.LoadRequest,
        ): GeckoResult<AllowOrDeny> {
            val target = Uri.parse(request.uri)
            val scheme = target.scheme ?: ""
            val allowed = when {
                scheme == "about" || scheme == "data" || scheme == "blob" -> true
                scheme != "http" && scheme != "https" -> false // intent://, market://, etc.
                // Ventana o pestaña nueva: casi siempre publicidad.
                request.target == GeckoSession.NavigationDelegate.TARGET_WINDOW_NEW -> false
                else -> baseDomain(target.host ?: "") == homeDomain
            }
            if (!allowed) runOnUiThread { showBlocked() }
            return GeckoResult.fromValue(if (allowed) AllowOrDeny.ALLOW else AllowOrDeny.DENY)
        }

        override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession>? {
            runOnUiThread { showBlocked() } // ventana emergente
            return null
        }
    }

    private inner class Content : GeckoSession.ContentDelegate {
        override fun onFullScreen(session: GeckoSession, fullScreen: Boolean) {
            fullscreen = fullScreen
            cursor.visibility = if (fullScreen) View.GONE else View.VISIBLE
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
        // En pantalla completa (video) las teclas van al reproductor del sitio.
        if (fullscreen && code != KeyEvent.KEYCODE_BACK) return super.dispatchKeyEvent(event)
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
        val scrollX = if (newX < margin || newX > root.width - margin) dx else 0
        val scrollY = if (newY < margin || newY > root.height - margin) dy * 3 else 0
        if (scrollX != 0 || scrollY != 0) {
            session.panZoomController.scrollBy(
                ScreenLength.fromPixels(scrollX.toDouble()),
                ScreenLength.fromPixels(scrollY.toDouble()),
            )
        }
        cursor.moveTo(newX.coerceIn(margin, root.width - margin), newY.coerceIn(margin, root.height - margin))
    }

    private fun click() {
        val time = SystemClock.uptimeMillis()
        val x = cursor.cx.toFloat()
        val y = cursor.cy.toFloat()
        val down = MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, x, y, 0)
        val up = MotionEvent.obtain(time, time + 60, MotionEvent.ACTION_UP, x, y, 0)
        down.source = InputDevice.SOURCE_TOUCHSCREEN
        up.source = InputDevice.SOURCE_TOUCHSCREEN
        view.dispatchTouchEvent(down)
        view.dispatchTouchEvent(up)
        down.recycle()
        up.recycle()
        cursor.pulse()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when {
            fullscreen -> session.exitFullScreen()
            canGoBack -> session.goBack()
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

        init {
            isFocusable = false
            isClickable = false
        }

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
    }
}
