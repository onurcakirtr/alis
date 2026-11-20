package com.alis.app.player

import android.app.Activity
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.text.CueGroup
import com.alis.app.ui.Open
import com.alis.core.data.Feature
import com.alis.core.data.Graph
import com.alis.core.model.Item
import com.alis.core.model.ItemDetail
import com.alis.core.model.Server
import com.alis.core.model.Subtitle
import com.alis.core.model.Track
import com.alis.core.player.AudioDelayProcessor
import com.alis.core.player.Players
import com.alis.core.player.TrackTools
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request

/** Altyazı seçimi: kapalı, dış dosya (Alış çizer) ya da dosyanın içindeki (ExoPlayer çözer). */
sealed interface SubChoice {
    data object Off : SubChoice
    data class External(val index: Int) : SubChoice
    data class Embedded(val label: String) : SubChoice
}

/**
 * Video oynatıcının tüm durumu ve mantığı. Etkinlik yapılandırma değişikliğinde yeniden kurulmadığı için
 * burada tutulur; Compose arayüzü yalnızca okur ve komut verir.
 */
class VideoController(
    private val act: Activity,
    private val g: Graph,
    val server: Server?,
    val queue: MutableList<Item>,
    startIndex: Int,
    private val folderId: String?,
) {
    private val scope = MainScope()
    val delayProcessor = AudioDelayProcessor()
    val player = Players.exo(act, g.net.client, delay = delayProcessor)
    private val settings get() = g.settings.current

    var index by mutableIntStateOf(startIndex)
    var item by mutableStateOf(queue[startIndex])
    var detail by mutableStateOf<ItemDetail?>(null)
    var externalSubs by mutableStateOf<List<Subtitle>>(emptyList())
    var subChoice by mutableStateOf<SubChoice>(SubChoice.Off)
    var cues by mutableStateOf<List<SubCue>>(emptyList())
    var subText by mutableStateOf<String?>(null)
    var subDelayMs by mutableLongStateOf(0)
    var audioDelayMs by mutableLongStateOf(0)
    var speed by mutableFloatStateOf(1f)
        private set
    var aspect by mutableStateOf("fit") // fit | fill | zoom | 16:9 | 4:3
    var repeat by mutableIntStateOf(0)  // 0 kapalı, 1 tek, 2 liste
    var shuffle by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var playing by mutableStateOf(false)
    var buffering by mutableStateOf(true)
    var positionMs by mutableLongStateOf(0)
    var durationMs by mutableLongStateOf(0)
    var resumedFrom by mutableLongStateOf(0)
    var tracksVersion by mutableIntStateOf(0)
    var subColor by mutableStateOf(0xFFFFFFFF.toInt())
    var subBottom by mutableFloatStateOf(0.06f)
    var eqPreset by mutableIntStateOf(-1)
    var boost by mutableIntStateOf(0) // mB (0 – 1500)
    var message by mutableStateOf<String?>(null)

    /** Sıradaki video geri sayımı (otomatik oynatma "5 sn sonra"): gösterilecek öğe ve kalan saniye */
    var upNext by mutableStateOf<Item?>(null)
    var upNextLeft by mutableIntStateOf(0)
    private var countdown: Job? = null
    /** Bu dosyada gömülü altyazı için dil tercihine bakıldı mı */
    private var autoEmbeddedDone = false

    /** Akış remux (AC3 / DTS gibi cihazın çözemediği sesler AAC'ye çevrilerek fMP4 akışı) */
    var remux by mutableStateOf(false)
    private var remuxBase = 0L
    var remuxAudio by mutableIntStateOf(-1)
    var probeTracks by mutableStateOf<List<Track>>(emptyList())
    private var probeDuration = 0L

    private val embeddedHistory = ArrayDeque<Pair<Long, String?>>()
    private var lastReport = 0L
    private var equalizer: Equalizer? = null
    private var loudness: LoudnessEnhancer? = null
    private var ticker: Job? = null

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            playing = isPlaying
            if (!isPlaying) report(force = true)
        }

        override fun onPlaybackStateChanged(state: Int) {
            buffering = state == Player.STATE_BUFFERING
            if (state == Player.STATE_ENDED) onEnded()
        }

        override fun onTracksChanged(tracks: Tracks) {
            tracksVersion++
            autoEmbedded(tracks)
            // Ses çözülemiyorsa Akış'ın remux'una geç (bir kez)
            if (!remux && trackable && TrackTools.audioUnsupported(tracks)) fallbackToRemux(player.currentPosition)
        }

        override fun onCues(cueGroup: CueGroup) {
            val text = cueGroup.cues.mapNotNull { it.text?.toString() }.joinToString("\n").ifEmpty { null }
            embeddedHistory.addLast(position() to text)
            while (embeddedHistory.size > 400) embeddedHistory.removeFirst()
        }

        override fun onPlayerError(e: PlaybackException) {
            val decoder = e.errorCode in setOf(
                PlaybackException.ERROR_CODE_DECODER_INIT_FAILED, PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
                PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES, PlaybackException.ERROR_CODE_DECODING_FAILED,
                PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            )
            if (!remux && decoder && trackable) {
                fallbackToRemux(positionMs)
            } else {
                error = e.errorCodeName + (e.message?.let { "\n$it" } ?: "")
            }
        }

        override fun onAudioSessionIdChanged(audioSessionId: Int) = applyAudioEffects()
    }

    init {
        player.addListener(listener)
        // Renderer (DLNA oynatıcı) modundan geldiyse denetleyici bu oynatıcıyı yönetir
        if (queue.firstOrNull()?.id == "offline:dmr") com.alis.app.RendererBridge.video = player
        load(startIndex)
        ticker = scope.launch {
            while (isActive) {
                positionMs = position()
                durationMs = duration()
                subText = currentSub()
                if (playing) report()
                delay(100)
            }
        }
    }

    // ---------------- Yükleme ----------------

    fun position(): Long = if (remux) remuxBase + player.currentPosition else player.currentPosition.coerceAtLeast(0)

    fun duration(): Long {
        if (remux) return probeDuration.takeIf { it > 0 } ?: item.durationMs
        val d = player.duration
        return if (d == C.TIME_UNSET || d <= 0) item.durationMs else d
    }

    private val trackable: Boolean get() = server?.isAkis == true && !item.id.startsWith("offline:") && !item.id.startsWith("live:")

    fun load(i: Int, startMs: Long = -1) {
        cancelUpNext()
        autoEmbeddedDone = false
        report(force = true)
        index = i
        item = queue[i]
        detail = null
        externalSubs = emptyList()
        cues = emptyList()
        subChoice = SubChoice.Off
        remux = false
        remuxAudio = -1
        probeTracks = emptyList()
        error = null
        resumedFrom = 0
        embeddedHistory.clear()
        val it0 = item
        scope.launch {
            val s = server
            val d = if (s != null && !it0.id.startsWith("offline:") && !it0.id.startsWith("live:")) {
                runCatching { g.library.detail(s, it0, folderId) }.getOrNull()
            } else null
            if (it0 != item) return@launch
            detail = d
            val playItem = Open.offline(act, d?.item?.copy(url = d.item.url ?: it0.url) ?: it0)
            val resume = when {
                startMs >= 0 -> startMs
                settings.on(Feature.RESUME_SYNC) && (d?.resumeMs ?: 0) > 5000 -> d!!.resumeMs
                else -> 0L
            }
            resumedFrom = resume
            if (settings.on(Feature.SUBTITLES)) externalSubs = d?.subs?.filter { !it.embedded }.orEmpty()
            player.setMediaItem(Players.toMediaItem(playItem, mime = if (playItem.ext == "ts") MimeTypes.VIDEO_MP2T else null), resume)
            TrackTools.prefer(player, settings.audioLang, settings.subLang, subsOn = false)
            player.playbackParameters = PlaybackParameters(speed)
            player.prepare()
            player.play()
            autoSubtitle()
            if (s?.isAkis == true && trackable) {
                runCatching { g.servers.client(s).probe(it0.id) }.getOrNull()?.let { (dur, tracks) ->
                    probeDuration = dur
                    probeTracks = tracks
                }
            }
        }
    }

    /** Tercih edilen dilde dış altyazı varsa onu seç (yoksa kapalı kalır, kullanıcı açar). */
    private fun autoSubtitle() {
        if (!settings.on(Feature.SUBTITLES)) return
        val want = settings.subLang.ifEmpty { server?.let { g.servers.cachedCaps(it.id)?.subLang }.orEmpty() }
        val list = externalSubs
        val pick = list.indexOfFirst { it.forced }.takeIf { it >= 0 }
            ?: list.indexOfFirst { want.isNotEmpty() && it.lang.equals(want, true) }.takeIf { it >= 0 }
        if (pick != null) selectExternal(pick, remember = false)
    }

    /**
     * Cihaz sesi çözemedi: Akış'ın anlık çevirisine geç. Ses parçası listesi (probe) henüz gelmediyse önce onu al;
     * parça numarası Akış'ın verdiği numaradır (0 çoğu zaman videodur).
     */
    private fun fallbackToRemux(atMs: Long) {
        if (remux) return
        remux = true // ikinci kez tetiklenmesin
        val s = server ?: return
        val id = item.id
        scope.launch {
            if (probeTracks.isEmpty()) {
                runCatching { g.servers.client(s).probe(id) }.getOrNull()?.let { (dur, tracks) ->
                    probeDuration = dur
                    probeTracks = tracks
                }
            }
            if (id != item.id) return@launch
            val audio = probeTracks.firstOrNull { !it.video && it.canTranscode } ?: probeTracks.firstOrNull { !it.video }
            message = act.getString(com.alis.app.R.string.pl_converting)
            startRemux(atMs, audio?.index ?: 1, audio?.canTranscode ?: true)
        }
    }

    private var remuxTx = true

    private fun startRemux(atMs: Long, audio: Int, transcode: Boolean) {
        val s = server ?: return
        val c = g.servers.client(s)
        remux = true
        remuxBase = atMs.coerceAtLeast(0)
        remuxAudio = audio
        remuxTx = transcode
        val url = c.remuxUrl(item.id, remuxBase / 1000.0, audio, transcode)
        val mi = MediaItem.Builder().setUri(url).setMimeType(MimeTypes.VIDEO_MP4).setMediaId(Players.mediaId(item)).build()
        player.setMediaItem(mi)
        player.prepare()
        player.play()
    }

    // ---------------- Komutlar ----------------

    fun toggle() = if (player.isPlaying) player.pause() else { if (player.playbackState == Player.STATE_ENDED) seekTo(0); player.play() }

    fun seekTo(ms: Long) {
        val target = ms.coerceIn(0, (duration().takeIf { it > 0 } ?: Long.MAX_VALUE))
        embeddedHistory.clear()
        if (remux) startRemux(target, remuxAudio, remuxTx) else player.seekTo(target)
    }

    fun seekBy(deltaMs: Long) = seekTo(position() + deltaMs)

    fun hasNext() = shuffle && queue.size > 1 || index < queue.size - 1 || detail?.next != null
    fun hasPrev() = index > 0

    fun next() {
        when {
            shuffle && queue.size > 1 -> load((queue.indices - index).random())
            index < queue.size - 1 -> load(index + 1)
            repeat == 2 && queue.size > 1 -> load(0)
            else -> detail?.next?.let { n -> playExternalNext(n) }
        }
    }

    fun prev() {
        if (position() > 5000 || index == 0) seekTo(0) else load(index - 1)
    }

    /** Akış'ın "sıradaki"si kuyrukta değilse (tek öğeyle açıldıysa) sona ekleyip oynat. */
    private fun playExternalNext(n: Item) {
        queue.add(n)
        load(queue.size - 1)
    }

    /** next() neyi oynatacak (geri sayım kartında gösterilir); karışıkta bilinmez. */
    private fun peekNext(): Item? = when {
        shuffle && queue.size > 1 -> null
        index < queue.size - 1 -> queue[index + 1]
        repeat == 2 && queue.size > 1 -> queue[0]
        else -> detail?.next
    }

    private fun onEnded() {
        report(force = true, ended = true)
        val mode = settings.autoplay
        when {
            repeat == 1 -> { seekTo(0); player.play() }
            mode == "now" && (hasNext() || repeat == 2) -> next()
            mode == "countdown" && (hasNext() || repeat == 2) -> startCountdown()
        }
    }

    private fun startCountdown() {
        countdown?.cancel()
        upNext = peekNext() ?: queue.getOrNull(index)
        upNextLeft = 5
        countdown = scope.launch {
            while (upNextLeft > 0) {
                delay(1000)
                upNextLeft--
            }
            playUpNext()
        }
    }

    /** Geri sayımı beklemeden sıradakine geç. */
    fun playUpNext() {
        countdown?.cancel()
        countdown = null
        upNext = null
        next()
    }

    fun cancelUpNext() {
        countdown?.cancel()
        countdown = null
        upNext = null
    }

    /** Altyazı dili tercihi varsa ve dış altyazı seçilmediyse, dosyanın içindeki aynı dildeki altyazıyı aç. */
    private fun autoEmbedded(tracks: Tracks) {
        if (autoEmbeddedDone || tracks.groups.isEmpty()) return
        autoEmbeddedDone = true
        val want = settings.subLang
        if (want.isEmpty() || subChoice != SubChoice.Off || !settings.on(Feature.SUBTITLES)) return
        val opt = TrackTools.options(player, C.TRACK_TYPE_TEXT).firstOrNull { o ->
            o.group.getTrackFormat(o.index).language?.startsWith(want, ignoreCase = true) == true
        } ?: return
        subChoice = SubChoice.Embedded(opt.label)
        TrackTools.select(player, opt)
    }

    fun changeSpeed(v: Float) {
        speed = v
        player.playbackParameters = PlaybackParameters(v)
    }

    fun setAudioDelay(ms: Long) {
        audioDelayMs = ms
        delayProcessor.delayMs = ms
        // İşlemcinin yeni gecikmeyi uygulaması için ses yolunu sıfırla
        if (!remux) player.seekTo(player.currentPosition)
    }

    fun selectExternal(i: Int, remember: Boolean = true) {
        val sub = externalSubs.getOrNull(i) ?: return
        if (remember && sub.lang.isNotEmpty()) g.settings.setSubLang(sub.lang.lowercase())
        subChoice = SubChoice.External(i)
        TrackTools.disable(player, C.TRACK_TYPE_TEXT)
        cues = emptyList()
        scope.launch {
            val parsed = withContext(Dispatchers.IO) {
                runCatching {
                    g.net.client.newCall(Request.Builder().url(sub.url).build()).execute().use { r ->
                        SubParser.parse(r.body?.string().orEmpty(), sub.url.substringBefore('?').lowercase())
                    }
                }.getOrDefault(emptyList())
            }
            if (subChoice == SubChoice.External(i)) cues = parsed
        }
    }

    fun selectEmbedded(o: TrackTools.Option) {
        o.group.getTrackFormat(o.index).language?.takeIf { it.isNotEmpty() && it != "und" }?.let { g.settings.setSubLang(it.substringBefore('-').lowercase()) }
        subChoice = SubChoice.Embedded(o.label)
        cues = emptyList()
        embeddedHistory.clear()
        TrackTools.select(player, o)
    }

    fun subtitlesOff() {
        // Kapatılınca bir sonraki videoda da kendiliğinden açılmasın
        g.settings.setSubLang("")
        subChoice = SubChoice.Off
        cues = emptyList()
        TrackTools.disable(player, C.TRACK_TYPE_TEXT)
    }

    fun selectAudio(o: TrackTools.Option) {
        o.group.getTrackFormat(o.index).language?.takeIf { it.isNotEmpty() && it != "und" }?.let { g.settings.setAudioLang(it.substringBefore('-').lowercase()) }
        TrackTools.select(player, o)
    }

    /** Remux kipinde ses parçası Akış'tan seçilir (akış yeniden başlar). */
    fun selectRemuxAudio(t: Track) = startRemux(position(), t.index, t.canTranscode)

    private fun currentSub(): String? {
        val pos = position() - subDelayMs
        return when (subChoice) {
            is SubChoice.External -> SubParser.at(cues, pos)
            is SubChoice.Embedded -> {
                if (subDelayMs <= 0) embeddedHistory.lastOrNull()?.second
                else embeddedHistory.lastOrNull { it.first <= pos }?.second
            }
            SubChoice.Off -> null
        }
    }

    // ---------------- Ses efektleri ----------------

    fun eqPresets(): List<String> = runCatching {
        val e = equalizer ?: return emptyList()
        (0 until e.numberOfPresets).map { e.getPresetName(it.toShort()) }
    }.getOrDefault(emptyList())

    fun setEq(preset: Int) {
        eqPreset = preset
        applyAudioEffects()
    }

    fun setBoostLevel(mb: Int) {
        boost = mb
        applyAudioEffects()
    }

    private fun applyAudioEffects() {
        val session = player.audioSessionId
        if (session == C.AUDIO_SESSION_ID_UNSET || session == 0) return
        runCatching {
            if (equalizer == null) equalizer = Equalizer(0, session)
            equalizer?.let { e ->
                e.enabled = eqPreset >= 0
                if (eqPreset >= 0 && eqPreset < e.numberOfPresets) e.usePreset(eqPreset.toShort())
            }
        }
        runCatching {
            if (loudness == null) loudness = LoudnessEnhancer(session)
            loudness?.setTargetGain(boost)
            loudness?.enabled = boost > 0
        }
    }

    // ---------------- Kaldığın yer senkronu ----------------

    private fun report(force: Boolean = false, ended: Boolean = false) {
        if (!trackable || !settings.on(Feature.RESUME_SYNC)) return
        val now = System.currentTimeMillis()
        if (!force && now - lastReport < 10_000) return
        lastReport = now
        val s = server ?: return
        val id = item.id
        val dur = duration()
        if (dur <= 0) return
        val pos = if (ended) dur else position()
        val it0 = item
        scope.launch { g.servers.client(s).reportProgress(id, pos / 1000.0, dur / 1000.0) }
        com.alis.app.WatchNext.publish(act, s.id, it0, pos, dur)
    }

    fun release() {
        report(force = true)
        ticker?.cancel()
        countdown?.cancel()
        runCatching { equalizer?.release() }
        runCatching { loudness?.release() }
        player.removeListener(listener)
        if (com.alis.app.RendererBridge.video === player) com.alis.app.RendererBridge.video = null
        player.release()
        scope.cancel()
    }

    /** Görüntü ve ses bilgisi (Bilgi paneli). */
    fun techInfo(): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        player.videoFormat?.let { f ->
            out += "Video" to listOfNotNull(f.sampleMimeType?.substringAfter('/'), "${f.width}×${f.height}", f.frameRate.takeIf { it > 0 }?.let { "%.2f fps".format(it) }).joinToString(" · ")
            if (f.bitrate > 0) out += "Video bit" to "${f.bitrate / 1000} kb/s"
        }
        player.audioFormat?.let { f ->
            out += "Audio" to listOfNotNull(f.sampleMimeType?.substringAfter('/'), "${f.channelCount} ch", "${f.sampleRate} Hz", f.language).joinToString(" · ")
        }
        out += "URL" to (player.currentMediaItem?.localConfiguration?.uri?.toString() ?: "")
        if (remux) out += "Mode" to "remux (Akış)"
        return out
    }
}
