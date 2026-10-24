package com.owari.launcher.iptv

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector

/** Crea reproductores ya configurados con el User-Agent y Referer que pide cada canal. */
@OptIn(UnstableApi::class)
object StreamPlayers {
    private const val DEFAULT_UA =
        "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Safari/537.36"

    /** Reproductor normal, a pantalla completa. */
    fun create(context: Context): ExoPlayer = ExoPlayer.Builder(context).build()

    /** Reproductor liviano para sacar miniaturas: calidad mínima y sin sonido. */
    fun createForThumbnails(context: Context): ExoPlayer {
        val selector = DefaultTrackSelector(context).apply {
            setParameters(buildUponParameters().setMaxVideoSize(640, 360).setForceLowestBitrate(true))
        }
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(1_000, 4_000, 500, 500)
            .build()
        return ExoPlayer.Builder(context)
            .setTrackSelector(selector)
            .setLoadControl(loadControl)
            .build()
            .apply { volume = 0f }
    }

    fun play(player: ExoPlayer, channel: Channel) {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(channel.userAgent.ifBlank { DEFAULT_UA })
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(10_000)
            .setReadTimeoutMs(15_000)
        if (channel.referrer.isNotBlank()) {
            http.setDefaultRequestProperties(mapOf("Referer" to channel.referrer, "Origin" to channel.referrer.trimEnd('/')))
        }
        val item = MediaItem.Builder().setUri(channel.url).apply {
            if (channel.url.contains(".m3u8", ignoreCase = true)) setMimeType(MimeTypes.APPLICATION_M3U8)
        }.build()
        val source = DefaultMediaSourceFactory(http).createMediaSource(item)
        player.setMediaSource(source)
        player.prepare()
        player.playWhenReady = true
    }
}
