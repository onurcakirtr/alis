package com.alis.core.data

import android.content.Context

/** Uygulamanın tek nesne grafiği (basit servis bulucu). Application bunu oluşturur ve [GraphProvider] ile verir. */
class Graph(ctx: Context) {
    val app: Context = ctx.applicationContext
    val net = Net(app)
    val settings = SettingsStore(app)
    val servers = ServerRepository(app, net, settings)
    val library = Library(net, servers)
    val transfers = Transfers(app, net, servers)
}

interface GraphProvider {
    val graph: Graph
}

val Context.graph: Graph get() = (applicationContext as GraphProvider).graph
