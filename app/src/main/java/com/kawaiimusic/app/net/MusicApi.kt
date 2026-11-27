package com.kawaiimusic.app.net

import com.kawaiimusic.app.DetailPage
import com.kawaiimusic.app.MusicItem
import com.kawaiimusic.app.Shelf
import com.kawaiimusic.app.Song
import org.json.JSONObject

enum class SearchFilter(val label: String, val params: String) {
    Songs("Songs", "EgWKAQIIAWoMEA4QChADEAQQCRAF"),
    Videos("Videos", "EgWKAQIQAWoMEA4QChADEAQQCRAF"),
    Albums("Albums", "EgWKAQIYAWoMEA4QChADEAQQCRAF"),
    Artists("Artists", "EgWKAQIgAWoMEA4QChADEAQQCRAF"),
}

/** High-level calls built on [InnerTube]. All blocking - call from Dispatchers.IO. */
object MusicApi {
    fun home(): List<Shelf> =
        parseShelves(InnerTube.web("browse", JSONObject().put("browseId", "FEmusic_home")))

    fun search(query: String, filter: SearchFilter): List<MusicItem> =
        parseSearch(InnerTube.web("search", JSONObject().put("query", query).put("params", filter.params)))

    fun suggestions(input: String): List<String> =
        parseSuggestions(InnerTube.web("music/get_search_suggestions", JSONObject().put("input", input)))

    fun page(browseId: String): DetailPage =
        parsePage(InnerTube.web("browse", JSONObject().put("browseId", browseId)))

    private fun next(videoId: String?, playlistId: String?, params: String?): List<Song> {
        val body = JSONObject()
            .put("enablePersistentPlaylistPanel", true)
            .put("isAudioOnly", true)
            .put("tunerSettingValue", "AUTOMIX_SETTING_NORMAL")
        videoId?.let { body.put("videoId", it) }
        playlistId?.let { body.put("playlistId", it) }
        params?.let { body.put("params", it) }
        return parseQueue(InnerTube.web("next", body))
    }

    /** Auto-generated "radio" queue that continues from a song. */
    fun radio(videoId: String): List<Song> = next(videoId, "RDAMVM$videoId", "wAEB")

    fun playlistSongs(playlistId: String): List<Song> = next(null, playlistId, null)
}
