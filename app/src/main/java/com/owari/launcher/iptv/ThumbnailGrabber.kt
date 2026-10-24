package com.owari.launcher.iptv

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.TextureView
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

/**
 * Saca una foto de lo que está pasando en un canal: lo abre sin sonido en un
 * reproductor oculto, espera el primer cuadro y lo copia. Uno a la vez, porque
 * la TV tiene poca memoria.
 */
class ThumbnailGrabber(context: Context, private val surface: TextureView) {
    private val handler = Handler(Looper.getMainLooper())
    private var player: ExoPlayer? = StreamPlayers.createForThumbnails(context).also { it.setVideoTextureView(surface) }
    private var pending: ((Bitmap?) -> Unit)? = null

    val busy get() = pending != null

    private val listener = object : Player.Listener {
        override fun onRenderedFirstFrame() {
            // Se espera un momento para no quedarse con un cuadro negro de arranque.
            handler.postDelayed({ finish(surface.getBitmap(320, 180)) }, 700)
        }

        override fun onPlayerError(error: PlaybackException) = finish(null)
    }

    init {
        player?.addListener(listener)
    }

    fun grab(channel: Channel, done: (Bitmap?) -> Unit) {
        val p = player ?: return done(null)
        if (busy) return done(null)
        pending = done
        StreamPlayers.play(p, channel)
        handler.postDelayed(timeout, 12_000)
    }

    private val timeout = Runnable { finish(null) }

    private fun finish(bitmap: Bitmap?) {
        val done = pending ?: return
        pending = null
        handler.removeCallbacksAndMessages(null)
        player?.stop()
        player?.clearMediaItems()
        done(bitmap)
    }

    fun release() {
        handler.removeCallbacksAndMessages(null)
        pending = null
        player?.removeListener(listener)
        player?.release()
        player = null
    }
}
