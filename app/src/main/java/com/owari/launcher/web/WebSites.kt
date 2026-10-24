package com.owari.launcher.web

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class WebSite(val name: String, val url: String)

/** Sitios guardados de la sección Web. Mercado Play viene de fábrica. */
object WebSites {
    private const val PREFS = "ajustes"
    private const val KEY = "sitios_web"
    private val DEFAULTS = listOf(WebSite("Mercado Play", "https://play.mercadolibre.com.co/"))

    fun all(context: Context): List<WebSite> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            ?: return DEFAULTS
        val array = runCatching { JSONArray(raw) }.getOrElse { return DEFAULTS }
        return List(array.length()) { i ->
            val o = array.getJSONObject(i)
            // La primera versión guardaba la dirección general; la de Colombia es .com.co
            val url = o.getString("url").let { if (it.trimEnd('/') == "https://play.mercadolibre.com") DEFAULTS[0].url else it }
            WebSite(o.getString("name"), url)
        }
    }

    fun add(context: Context, name: String, url: String) = save(context, all(context) + WebSite(name, url))

    fun remove(context: Context, site: WebSite) = save(context, all(context) - site)

    private fun save(context: Context, sites: List<WebSite>) {
        val array = JSONArray()
        sites.forEach { array.put(JSONObject().put("name", it.name).put("url", it.url)) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, array.toString()).apply()
    }
}
