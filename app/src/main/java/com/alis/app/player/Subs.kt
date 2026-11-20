package com.alis.app.player

/**
 * Dış altyazılar Alış'ın kendi çiziciyle gösterilir: böylece gecikme (ileri / geri), boyut,
 * renk ve konum serbestçe ayarlanır. Desteklenen: SRT, WebVTT, ASS/SSA (yalnız metin).
 */
data class SubCue(val start: Long, val end: Long, val text: String)

object SubParser {
    private val time = Regex("""(?:(\d+):)?(\d{1,2}):(\d{2})[.,](\d{1,3})""")

    private fun ms(h: String?, m: String, s: String, frac: String): Long {
        val f = frac.padEnd(3, '0').take(3).toLong()
        return ((h?.toLongOrNull() ?: 0) * 3600 + m.toLong() * 60 + s.toLong()) * 1000 + f
    }

    fun parse(text: String, hint: String): List<SubCue> {
        val t = text.removePrefix("﻿").replace("\r\n", "\n").replace('\r', '\n')
        return when {
            hint.endsWith(".ass") || hint.endsWith(".ssa") || t.contains("[Events]") -> ass(t)
            else -> srtOrVtt(t)
        }.sortedBy { it.start }
    }

    private fun clean(s: String) = s
        .replace(Regex("<[^>]+>"), "")
        .replace(Regex("\\{[^}]*\\}"), "")
        .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ")
        .trim()

    private fun srtOrVtt(t: String): List<SubCue> {
        val out = ArrayList<SubCue>()
        for (block in t.split(Regex("\n\\s*\n"))) {
            val lines = block.lines().filter { it.isNotBlank() }
            val i = lines.indexOfFirst { it.contains("-->") }
            if (i < 0) continue
            val parts = lines[i].split("-->")
            val a = time.find(parts[0]) ?: continue
            val b = time.find(parts.getOrNull(1) ?: continue) ?: continue
            val start = ms(a.groupValues[1].ifEmpty { null }, a.groupValues[2], a.groupValues[3], a.groupValues[4])
            val end = ms(b.groupValues[1].ifEmpty { null }, b.groupValues[2], b.groupValues[3], b.groupValues[4])
            val body = clean(lines.drop(i + 1).joinToString("\n"))
            if (body.isNotEmpty()) out += SubCue(start, end, body)
        }
        return out
    }

    private fun ass(t: String): List<SubCue> {
        val out = ArrayList<SubCue>()
        var fields = listOf("Layer", "Start", "End", "Style", "Name", "MarginL", "MarginR", "MarginV", "Effect", "Text")
        var inEvents = false
        for (line in t.lines()) {
            val l = line.trim()
            if (l.startsWith("[")) { inEvents = l.equals("[Events]", true); continue }
            if (!inEvents) continue
            if (l.startsWith("Format:")) { fields = l.substringAfter(':').split(',').map { it.trim() }; continue }
            if (!l.startsWith("Dialogue:")) continue
            val values = l.substringAfter(':').split(',', limit = fields.size).map { it.trim() }
            fun v(n: String) = values.getOrNull(fields.indexOf(n)).orEmpty()
            val a = time.find(v("Start")) ?: continue
            val b = time.find(v("End")) ?: continue
            val start = ms(a.groupValues[1].ifEmpty { null }, a.groupValues[2], a.groupValues[3], a.groupValues[4].padEnd(2, '0') + "0")
            val end = ms(b.groupValues[1].ifEmpty { null }, b.groupValues[2], b.groupValues[3], b.groupValues[4].padEnd(2, '0') + "0")
            val body = clean(v("Text").replace("\\N", "\n").replace("\\n", "\n").replace("\\h", " "))
            if (body.isNotEmpty()) out += SubCue(start, end, body)
        }
        return out
    }

    /** O andaki (gecikme uygulanmış) altyazı metni; ikili arama. */
    fun at(cues: List<SubCue>, posMs: Long): String? {
        if (cues.isEmpty()) return null
        var lo = 0
        var hi = cues.size - 1
        var idx = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (cues[mid].start <= posMs) { idx = mid; lo = mid + 1 } else hi = mid - 1
        }
        if (idx < 0) return null
        // Üst üste binen satırlar
        val active = ArrayList<String>()
        var j = idx
        while (j >= 0 && idx - j < 6) {
            val c = cues[j]
            if (posMs in c.start..c.end) active.add(0, c.text)
            j--
        }
        return active.joinToString("\n").ifEmpty { null }
    }
}
