package com.alis.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.staticCompositionLocalOf

/** Ekranlar. Mobil ve TV arayüzü aynı yolları kullanır, ama her biri kendi tasarımıyla çizer. */
sealed interface Route {
    data object Servers : Route
    data class Home(val server: String) : Route
    data class Browse(val server: String, val id: String, val title: String) : Route
    /** server = null: tüm sunucularda ara */
    data class Search(val server: String?) : Route
    data class Files(val server: String, val path: String) : Route
    data class Zip(val server: String, val path: String, val name: String) : Route
    data class Doc(val server: String, val path: String, val name: String) : Route
    data class Info(val server: String, val id: String?, val path: String?, val title: String) : Route
    data class Status(val server: String) : Route
    data class Apps(val server: String) : Route
    data class Live(val server: String) : Route
    data class Notifs(val server: String) : Route
    data class Inbox(val server: String) : Route
    data class Admin(val server: String) : Route
    data class Pair(val server: String, val withCode: Boolean = false) : Route
    data object AddServer : Route
    data object Scan : Route
    data object Photos : Route
    data object Music : Route
    data object Transfers : Route
    data object Settings : Route
}

/** Basit yığın: Geri tuşu en üstteki ekranı kapatır. (Yapılandırma değişikliğinde etkinlik yeniden kurulmaz.) */
class Navigator(root: Route = Route.Servers) {
    val stack = mutableStateListOf(root)
    val top: Route get() = stack.last()
    val canPop: Boolean get() = stack.size > 1

    fun push(r: Route) {
        if (stack.last() != r) stack.add(r)
    }

    fun pop(): Boolean {
        if (stack.size <= 1) return false
        stack.removeAt(stack.lastIndex)
        return true
    }

    /** Sekme değişimi: yığını bu köke indirir. */
    fun root(r: Route) {
        stack.clear()
        stack.add(r)
    }

    fun replace(r: Route) {
        if (stack.isNotEmpty()) stack[stack.lastIndex] = r else stack.add(r)
    }
}

val LocalNav = staticCompositionLocalOf<Navigator> { error("Navigator yok") }

@Composable
fun nav(): Navigator = LocalNav.current
