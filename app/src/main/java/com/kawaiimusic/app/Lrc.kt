package com.kawaiimusic.app

data class LyricLine(val timeMs: Long, val text: String)

data class Lyrics(val lines: List<LyricLine>, val synced: Boolean)

/** Parses standard .lrc files ([mm:ss.xx] text). Falls back to plain, unsynced lines. */
object LrcParser {
    private val TAG = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")

    fun parse(raw: String): Lyrics {
        val synced = mutableListOf<LyricLine>()
        val plain = mutableListOf<LyricLine>()
        for (line in raw.lines()) {
            val matches = TAG.findAll(line).toList()
            val text = TAG.replace(line, "").trim()
            if (matches.isEmpty()) {
                // skip metadata tags such as [ar:Artist]
                if (text.isNotEmpty() && !text.startsWith("[")) plain += LyricLine(0, text)
                continue
            }
            for (m in matches) {
                val min = m.groupValues[1].toLong()
                val sec = m.groupValues[2].toLong()
                val frac = m.groupValues[3]
                val ms = when (frac.length) {
                    0 -> 0L
                    1 -> frac.toLong() * 100
                    2 -> frac.toLong() * 10
                    else -> frac.take(3).toLong()
                }
                synced += LyricLine(min * 60_000 + sec * 1_000 + ms, text)
            }
        }
        return if (synced.isNotEmpty()) Lyrics(synced.sortedBy { it.timeMs }, true)
        else Lyrics(plain, false)
    }
}
