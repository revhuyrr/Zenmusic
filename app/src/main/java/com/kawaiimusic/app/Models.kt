package com.kawaiimusic.app

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import org.json.JSONArray
import org.json.JSONObject

data class Song(
    val id: String,            // YouTube video id
    val title: String,
    val artist: String,
    val thumb: String?,
    val durationMs: Long = 0L,
)

enum class Kind { SONG, ALBUM, PLAYLIST, ARTIST }

/** Anything that can appear in a shelf or result list. */
data class MusicItem(
    val kind: Kind,
    val title: String,
    val subtitle: String,
    val thumb: String?,
    val videoId: String? = null,
    val browseId: String? = null,
    val playlistId: String? = null,
    val artist: String = "",
    val durationMs: Long = 0L,
)

data class Shelf(val title: String, val items: List<MusicItem>)

data class DetailPage(
    val title: String,
    val subtitle: String,
    val thumb: String?,
    val items: List<MusicItem>,
)

fun MusicItem.toSong(): Song? = videoId?.let {
    Song(it, title, artist.ifBlank { subtitle }, thumb, durationMs)
}

fun Song.toItem() = MusicItem(Kind.SONG, title, artist, thumb, videoId = id, artist = artist, durationMs = durationMs)

/** The URI is only a placeholder; PlaybackService resolves it to a real stream when needed. */
fun Song.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(id)
    .setUri("https://music.youtube.com/watch?v=$id")
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setArtworkUri(thumb?.sized(544)?.let { Uri.parse(it) })
            .setDurationMs(durationMs.takeIf { it > 0 })
            .build()
    )
    .build()

fun MediaItem.toSongOrNull(): Song? {
    if (mediaId.isEmpty() || mediaId == MediaItem.DEFAULT_MEDIA_ID) return null
    val m = mediaMetadata
    return Song(
        id = mediaId,
        title = m.title?.toString() ?: "",
        artist = m.artist?.toString() ?: "",
        thumb = m.artworkUri?.toString(),
        durationMs = m.durationMs ?: 0L,
    )
}

/** Rewrites Google image URLs (…=w60-h60…) to the requested square size. */
fun String.sized(px: Int): String {
    val regex = Regex("=w\\d+-h\\d+")
    return if (regex.containsMatchIn(this)) replace(regex, "=w$px-h$px") else this
}

data class Playlist(val name: String, val songs: List<Song>)

class PlaylistStore(context: Context) {
    private val prefs = context.getSharedPreferences("playlists", Context.MODE_PRIVATE)

    fun load(): List<Playlist> = try {
        val arr = JSONArray(prefs.getString("data", "[]") ?: "[]")
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val songs = o.getJSONArray("songs")
            Playlist(
                o.getString("name"),
                (0 until songs.length()).map { j ->
                    val s = songs.getJSONObject(j)
                    Song(
                        s.getString("id"), s.optString("title"), s.optString("artist"),
                        s.optString("thumb").takeIf { it.isNotEmpty() }, s.optLong("dur"),
                    )
                },
            )
        }
    } catch (_: Exception) {
        emptyList()
    }

    fun save(list: List<Playlist>) {
        val arr = JSONArray()
        list.forEach { p ->
            val songs = JSONArray()
            p.songs.forEach { s ->
                songs.put(
                    JSONObject().put("id", s.id).put("title", s.title).put("artist", s.artist)
                        .put("thumb", s.thumb ?: "").put("dur", s.durationMs)
                )
            }
            arr.put(JSONObject().put("name", p.name).put("songs", songs))
        }
        prefs.edit().putString("data", arr.toString()).apply()
    }
}
