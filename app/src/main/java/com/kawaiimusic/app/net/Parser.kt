package com.kawaiimusic.app.net

import com.kawaiimusic.app.DetailPage
import com.kawaiimusic.app.Kind
import com.kawaiimusic.app.MusicItem
import com.kawaiimusic.app.Shelf
import com.kawaiimusic.app.Song
import org.json.JSONArray
import org.json.JSONObject

/*
 * YouTube Music responses are deeply nested and change shape often, so instead of following
 * fixed paths we look for the renderer objects by name wherever they sit in the tree.
 */

internal fun collect(node: Any?, key: String, out: MutableList<JSONObject>) {
    when (node) {
        is JSONObject -> {
            val keys = node.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val v = node.opt(k)
                if (k == key && v is JSONObject) out += v else collect(v, key, out)
            }
        }
        is JSONArray -> for (i in 0 until node.length()) collect(node.opt(i), key, out)
    }
}

internal fun findFirst(node: Any?, key: String): Any? {
    when (node) {
        is JSONObject -> {
            if (node.has(key)) return node.opt(key)
            val keys = node.keys()
            while (keys.hasNext()) {
                val found = findFirst(node.opt(keys.next()), key)
                if (found != null) return found
            }
        }
        is JSONArray -> for (i in 0 until node.length()) {
            val found = findFirst(node.opt(i), key)
            if (found != null) return found
        }
    }
    return null
}

private fun JSONObject.path(vararg keys: String): JSONObject? {
    var cur: JSONObject = this
    for (k in keys) cur = cur.optJSONObject(k) ?: return null
    return cur
}

private fun runsOf(o: JSONObject?): List<JSONObject> {
    val a = o?.optJSONArray("runs") ?: return emptyList()
    return (0 until a.length()).mapNotNull { a.optJSONObject(it) }
}

private fun textOf(o: JSONObject?): String = runsOf(o).joinToString("") { it.optString("text") }

private fun thumbOf(node: Any?): String? {
    val arr = findFirst(node, "thumbnails") as? JSONArray ?: return null
    if (arr.length() == 0) return null
    return arr.optJSONObject(arr.length() - 1)?.optString("url")?.takeIf { it.isNotEmpty() }
}

private val DURATION = Regex("^\\d{1,2}:\\d{2}(:\\d{2})?$")

internal fun parseDuration(text: String): Long {
    val t = text.trim()
    if (!DURATION.matches(t)) return 0L
    val parts = t.split(":").map { it.toLongOrNull() ?: 0L }
    return parts.fold(0L) { acc, p -> acc * 60 + p } * 1000L
}

private val TYPE_LABELS = setOf("Song", "Video", "Album", "Single", "EP", "Playlist", "Artist", "Podcast", "Episode")

private fun isSeparator(s: String) = s.trim().let { it.isEmpty() || it == "•" || it == "·" || it == "&" }

private fun isArtistRun(run: JSONObject): Boolean {
    val type = run.path("navigationEndpoint", "browseEndpoint", "browseEndpointContextSupportedConfigs", "browseEndpointContextMusicConfig")
        ?.optString("pageType") ?: return false
    return type.contains("ARTIST") || type.contains("USER_CHANNEL")
}

private fun kindForBrowseId(id: String): Kind = when {
    id.startsWith("MPRE") || id.startsWith("OLAK") -> Kind.ALBUM
    id.startsWith("UC") -> Kind.ARTIST
    else -> Kind.PLAYLIST
}

internal fun parseResponsive(r: JSONObject): MusicItem? {
    val cols = r.optJSONArray("flexColumns") ?: return null
    fun col(i: Int): List<JSONObject> =
        runsOf(cols.optJSONObject(i)?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")?.optJSONObject("text"))

    val title = col(0).joinToString("") { it.optString("text") }
    if (title.isBlank()) return null

    val videoId = r.optJSONObject("playlistItemData")?.optString("videoId")?.takeIf { it.isNotEmpty() }
        ?: (findFirst(r.optJSONObject("overlay"), "videoId") as? String)?.takeIf { it.isNotEmpty() }

    val sub = col(1).filter { !isSeparator(it.optString("text")) }
    val fixed = runsOf(
        r.optJSONArray("fixedColumns")?.optJSONObject(0)
            ?.optJSONObject("musicResponsiveListItemFixedColumnRenderer")?.optJSONObject("text")
    )

    val durationText = (sub + fixed).map { it.optString("text").trim() }.lastOrNull { DURATION.matches(it) } ?: ""

    val artistRuns = sub.filter(::isArtistRun)
    val artist = if (artistRuns.isNotEmpty()) {
        artistRuns.joinToString(", ") { it.optString("text") }
    } else {
        sub.map { it.optString("text").trim() }
            .firstOrNull { it !in TYPE_LABELS && !DURATION.matches(it) && !it.endsWith("plays") && !it.endsWith("views") }
            ?: ""
    }
    val subtitle = sub.joinToString(" • ") { it.optString("text").trim() }
    val thumb = thumbOf(r.optJSONObject("thumbnail"))

    if (videoId != null) {
        return MusicItem(Kind.SONG, title, artist, thumb, videoId = videoId, artist = artist, durationMs = parseDuration(durationText))
    }
    val browseId = r.path("navigationEndpoint")?.optJSONObject("browseEndpoint")?.optString("browseId")
        ?.takeIf { it.isNotEmpty() } ?: return null
    return MusicItem(kindForBrowseId(browseId), title, subtitle, thumb, browseId = browseId)
}

internal fun parseTwoRow(r: JSONObject): MusicItem? {
    val title = textOf(r.optJSONObject("title"))
    if (title.isBlank()) return null
    val subRuns = runsOf(r.optJSONObject("subtitle"))
    val parts = subRuns.map { it.optString("text").trim() }.filter { !isSeparator(it) }
    val subtitle = parts.joinToString(" • ")
    val thumb = thumbOf(r.optJSONObject("thumbnailRenderer"))
    val nav = r.optJSONObject("navigationEndpoint")

    val videoId = nav?.optJSONObject("watchEndpoint")?.optString("videoId")?.takeIf { it.isNotEmpty() }
    if (videoId != null) {
        val artistRuns = subRuns.filter(::isArtistRun)
        val artist = if (artistRuns.isNotEmpty()) artistRuns.joinToString(", ") { it.optString("text") }
        else parts.firstOrNull { it !in TYPE_LABELS && !it.endsWith("plays") && !it.endsWith("views") } ?: ""
        return MusicItem(Kind.SONG, title, subtitle, thumb, videoId = videoId, artist = artist)
    }
    val browseId = nav?.optJSONObject("browseEndpoint")?.optString("browseId")?.takeIf { it.isNotEmpty() }
    if (browseId != null) return MusicItem(kindForBrowseId(browseId), title, subtitle, thumb, browseId = browseId)

    val playlistId = nav?.optJSONObject("watchPlaylistEndpoint")?.optString("playlistId")?.takeIf { it.isNotEmpty() }
    if (playlistId != null) return MusicItem(Kind.PLAYLIST, title, subtitle, thumb, playlistId = playlistId)
    return null
}

private fun parseItems(arr: JSONArray?): List<MusicItem> {
    if (arr == null) return emptyList()
    return (0 until arr.length()).mapNotNull { i ->
        val o = arr.optJSONObject(i) ?: return@mapNotNull null
        o.optJSONObject("musicTwoRowItemRenderer")?.let(::parseTwoRow)
            ?: o.optJSONObject("musicResponsiveListItemRenderer")?.let(::parseResponsive)
    }
}

fun parseShelves(root: JSONObject): List<Shelf> {
    val shelves = mutableListOf<JSONObject>()
    collect(root, "musicCarouselShelfRenderer", shelves)
    return shelves.mapNotNull { s ->
        val title = textOf(s.path("header", "musicCarouselShelfBasicHeaderRenderer", "title"))
        val items = parseItems(s.optJSONArray("contents"))
        if (items.isEmpty() || title.isBlank()) null else Shelf(title, items)
    }
}

fun parseSearch(root: JSONObject): List<MusicItem> {
    val rows = mutableListOf<JSONObject>()
    collect(root, "musicResponsiveListItemRenderer", rows)
    return rows.mapNotNull(::parseResponsive).distinctBy { it.videoId ?: it.browseId }
}

fun parseSuggestions(root: JSONObject): List<String> {
    val rows = mutableListOf<JSONObject>()
    collect(root, "searchSuggestionRenderer", rows)
    return rows.map { textOf(it.optJSONObject("suggestion")) }.filter { it.isNotBlank() }.distinct()
}

fun parseQueue(root: JSONObject): List<Song> {
    val rows = mutableListOf<JSONObject>()
    collect(root, "playlistPanelVideoRenderer", rows)
    return rows.mapNotNull { r ->
        val id = r.optString("videoId").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        val title = textOf(r.optJSONObject("title"))
        val by = runsOf(r.optJSONObject("longBylineText")).filter { !isSeparator(it.optString("text")) }
        val artistRuns = by.filter(::isArtistRun)
        val artist = if (artistRuns.isNotEmpty()) artistRuns.joinToString(", ") { it.optString("text") }
        else by.firstOrNull()?.optString("text") ?: ""
        Song(id, title, artist, thumbOf(r.optJSONObject("thumbnail")), parseDuration(textOf(r.optJSONObject("lengthText"))))
    }.distinctBy { it.id }
}

fun parsePage(root: JSONObject): DetailPage {
    var header: JSONObject? = null
    for (key in listOf("musicResponsiveHeaderRenderer", "musicDetailHeaderRenderer", "musicImmersiveHeaderRenderer", "musicVisualHeaderRenderer")) {
        val found = mutableListOf<JSONObject>()
        collect(root, key, found)
        if (found.isNotEmpty()) { header = found[0]; break }
    }
    val title = textOf(header?.optJSONObject("title"))
    val strap = runsOf(header?.optJSONObject("straplineTextOne")).firstOrNull()?.optString("text")
    val subParts = runsOf(header?.optJSONObject("subtitle")).map { it.optString("text").trim() }.filter { !isSeparator(it) }
    val pageArtist = strap
        ?: subParts.firstOrNull { it !in TYPE_LABELS && !Regex("^\\d{4}$").matches(it) }
        ?: ""
    val pageThumb = thumbOf(header)

    val songRows = mutableListOf<JSONObject>()
    collect(root, "musicResponsiveListItemRenderer", songRows)
    val songs = songRows.mapNotNull(::parseResponsive)
        .filter { it.kind == Kind.SONG }
        .map { it.copy(artist = it.artist.ifBlank { pageArtist }, thumb = it.thumb ?: pageThumb, subtitle = it.subtitle) }

    val shelves = parseShelves(root).flatMap { it.items }
    val ids = songs.mapNotNull { it.videoId }.toSet()
    val others = shelves.filter { it.videoId == null || it.videoId !in ids }

    return DetailPage(
        title = title,
        subtitle = if (strap != null) strap else subParts.joinToString(" • "),
        thumb = pageThumb,
        items = songs + others,
    )
}
