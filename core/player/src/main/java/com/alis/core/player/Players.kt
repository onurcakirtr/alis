package com.alis.core.player

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.alis.core.model.Item
import com.alis.core.model.Kind
import com.alis.core.model.Subtitle
import okhttp3.OkHttpClient
import java.util.Locale

/** ExoPlayer kurulumu ve öğe dönüşümleri. */
object Players {

    @OptIn(UnstableApi::class)
    fun exo(ctx: Context, http: OkHttpClient, audioOnly: Boolean = false, delay: AudioDelayProcessor? = null): ExoPlayer {
        val upstream = OkHttpDataSource.Factory(http).setUserAgent("Alis/1.0 (Android)")
        // content:// (indirilenler) ve http aynı fabrikadan
        val data = DefaultDataSource.Factory(ctx, upstream)
        val renderers = object : DefaultRenderersFactory(ctx) {
            // Ses gecikmesi işlemcisi ses yoluna eklenir
            override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink? =
                DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .apply { if (delay != null) setAudioProcessors(arrayOf(delay)) }
                    .build()
        }
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
        return ExoPlayer.Builder(ctx, renderers)
            .setMediaSourceFactory(DefaultMediaSourceFactory(data))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(if (audioOnly) C.AUDIO_CONTENT_TYPE_MUSIC else C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
    }

    fun subtitleMime(s: Subtitle): String {
        val u = s.url.substringBefore('?').lowercase()
        return when {
            u.endsWith(".vtt") -> MimeTypes.TEXT_VTT
            u.endsWith(".ass") || u.endsWith(".ssa") -> MimeTypes.TEXT_SSA
            u.endsWith(".ttml") || u.endsWith(".xml") -> MimeTypes.APPLICATION_TTML
            else -> MimeTypes.APPLICATION_SUBRIP
        }
    }

    /** "sunucu|öğe" — ilerleme bildirimi ve sıradaki için. */
    fun mediaId(item: Item) = item.serverId + "|" + item.id

    fun serverOf(mediaId: String) = mediaId.substringBefore('|')
    fun itemOf(mediaId: String) = mediaId.substringAfter('|')

    /**
     * Kitaplık öğesini Media3 öğesine çevirir.
     * @param uri yerel (indirilmiş) dosya ya da farklı bir akış adresi (remux) verilirse o kullanılır
     */
    fun toMediaItem(item: Item, subs: List<Subtitle> = emptyList(), uri: Uri? = null, mime: String? = null): MediaItem {
        val meta = MediaMetadata.Builder()
            .setTitle(item.title)
            .setArtist(item.artist)
            .setAlbumTitle(item.album)
            .setArtworkUri(item.thumb?.let { Uri.parse(it) })
            .setMediaType(
                when (item.kind) {
                    Kind.AUDIO -> MediaMetadata.MEDIA_TYPE_MUSIC
                    Kind.VIDEO -> MediaMetadata.MEDIA_TYPE_MOVIE
                    else -> MediaMetadata.MEDIA_TYPE_MIXED
                },
            )
            .setIsPlayable(true)
            .setIsBrowsable(false)
            .build()
        val b = MediaItem.Builder()
            .setMediaId(mediaId(item))
            .setUri(uri ?: Uri.parse(item.url ?: ""))
            .setMediaMetadata(meta)
            // Müzik servisine (MediaController üzerinden) giderken adres bununla taşınır
            .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri ?: Uri.parse(item.url ?: "")).build())
        mime?.let { b.setMimeType(it) }
        if (subs.isNotEmpty()) {
            b.setSubtitleConfigurations(
                subs.filter { !it.embedded }.map { s ->
                    MediaItem.SubtitleConfiguration.Builder(Uri.parse(s.url))
                        .setMimeType(subtitleMime(s))
                        .setLanguage(s.lang.ifEmpty { null })
                        .setLabel(s.label.ifEmpty { s.lang.ifEmpty { null } })
                        .setSelectionFlags(if (s.forced) C.SELECTION_FLAG_FORCED else 0)
                        .build()
                },
            )
        }
        return b.build()
    }
}

/** Ses ve altyazı parçaları: listeleme ve seçme. */
object TrackTools {
    data class Option(val group: Tracks.Group, val index: Int, val label: String, val selected: Boolean, val supported: Boolean)

    private fun label(f: androidx.media3.common.Format, n: Int): String {
        val lang = f.language?.takeIf { it != "und" && it.isNotEmpty() }?.let { Locale.forLanguageTag(it).displayLanguage.replaceFirstChar { c -> c.uppercase() } }
        val parts = listOfNotNull(
            f.label?.takeIf { it.isNotBlank() } ?: lang,
            if (f.label != null && lang != null && f.label != lang) lang else null,
            f.channelCount.takeIf { it > 0 }?.let { ch -> if (ch >= 6) "5.1" else if (ch == 2) "2.0" else if (ch == 1) "mono" else "$ch ch" },
            f.sampleMimeType?.substringAfter('/')?.uppercase()?.takeIf { f.channelCount > 0 },
        )
        return parts.joinToString(" · ").ifEmpty { "#$n" }
    }

    fun options(player: Player, type: Int): List<Option> {
        val out = ArrayList<Option>()
        var n = 1
        for (g in player.currentTracks.groups) {
            if (g.type != type) continue
            for (i in 0 until g.length) {
                out += Option(g, i, label(g.getTrackFormat(i), n++), g.isTrackSelected(i), g.isTrackSupported(i))
            }
        }
        return out
    }

    fun select(player: Player, o: Option) {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(o.group.type, false)
            .setOverrideForType(TrackSelectionOverride(o.group.mediaTrackGroup, o.index))
            .build()
    }

    fun disable(player: Player, type: Int) {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(type)
            .setTrackTypeDisabled(type, true)
            .build()
    }

    /** Tercih edilen dil (ayarlardan / Akış'ın altyazı dilinden). */
    fun prefer(player: Player, audioLang: String, subLang: String, subsOn: Boolean) {
        val b = player.trackSelectionParameters.buildUpon()
        if (audioLang.isNotEmpty()) b.setPreferredAudioLanguage(audioLang)
        if (subLang.isNotEmpty()) b.setPreferredTextLanguage(subLang)
        b.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !subsOn)
        player.trackSelectionParameters = b.build()
    }

    /** Ses parçası var ama hiçbiri çözülemiyor mu? (AC3/DTS → Akış'ın remux'una geç) */
    fun audioUnsupported(tracks: Tracks): Boolean {
        val audio = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
        return audio.isNotEmpty() && audio.none { g -> (0 until g.length).any { g.isTrackSupported(it) } }
    }

    fun videoUnsupported(tracks: Tracks): Boolean {
        val video = tracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }
        return video.isNotEmpty() && video.none { g -> (0 until g.length).any { g.isTrackSupported(it) } }
    }
}
