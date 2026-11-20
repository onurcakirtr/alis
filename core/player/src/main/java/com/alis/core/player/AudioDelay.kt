package com.alis.core.player

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer

/**
 * Ses gecikmesi (görüntüyle senkron düzeltme).
 *  - Pozitif: ses geç gelir — her sıfırlamada (başlangıç / sarma) başa sessizlik eklenir
 *  - Negatif: ses erken gelir — her sıfırlamada baştan o kadar ses atlanır
 * Oynatıcının saati sesten çalındığı için görüntü aynı kalır, ses kayar.
 * Yeni değer bir sonraki sıfırlamada (sarma) uygulanır; oynatıcı bunu değişiklikte kendisi tetikler.
 */
@OptIn(UnstableApi::class)
class AudioDelayProcessor : BaseAudioProcessor() {
    @Volatile
    var delayMs: Long = 0

    private var pendingSilence = 0L
    private var pendingDrop = 0L

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        return inputAudioFormat
    }

    private fun frameBytes(): Int = inputAudioFormat.bytesPerFrame.coerceAtLeast(1)

    private fun bytesFor(ms: Long): Long {
        val frames = inputAudioFormat.sampleRate.toLong() * ms / 1000
        return frames * frameBytes()
    }

    override fun onFlush() {
        val d = delayMs
        pendingSilence = if (d > 0) bytesFor(d) else 0
        pendingDrop = if (d < 0) bytesFor(-d) else 0
    }

    override fun onReset() {
        pendingSilence = 0
        pendingDrop = 0
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        var remaining = inputBuffer.remaining()
        if (remaining == 0) return
        // Atlanacak ses (negatif gecikme)
        if (pendingDrop > 0) {
            val n = minOf(pendingDrop, remaining.toLong()).toInt()
            inputBuffer.position(inputBuffer.position() + n)
            pendingDrop -= n
            remaining -= n
            if (remaining == 0) return
        }
        val silence = minOf(pendingSilence, 512L * 1024).toInt()
        val out = replaceOutputBuffer(silence + remaining)
        if (silence > 0) {
            repeat(silence) { out.put(0) }
            pendingSilence -= silence
        }
        out.put(inputBuffer)
        out.flip()
    }
}
