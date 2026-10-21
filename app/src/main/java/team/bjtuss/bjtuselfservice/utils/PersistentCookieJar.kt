package team.bjtuss.bjtuselfservice.utils

import android.content.Context
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray

/**
 * Cookie jar that keeps the MIS -> bksy -> course-platform session across app
 * restarts. Cookies are matched by domain/path, not by the host that happened
 * to set them, so a .bjtu.edu.cn cookie is visible to every subdomain.
 */
class PersistentCookieJar(context: Context) : CookieJar {
    private val prefs = context.getSharedPreferences("cookie_jar", Context.MODE_PRIVATE)
    private val cookies = mutableListOf<Cookie>()

    init {
        load()
    }

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val now = System.currentTimeMillis()
        this.cookies.removeAll { it.expiresAt < now }
        cookies.forEach { incoming ->
            this.cookies.removeAll {
                it.name == incoming.name &&
                    it.domain == incoming.domain &&
                    it.path == incoming.path
            }
            this.cookies.add(incoming)
        }
        persist()
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        cookies.removeAll { it.expiresAt < now }
        return cookies.filter { it.matches(url) }
    }

    @Synchronized
    fun clear() {
        cookies.clear()
        prefs.edit().remove(KEY).apply()
    }

    private fun persist() {
        val array = JSONArray()
        cookies.forEach { array.put(it.toString()) }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    private fun load() {
        val raw = prefs.getString(KEY, null) ?: return
        runCatching {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val line = array.optString(i)
                val domain = DOMAIN.find(line)?.groupValues?.get(1)?.removePrefix(".")
                    ?: continue
                val url = ("https://" + domain + "/").toHttpUrlOrNull() ?: continue
                Cookie.parse(url, line)?.let { cookies.add(it) }
            }
        }
    }

    private companion object {
        const val KEY = "cookies"
        val DOMAIN = Regex("domain=([^;]+)")
    }
}
