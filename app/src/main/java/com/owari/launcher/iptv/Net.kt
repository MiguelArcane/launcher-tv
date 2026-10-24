package com.owari.launcher.iptv

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/** Descargas pequeñas en segundo plano: logos, miniaturas de YouTube y la página "en vivo" de YouTube. */
object Net {
    private val pool = Executors.newFixedThreadPool(3)
    private val main = Handler(Looper.getMainLooper())
    private const val DESKTOP_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Safari/537.36"

    private val images = object : LruCache<String, Bitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun cachedImage(url: String): Bitmap? = images.get(url)

    fun loadImage(url: String, skipCache: Boolean = false, done: (Bitmap?) -> Unit) {
        if (url.isBlank()) { done(null); return }
        if (!skipCache) images.get(url)?.let { done(it); return }
        pool.execute {
            val bitmap = runCatching {
                val bytes = get(url).readBytes()
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= 320) sample *= 2
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            }.getOrNull()
            if (bitmap != null) images.put(url, bitmap)
            main.post { done(bitmap) }
        }
    }

    private val liveIds = mutableMapOf<String, Pair<String, Long>>()

    /** Busca qué video está transmitiendo ahora un canal oficial de YouTube (página /@canal/live). */
    fun youTubeLiveId(pageUrl: String, done: (String?) -> Unit) {
        liveIds[pageUrl]?.let { (id, time) ->
            if (System.currentTimeMillis() - time < 10 * 60_000) { done(id); return }
        }
        pool.execute {
            val id = runCatching {
                val html = get(pageUrl).bufferedReader().use { it.readText() }
                val live = html.contains("\"isLiveNow\":true")
                Regex("""<link rel="canonical" href="https://www\.youtube\.com/watch\?v=([\w-]{11})"""")
                    .find(html)?.groupValues?.get(1)?.takeIf { live }
            }.getOrNull()
            if (id != null) synchronized(liveIds) { liveIds[pageUrl] = id to System.currentTimeMillis() }
            main.post { done(id) }
        }
    }

    fun youTubeThumbnail(videoId: String) =
        "https://i.ytimg.com/vi/$videoId/hqdefault_live.jpg?m=${System.currentTimeMillis() / 60_000}"

    private fun get(url: String) = (URL(url).openConnection() as HttpURLConnection).run {
        connectTimeout = 8_000
        readTimeout = 10_000
        instanceFollowRedirects = true
        setRequestProperty("User-Agent", DESKTOP_UA)
        setRequestProperty("Accept-Language", "es-CO,es")
        inputStream
    }
}
