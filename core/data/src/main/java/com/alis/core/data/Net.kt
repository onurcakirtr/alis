package com.alis.core.data

import android.content.Context
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Kalıcı, şifreli çerez kavanozu: Akış'ın PIN (akis_pin), uzaktan oturum (akis_remote)
 * ve yönetim (akis_admin) çerezleri uygulama kapansa da hatırlanır.
 */
class SecureCookieJar(ctx: Context) : CookieJar {
    private val file = File(ctx.filesDir, "cookies.bin")
    private val store = ConcurrentHashMap<String, Cookie>()

    init {
        runCatching {
            if (file.exists()) {
                val a = JSONArray(SecureBox.decrypt(file.readText()) ?: "[]")
                val now = System.currentTimeMillis()
                for (i in 0 until a.length()) {
                    val o = a.getJSONObject(i)
                    val b = Cookie.Builder().name(o.getString("n")).value(o.getString("v")).path(o.optString("p", "/"))
                        .expiresAt(o.getLong("e"))
                    if (o.optBoolean("ho")) b.hostOnlyDomain(o.getString("d")) else b.domain(o.getString("d"))
                    if (o.optBoolean("s")) b.secure()
                    if (o.optBoolean("h")) b.httpOnly()
                    val c = b.build()
                    if (c.expiresAt > now) store[keyOf(c)] = c
                }
            }
        }
    }

    private fun keyOf(c: Cookie) = "${c.domain}|${c.path}|${c.name}"

    @Synchronized
    private fun persist() {
        val a = JSONArray()
        store.values.filter { it.persistent }.forEach { c ->
            a.put(
                JSONObject().put("n", c.name).put("v", c.value).put("d", c.domain).put("p", c.path).put("e", c.expiresAt)
                    .put("s", c.secure).put("h", c.httpOnly).put("ho", c.hostOnly),
            )
        }
        runCatching { file.writeText(SecureBox.encrypt(a.toString())) }
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val now = System.currentTimeMillis()
        for (c in cookies) {
            if (c.expiresAt <= now) store.remove(keyOf(c)) else store[keyOf(c)] = c
        }
        persist()
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        return store.values.filter { it.expiresAt > now && it.matches(url) }
    }

    /** Bir sunucunun bütün çerezlerini siler (sunucu kaldırılınca / çıkış). */
    fun clear(host: String) {
        store.keys.filter { it.startsWith("$host|") }.forEach { store.remove(it) }
        persist()
    }
}

/**
 * Eşleşmiş Akış'lara "Authorization: Bearer" ekler. Anahtar, isteğin gittiği
 * "host:port" için kayıtlıysa eklenir (Coil ve ExoPlayer da aynı istemciyi kullanır).
 */
class AuthInterceptor : Interceptor {
    private val tokens = ConcurrentHashMap<String, String>()

    fun set(hostPort: String, token: String?) {
        if (token.isNullOrEmpty()) tokens.remove(hostPort) else tokens[hostPort] = token
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val req = chain.request()
        val token = tokens["${req.url.host}:${req.url.port}"]
        if (token == null || req.header("Authorization") != null) return chain.proceed(req)
        return chain.proceed(req.newBuilder().header("Authorization", "Bearer $token").build())
    }

    companion object {
        /** "http://1.2.3.4:8200" → "1.2.3.4:8200" (varsayılan portlar dahil) */
        fun keyOf(base: String): String {
            val url = base.toHttpUrlOrNull() ?: return base
            return "${url.host}:${url.port}"
        }
    }
}

/** Uygulama genelindeki tek HTTP istemcisi. */
class Net(ctx: Context) {
    companion object {
        const val USER_AGENT = "Alis/1.0 (Android)"
    }

    val cookies = SecureCookieJar(ctx)
    val auth = AuthInterceptor()
    val client: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookies)
        .addInterceptor(auth)
        // Akış, web arayüzü kapalıyken de Alış'ı bununla tanır
        .addInterceptor { chain -> chain.proceed(chain.request().newBuilder().header("User-Agent", USER_AGENT).build()) }
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
}
