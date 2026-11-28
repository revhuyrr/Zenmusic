package com.kawaiimusic.app.net

import android.content.Context
import com.kawaiimusic.app.LrcParser
import com.kawaiimusic.app.Lyrics
import com.kawaiimusic.app.Song
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder

/** Time-synced lyrics from LRCLIB (https://lrclib.net), cached on disk per song. */
object LyricsRepository {
    private const val UA = "KawaiMusic/1.1.0 by Zenith (https://github.com/revhuyrr/zenmusic)"

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun cleanTitle(t: String) =
        t.replace(Regex("\\s*[(\\[][^)\\]]*[)\\]]"), "").replace(Regex("\\s+"), " ").trim()

    private fun firstArtist(a: String) = a.split(",", "&", " x ", " feat.", " ft.").first().trim()

    fun fetch(context: Context, song: Song, durationSec: Long): Lyrics? {
        val dir = File(context.filesDir, "lyrics").apply { mkdirs() }
        val cached = File(dir, "${song.id}.lrc")
        if (cached.exists()) return LrcParser.parse(cached.readText()).takeIf { it.lines.isNotEmpty() }
        val missing = File(dir, "${song.id}.none")
        if (missing.exists() && System.currentTimeMillis() - missing.lastModified() < 24 * 3600_000L) return null

        val title = cleanTitle(song.title)
        val artist = firstArtist(song.artist)
        val dur = if (durationSec > 0) durationSec else song.durationMs / 1000

        val hit = byExact(title, artist, dur) ?: bySearch("$title $artist")
        val raw = hit?.let { it.optString("syncedLyrics").ifBlank { it.optString("plainLyrics") } }.orEmpty()
        if (raw.isBlank()) {
            missing.writeText("")
            return null
        }
        cached.writeText(raw)
        return LrcParser.parse(raw).takeIf { it.lines.isNotEmpty() }
    }

    private fun byExact(title: String, artist: String, dur: Long): JSONObject? = try {
        val url = "https://lrclib.net/api/get?track_name=${enc(title)}&artist_name=${enc(artist)}" +
            (if (dur > 0) "&duration=$dur" else "")
        val res = Http.request(url, headers = mapOf("User-Agent" to UA))
        if (res.code == 200) JSONObject(res.body) else null
    } catch (_: Exception) {
        null
    }

    private fun bySearch(query: String): JSONObject? = try {
        val res = Http.request("https://lrclib.net/api/search?q=${enc(query)}", headers = mapOf("User-Agent" to UA))
        if (res.code == 200) {
            val arr = JSONArray(res.body)
            var plain: JSONObject? = null
            var found: JSONObject? = null
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                if (o.optString("syncedLyrics").isNotBlank()) { found = o; break }
                if (plain == null && o.optString("plainLyrics").isNotBlank()) plain = o
            }
            found ?: plain
        } else null
    } catch (_: Exception) {
        null
    }
}
