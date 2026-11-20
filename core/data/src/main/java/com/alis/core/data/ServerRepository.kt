package com.alis.core.data

import android.content.Context
import com.alis.core.akis.AkisClient
import com.alis.core.discovery.DeviceDescription
import com.alis.core.discovery.SsdpDiscovery
import com.alis.core.model.Capabilities
import com.alis.core.model.Server
import com.alis.core.model.ServerKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Sunucular: keşfedilenler (SSDP) + kayıtlılar (elle, QR, uzak). Kayıtlar files/servers.json'da,
 * eşleştirme anahtarları Keystore ile şifreli.
 */
class ServerRepository(private val ctx: Context, private val net: Net, private val settings: SettingsStore) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val file = File(ctx.filesDir, "servers.json")
    private val discovery = SsdpDiscovery(ctx, net.client)
    private val quick = net.client.newBuilder().connectTimeout(3, TimeUnit.SECONDS).readTimeout(5, TimeUnit.SECONDS).build()

    private val _saved = MutableStateFlow(load())
    private val _found = MutableStateFlow<Map<String, Server>>(emptyMap())
    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val caps = ConcurrentHashMap<String, Capabilities>()
    private val _capsVersion = MutableStateFlow(0)
    val capsVersion: StateFlow<Int> = _capsVersion.asStateFlow()

    /** Kayıtlılar önce; çevrim içi durumu keşiften gelir. Genel DLNA kapalıysa Akış olmayanlar gizlenir. */
    val servers: StateFlow<List<Server>> = combine(_saved, _found, settings.state) { saved, found, s ->
        val out = LinkedHashMap<String, Server>()
        for (sv in saved) {
            val f = found[sv.id]
            out[sv.id] = if (f != null) sv.copy(online = true, base = if (sv.remote) sv.base else f.base, icon = f.icon ?: sv.icon, cdControl = f.cdControl ?: sv.cdControl, lastSeen = f.lastSeen) else sv
        }
        for ((id, f) in found) if (id !in out) out[id] = f
        out.values.filter { s.on(Feature.GENERIC_DLNA) || it.kind == ServerKind.AKIS }
            .sortedWith(compareBy({ !it.saved }, { !it.online }, { it.kind != ServerKind.AKIS }, { it.name.lowercase() }))
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    init {
        _saved.value.forEach { registerToken(it) }
    }

    private fun registerToken(s: Server) {
        net.auth.set(AuthInterceptor.keyOf(s.base), s.token)
    }

    // ---------------- Kayıt ----------------

    private fun load(): List<Server> = runCatching {
        val a = JSONArray(file.readText())
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            Server(
                id = o.getString("id"), name = o.optString("name"), base = o.getString("base"),
                kind = if (o.optString("kind") == "AKIS") ServerKind.AKIS else ServerKind.DLNA,
                icon = o.optString("icon").ifEmpty { null }, cdControl = o.optString("cd").ifEmpty { null },
                saved = true, remote = o.optBoolean("remote"),
                token = o.optString("token").takeIf { it.isNotEmpty() }?.let { SecureBox.decrypt(it) },
                lastSeen = o.optLong("seen"), model = o.optString("model").ifEmpty { null },
            )
        }
    }.getOrDefault(emptyList())

    @Synchronized
    private fun persist(list: List<Server>) {
        _saved.value = list
        val a = JSONArray()
        list.forEach { s ->
            a.put(
                JSONObject().put("id", s.id).put("name", s.name).put("base", s.base).put("kind", s.kind.name)
                    .put("icon", s.icon ?: "").put("cd", s.cdControl ?: "").put("remote", s.remote)
                    .put("token", s.token?.let { SecureBox.encrypt(it) } ?: "").put("seen", s.lastSeen).put("model", s.model ?: ""),
            )
        }
        runCatching { file.writeText(a.toString()) }
        list.forEach { registerToken(it) }
    }

    fun byId(id: String): Server? = servers.value.firstOrNull { it.id == id } ?: _saved.value.firstOrNull { it.id == id }

    fun save(s: Server) {
        val list = _saved.value.filter { it.id != s.id } + s.copy(saved = true)
        persist(list)
    }

    fun remove(id: String) {
        val s = _saved.value.firstOrNull { it.id == id } ?: return
        net.auth.set(AuthInterceptor.keyOf(s.base), null)
        runCatching { net.cookies.clear(s.host) }
        persist(_saved.value.filter { it.id != id })
        caps.remove(id)
    }

    fun rename(id: String, name: String) {
        val s = byId(id) ?: return
        save(s.copy(name = name.trim().ifEmpty { s.name }))
    }

    /** Eşleştirme anahtarını kaydeder (sunucu da kayıtlılara eklenir). */
    fun setToken(id: String, token: String?) {
        val s = byId(id) ?: return
        save(s.copy(token = token))
        registerToken(s.copy(token = token))
        caps.remove(id)
        _capsVersion.value++
    }

    // ---------------- Keşif ----------------

    private var scanJob: Job? = null

    fun scan() {
        if (scanJob?.isActive == true) return
        scanJob = scope.launch {
            _scanning.value = true
            val seen = HashMap<String, Server>()
            try {
                // "Otomatik keşif" kapalıysa yalnızca kayıtlı sunucular yoklanır
                if (settings.current.on(Feature.DISCOVERY)) {
                    discovery.search(5000).collect { d ->
                        val s = d.toServer()
                        seen[s.id] = s
                        _found.value = _found.value + (s.id to s)
                    }
                }
                // Keşifte görünmeyen kayıtlıları doğrudan yokla (ör. çok noktaya yayını engelleyen ağlar, uzak adresler)
                coroutineScope {
                    _saved.value.filter { it.id !in seen }.map { s -> async { s to ping(s) } }.awaitAll()
                }.forEach { (s, ok) ->
                    _found.value = if (ok) _found.value + (s.id to s.copy(online = true, lastSeen = System.currentTimeMillis())) else _found.value - s.id
                }
                // Önceki taramada bulunup şimdi cevap vermeyenleri çevrim dışı say
                _found.value = _found.value.filterKeys { it in seen || _found.value[it]?.lastSeen?.let { t -> System.currentTimeMillis() - t < 15_000 } == true }
            } finally {
                _scanning.value = false
            }
        }
    }

    private suspend fun ping(s: Server): Boolean = withContext(Dispatchers.IO) {
        val path = if (s.isAkis) "/icon48.png" else "/"
        runCatching {
            quick.newCall(Request.Builder().url(s.base + path).head().build()).execute().use { it.code < 500 }
        }.getOrDefault(false)
    }

    /**
     * Adres ekleme: "192.168.1.5", "192.168.1.5:8200", "http://…", "https://xyz.trycloudflare.com".
     * Önce UPnP tanımı denenir, olmazsa Akış'ın /api/conf'u (tünelde desc.xml kapalıdır).
     */
    suspend fun addAddress(input: String): Result<Server> = withContext(Dispatchers.IO) {
        val raw = input.trim().trimEnd('/')
        if (raw.isEmpty()) return@withContext Result.failure(IllegalArgumentException("empty"))
        val candidates = when {
            raw.startsWith("http://") || raw.startsWith("https://") -> listOf(raw)
            raw.contains(':') -> listOf("http://$raw")
            raw.contains('.') && raw.any { it.isLetter() } && !raw.endsWith(".local") -> listOf("https://$raw", "http://$raw:8200", "http://$raw")
            else -> listOf("http://$raw:8200", "http://$raw")
        }
        for (base in candidates) {
            val origin = SsdpDiscovery.origin(base)
            val remote = origin.startsWith("https://")
            if (!remote) {
                discovery.describe("$origin/desc.xml")?.let { d ->
                    val s = d.toServer().copy(saved = true, remote = false)
                    save(s)
                    _found.value = _found.value + (s.id to s)
                    return@withContext Result.success(s)
                }
            }
            val conf = withTimeoutOrNull(8000) {
                runCatching { AkisClient(quick, Server(origin, "", origin, ServerKind.AKIS)).conf() }.getOrNull()
            }
            // Erişim (giriş / onay) gerekiyorsa da Akış'tır: kaydedilir, ekranda giriş istenir
            val isAkis = conf != null || withTimeoutOrNull(5000) { looksLikeAkis(origin) } == true
            if (isAkis) {
                val s = Server(
                    id = if (remote) "remote:$origin" else origin, name = conf?.name?.ifEmpty { null } ?: origin.substringAfter("://"),
                    base = origin, kind = ServerKind.AKIS, icon = "$origin/icon.png", saved = true, remote = remote, online = true,
                    lastSeen = System.currentTimeMillis(),
                )
                save(s)
                conf?.let { caps[s.id] = it }
                return@withContext Result.success(s)
            }
        }
        Result.failure(java.io.IOException("unreachable"))
    }

    private fun looksLikeAkis(origin: String): Boolean = runCatching {
        quick.newCall(Request.Builder().url("$origin/api/conf").build()).execute().use { r ->
            r.header("X-Akis-Access") != null || r.code == 401
        }
    }.getOrDefault(false)

    fun describe(d: DeviceDescription) = d.toServer()

    // ---------------- Yetenekler ----------------

    fun cachedCaps(id: String): Capabilities? = caps[id]

    suspend fun capabilities(s: Server, refresh: Boolean = false): Capabilities {
        if (!s.isAkis) return Capabilities(name = s.name, search = s.cdControl != null)
        if (!refresh) caps[s.id]?.let { return it }
        val c = client(s).conf()
        caps[s.id] = c
        _capsVersion.value++
        // Adresle eklenmiş ve adı hiç değiştirilmemişse Akış'ın kendi adını al
        if (c.name.isNotEmpty() && s.saved && s.name == s.base.substringAfter("://")) save(s.copy(name = c.name))
        return c
    }

    fun client(s: Server) = AkisClient(net.client, s)
}
