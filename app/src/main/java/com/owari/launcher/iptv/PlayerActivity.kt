package com.owari.launcher.iptv

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.owari.launcher.dp
import com.owari.launcher.label
import com.owari.launcher.rounded

/**
 * Canal a pantalla completa. Las flechas (arriba/derecha = siguiente, abajo/izquierda =
 * anterior), CH+/CH− y siguiente/anterior del control cambian de canal dentro del mismo
 * país, como en la TV normal. Los canales de YouTube se saltan aquí.
 */
@OptIn(UnstableApi::class)
class PlayerActivity : Activity() {

    companion object {
        const val EXTRA_COUNTRY = "country"
        const val EXTRA_INDEX = "index"
    }

    private lateinit var playerView: PlayerView
    private lateinit var info: LinearLayout
    private lateinit var infoName: TextView
    private lateinit var infoText: TextView
    private var player: ExoPlayer? = null
    private val handler = Handler(Looper.getMainLooper())

    private var channels: List<Channel> = emptyList()
    private var index = 0

    private val hideInfo = Runnable { info.visibility = View.GONE }

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_BUFFERING) showInfo("Cargando…", keep = true)
            if (state == Player.STATE_READY) showInfo(null, keep = false)
        }

        override fun onPlayerError(error: PlaybackException) {
            showInfo("Este canal no está transmitiendo ahora.\nUsa las flechas para cambiar de canal.", keep = true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val country = intent.getStringExtra(EXTRA_COUNTRY) ?: ChannelStore.HOME_COUNTRY
        channels = ChannelStore.byCountry(this, country).filterNot { it.isYouTube }
        val wanted = ChannelStore.byCountry(this, country).getOrNull(intent.getIntExtra(EXTRA_INDEX, 0))
        index = channels.indexOfFirst { it.id == wanted?.id }.coerceAtLeast(0)
        setContentView(buildScreen())
    }

    override fun onStart() {
        super.onStart()
        player = StreamPlayers.create(this).also {
            it.addListener(listener)
            playerView.player = it
        }
        tune()
    }

    override fun onStop() {
        handler.removeCallbacksAndMessages(null)
        playerView.player = null
        player?.release()
        player = null
        super.onStop()
    }

    private fun buildScreen(): View {
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        playerView = PlayerView(this).apply {
            useController = false
            setShutterBackgroundColor(Color.BLACK)
        }
        root.addView(playerView, FrameLayout.LayoutParams(-1, -1))

        info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(14), dp(20), dp(14))
            background = rounded(Color.parseColor("#CC000000"), dp(14).toFloat())
        }
        infoName = label(22f, Color.WHITE, bold = true)
        infoText = label(14f, Color.parseColor("#DDDDDD"))
        info.addView(infoName)
        info.addView(infoText)
        root.addView(info, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START).apply {
            setMargins(dp(32), dp(28), dp(32), 0)
        })
        return root
    }

    private fun tune() {
        val channel = channels.getOrNull(index) ?: return finish()
        player?.let { StreamPlayers.play(it, channel) }
        showInfo("Cargando…", keep = true)
    }

    private fun showInfo(text: String?, keep: Boolean) {
        val channel = channels.getOrNull(index) ?: return
        infoName.text = "${index + 1}. ${channel.name}"
        infoText.text = text ?: channel.country
        infoText.visibility = View.VISIBLE
        info.visibility = View.VISIBLE
        handler.removeCallbacks(hideInfo)
        if (!keep) handler.postDelayed(hideInfo, 3_500)
    }

    private fun change(step: Int) {
        if (channels.isEmpty()) return
        index = (index + step + channels.size) % channels.size
        tune()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_CHANNEL_UP,
        KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { change(+1); true }
        KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_CHANNEL_DOWN,
        KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_MEDIA_REWIND -> { change(-1); true }
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_INFO -> {
            showInfo(null, keep = false); true
        }
        else -> super.onKeyDown(keyCode, event)
    }
}
