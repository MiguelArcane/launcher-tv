package com.owari.launcher.iptv

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Un canal de TV IP. [factoryUrl] es la dirección que trae la app y nunca se borra. */
data class Channel(
    val id: String,
    val name: String,
    val country: String,
    val logo: String,
    val url: String,
    val factoryUrl: String?,
    val type: String,
    val referrer: String,
    val userAgent: String,
) {
    val isYouTube get() = type == "youtube"
    val isUserAdded get() = factoryUrl == null
    val isEdited get() = factoryUrl != null && url != factoryUrl
}

/**
 * Lista de canales: la de fábrica (assets/canales.json, solo lectura) más los cambios
 * del usuario guardados aparte (direcciones cambiadas y canales agregados).
 */
object ChannelStore {
    private const val USER_FILE = "canales_usuario.json"
    const val HOME_COUNTRY = "Colombia"

    private var factory: List<Channel>? = null

    private fun factory(context: Context): List<Channel> = factory ?: run {
        val text = context.assets.open("canales.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
        val array = JSONArray(text)
        List(array.length()) { i ->
            val o = array.getJSONObject(i)
            Channel(
                id = o.getString("id"),
                name = o.getString("name"),
                country = o.getString("country"),
                logo = o.optString("logo"),
                url = o.getString("url"),
                factoryUrl = o.getString("url"),
                type = o.optString("type", "hls"),
                referrer = o.optString("referrer"),
                userAgent = o.optString("userAgent"),
            )
        }.also { factory = it }
    }

    private fun userFile(context: Context) = File(context.filesDir, USER_FILE)

    private fun readUser(context: Context): JSONObject {
        val file = userFile(context)
        if (!file.exists()) return JSONObject().put("overrides", JSONObject()).put("added", JSONArray())
        return runCatching { JSONObject(file.readText()) }.getOrElse {
            JSONObject().put("overrides", JSONObject()).put("added", JSONArray())
        }
    }

    private fun writeUser(context: Context, data: JSONObject) {
        val file = userFile(context)
        val tmp = File(file.parentFile, "$USER_FILE.tmp")
        tmp.writeText(data.toString())
        tmp.renameTo(file)
    }

    fun all(context: Context): List<Channel> {
        val user = readUser(context)
        val overrides = user.optJSONObject("overrides") ?: JSONObject()
        val result = factory(context).map { channel ->
            overrides.optString(channel.id).takeIf { it.isNotBlank() }?.let { channel.copy(url = it) } ?: channel
        }.toMutableList()
        val added = user.optJSONArray("added") ?: JSONArray()
        for (i in 0 until added.length()) {
            val o = added.getJSONObject(i)
            val url = o.getString("url")
            result += Channel(
                id = o.getString("id"),
                name = o.getString("name"),
                country = o.getString("country"),
                logo = "",
                url = url,
                factoryUrl = null,
                type = if (isYouTubeUrl(url)) "youtube" else "hls",
                referrer = "",
                userAgent = "",
            )
        }
        return result
    }

    /** Países con Colombia primero y el resto en orden alfabético. */
    fun countries(context: Context): List<String> =
        all(context).map { it.country }.distinct()
            .sortedWith(compareBy<String>({ it != HOME_COUNTRY }, { it.lowercase() }))

    fun byCountry(context: Context, country: String): List<Channel> =
        all(context).filter { it.country == country }

    fun find(context: Context, id: String): Channel? = all(context).firstOrNull { it.id == id }

    /** Cambia la dirección de un canal. Para los de fábrica la original se conserva aparte. */
    fun setUrl(context: Context, id: String, url: String) {
        val user = readUser(context)
        if (factory(context).any { it.id == id }) {
            user.getJSONObject("overrides").put(id, url)
        } else {
            val added = user.getJSONArray("added")
            for (i in 0 until added.length()) {
                if (added.getJSONObject(i).getString("id") == id) added.getJSONObject(i).put("url", url)
            }
        }
        writeUser(context, user)
    }

    /** Vuelve a la dirección de fábrica. */
    fun restore(context: Context, id: String) {
        val user = readUser(context)
        user.getJSONObject("overrides").remove(id)
        writeUser(context, user)
    }

    fun add(context: Context, name: String, country: String, url: String): String {
        val user = readUser(context)
        val id = "usuario-" + UUID.randomUUID().toString().take(8)
        user.getJSONArray("added").put(
            JSONObject().put("id", id).put("name", name).put("country", country).put("url", url)
        )
        writeUser(context, user)
        return id
    }

    /** Solo se pueden borrar los canales que agregó el usuario; los de fábrica nunca. */
    fun removeAdded(context: Context, id: String) {
        val user = readUser(context)
        val added = user.getJSONArray("added")
        val kept = JSONArray()
        for (i in 0 until added.length()) {
            if (added.getJSONObject(i).getString("id") != id) kept.put(added.getJSONObject(i))
        }
        user.put("added", kept)
        writeUser(context, user)
    }

    fun isYouTubeUrl(url: String) = url.contains("youtube.com") || url.contains("youtu.be")
}
