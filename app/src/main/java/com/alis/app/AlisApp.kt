package com.alis.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.alis.core.data.Graph
import com.alis.core.data.GraphProvider
import com.alis.core.data.Transfers
import com.alis.core.data.TransfersProvider

class AlisApp : Application(), GraphProvider, TransfersProvider, SingletonImageLoader.Factory {
    private lateinit var g: Graph
    override val graph: Graph get() = g

    override val transfers: Transfers get() = graph.transfers

    override fun onCreate() {
        super.onCreate()
        g = Graph(this)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_MIRROR, getString(R.string.ch_mirror), NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel(CH_STATUS, getString(R.string.ch_status), NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CH_RENDERER, getString(R.string.ch_renderer), NotificationManager.IMPORTANCE_LOW))
        Background.sync(this)
    }

    /** Küçük resimler de aynı istemciden: eşleştirme anahtarı ve çerezler gider. */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { graph.net.client })) }
            .crossfade(true)
            .build()

    companion object {
        const val CH_MIRROR = "mirror"
        const val CH_STATUS = "status"
        const val CH_RENDERER = "renderer"
    }
}
