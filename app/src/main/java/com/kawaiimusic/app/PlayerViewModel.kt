package com.kawaiimusic.app

import android.app.Application
import android.content.ComponentName
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.kawaiimusic.app.net.LyricsRepository
import com.kawaiimusic.app.net.MusicApi
import com.kawaiimusic.app.net.SearchFilter
import com.kawaiimusic.app.net.StreamRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlayerViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx = app.applicationContext
    private val playlistStore = PlaylistStore(ctx)

    // ---- playback state -------------------------------------------------------------------
    var current by mutableStateOf<Song?>(null); private set
    var isPlaying by mutableStateOf(false); private set
    var isBuffering by mutableStateOf(false); private set
    var positionMs by mutableLongStateOf(0L); private set
    var durationMs by mutableLongStateOf(0L); private set
    var shuffle by mutableStateOf(false); private set
    var repeatMode by mutableIntStateOf(Player.REPEAT_MODE_OFF); private set
    var accent by mutableStateOf(DefaultAccent); private set
    var lyrics by mutableStateOf<Lyrics?>(null); private set
    var lyricsLoading by mutableStateOf(false); private set
    var playbackError by mutableStateOf<String?>(null); private set

    // ---- browse state ---------------------------------------------------------------------
    var home by mutableStateOf<List<Shelf>>(emptyList()); private set
    var homeLoading by mutableStateOf(false); private set
    var homeError by mutableStateOf<String?>(null); private set

    var query by mutableStateOf(""); private set
    var filter by mutableStateOf(SearchFilter.Songs); private set
    var results by mutableStateOf<List<MusicItem>>(emptyList()); private set
    var searching by mutableStateOf(false); private set
    var searchError by mutableStateOf<String?>(null); private set
    var searched by mutableStateOf(false); private set
    var suggestions by mutableStateOf<List<String>>(emptyList()); private set

    var detailOpen by mutableStateOf(false); private set
    var detailSeed by mutableStateOf<MusicItem?>(null); private set
    var detail by mutableStateOf<DetailPage?>(null); private set
    var detailLoading by mutableStateOf(false); private set
    var detailError by mutableStateOf<String?>(null); private set

    var playlists by mutableStateOf<List<Playlist>>(emptyList()); private set

    private var controller: MediaController? = null
    private var suggestJob: Job? = null
    private var searchJob: Job? = null
    private var detailJob: Job? = null
    private var retriedId: String? = null

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            this@PlayerViewModel.isPlaying = isPlaying
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            updateCurrent(mediaItem)
            controller?.let { c ->
                durationMs = c.duration.coerceAtLeast(0L)
                // Keep related songs coming: when the queue is nearly used up, fetch more.
                val remaining = c.mediaItemCount - c.currentMediaItemIndex - 1
                if (remaining <= 2) current?.let { appendRelated(it) }
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            isBuffering = playbackState == Player.STATE_BUFFERING
            if (playbackState == Player.STATE_READY) playbackError = null
            controller?.let { durationMs = it.duration.coerceAtLeast(0L) }
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            shuffle = shuffleModeEnabled
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            this@PlayerViewModel.repeatMode = repeatMode
        }

        override fun onPlayerError(error: PlaybackException) {
            val c = controller ?: return
            val id = current?.id
            // Stream links expire; try once more with a fresh one before skipping the song.
            if (id != null && retriedId != id) {
                retriedId = id
                StreamRepository.invalidate(id)
                c.prepare()
                c.play()
                return
            }
            playbackError = StreamRepository.lastError?.let { "Can't play: $it" } ?: "Couldn't play this song"
            if (c.hasNextMediaItem()) {
                c.seekToNextMediaItem()
                c.prepare()
                c.play()
            }
        }
    }

    init {
        playlists = playlistStore.load()
        connect()
        loadHome()
        viewModelScope.launch {
            while (true) {
                controller?.let {
                    positionMs = it.currentPosition.coerceAtLeast(0L)
                    val d = it.duration
                    if (d > 0) durationMs = d
                }
                delay(200)
            }
        }
    }

    private fun connect() {
        val token = SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))
        val future = MediaController.Builder(ctx, token).buildAsync()
        future.addListener({
            try {
                val c = future.get()
                controller = c
                c.addListener(listener)
                isPlaying = c.isPlaying
                shuffle = c.shuffleModeEnabled
                repeatMode = c.repeatMode
                updateCurrent(c.currentMediaItem)
            } catch (_: Exception) {
            }
        }, ContextCompat.getMainExecutor(ctx))
    }

    private fun updateCurrent(item: MediaItem?) {
        val song = item?.toSongOrNull()
        if (song?.id != current?.id) retriedId = null
        current = song
        lyrics = null
        if (song == null) {
            accent = DefaultAccent
            lyricsLoading = false
            return
        }
        viewModelScope.launch {
            val color = withContext(Dispatchers.IO) { averageColor(song.thumb) }
            if (current?.id == song.id && color != null) accent = color
        }
        lyricsLoading = true
        viewModelScope.launch {
            val found = withContext(Dispatchers.IO) {
                try {
                    LyricsRepository.fetch(ctx, song, (controller?.duration ?: 0L).coerceAtLeast(0L) / 1000)
                } catch (_: Exception) {
                    null
                }
            }
            if (current?.id == song.id) {
                lyrics = found
                lyricsLoading = false
            }
        }
    }

    private suspend fun averageColor(url: String?): Color? {
        if (url == null) return null
        return try {
            val request = ImageRequest.Builder(ctx).data(url.sized(64)).size(32).allowHardware(false).build()
            val drawable = (ctx.imageLoader.execute(request) as? SuccessResult)?.drawable ?: return null
            val px = android.graphics.Bitmap.createScaledBitmap(drawable.toBitmap(), 1, 1, true).getPixel(0, 0)
            Color(px)
        } catch (_: Exception) {
            null
        }
    }

    // ---- home / search / detail -----------------------------------------------------------

    fun loadHome() {
        if (homeLoading) return
        homeLoading = true
        homeError = null
        viewModelScope.launch {
            try {
                val shelves = withContext(Dispatchers.IO) { MusicApi.home() }
                home = shelves
                if (shelves.isEmpty()) homeError = "Nothing to show right now"
            } catch (e: Exception) {
                homeError = "Couldn't load music. Check your connection."
            } finally {
                homeLoading = false
            }
        }
    }

    fun onQueryChange(text: String) {
        query = text
        suggestJob?.cancel()
        if (text.isBlank()) {
            suggestions = emptyList()
            return
        }
        suggestJob = viewModelScope.launch {
            delay(250)
            suggestions = try {
                withContext(Dispatchers.IO) { MusicApi.suggestions(text) }.take(8)
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    fun selectFilter(f: SearchFilter) {
        filter = f
        if (query.isNotBlank()) search(query)
    }

    fun search(text: String = query) {
        val q = text.trim()
        if (q.isEmpty()) return
        query = q
        suggestJob?.cancel()
        suggestions = emptyList()
        searchJob?.cancel()
        searching = true
        searchError = null
        searched = true
        searchJob = viewModelScope.launch {
            try {
                results = withContext(Dispatchers.IO) { MusicApi.search(q, filter) }
            } catch (e: Exception) {
                results = emptyList()
                searchError = "Search failed. Check your connection."
            } finally {
                searching = false
            }
        }
    }

    /** Opens an album / artist / playlist page. */
    fun openCollection(item: MusicItem) {
        detailJob?.cancel()
        detailOpen = true
        detailSeed = item
        detail = null
        detailError = null
        detailLoading = true
        detailJob = viewModelScope.launch {
            try {
                detail = withContext(Dispatchers.IO) {
                    when {
                        item.browseId != null -> MusicApi.page(item.browseId)
                        item.playlistId != null -> {
                            val songs = MusicApi.playlistSongs(item.playlistId)
                            DetailPage(item.title, item.subtitle, item.thumb, songs.map { it.toItem() })
                        }
                        else -> throw IllegalStateException("Nothing to open")
                    }
                }
            } catch (e: Exception) {
                detailError = "Couldn't open this page"
            } finally {
                detailLoading = false
            }
        }
    }

    fun closeDetail() {
        detailJob?.cancel()
        detailOpen = false
        detail = null
        detailSeed = null
        detailLoading = false
    }

    /**
     * Plays/opens whatever the user tapped.
     *
     * [asQueue] = true (album / artist / playlist pages) plays the page's songs in order.
     * Otherwise (search results, Home shelves) only the tapped song is queued, followed by
     * songs related to it - not the rest of the result list.
     */
    fun onItemClick(item: MusicItem, siblings: List<MusicItem>, asQueue: Boolean = false) {
        if (item.kind != Kind.SONG) {
            openCollection(item)
            return
        }
        if (asQueue) {
            val songs = siblings.filter { it.kind == Kind.SONG }.mapNotNull { it.toSong() }
            val index = songs.indexOfFirst { it.id == item.videoId }
            if (index >= 0) playSongs(songs, index)
        } else {
            item.toSong()?.let { playWithRelated(it) }
        }
    }

    fun playWithRelated(song: Song) = playSongs(listOf(song), 0)

    // ---- playback -------------------------------------------------------------------------

    fun playSongs(list: List<Song>, index: Int) {
        val c = controller ?: return
        if (list.isEmpty()) return
        val start = index.coerceIn(0, list.lastIndex)
        c.setMediaItems(list.map { it.toMediaItem() }, start, 0L)
        c.prepare()
        c.play()
        playbackError = null
        relatedJobFor = null // lets the transition listener fetch related songs for this queue
    }

    private var relatedJobFor: String? = null

    /** Fetches songs related to [song] (YouTube Music radio) and appends the ones not queued yet. */
    private fun appendRelated(song: Song) {
        if (relatedJobFor == song.id) return
        relatedJobFor = song.id
        viewModelScope.launch {
            val more = try {
                withContext(Dispatchers.IO) { MusicApi.radio(song.id) }
            } catch (_: Exception) {
                emptyList()
            }
            val c = controller ?: return@launch
            val queued = (0 until c.mediaItemCount).map { c.getMediaItemAt(it).mediaId }.toSet()
            val fresh = more.filter { it.id !in queued }
            if (fresh.isNotEmpty()) c.addMediaItems(fresh.map { it.toMediaItem() })
        }
    }

    fun togglePlay() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun next() { controller?.seekToNextMediaItem() }

    fun previous() {
        val c = controller ?: return
        if (c.currentPosition > 3_000) c.seekTo(0) else c.seekToPreviousMediaItem()
    }

    fun seekTo(ms: Long) {
        controller?.seekTo(ms)
        positionMs = ms
    }

    fun toggleShuffle() { controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled } }

    fun cycleRepeat() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    // ---- playlists ------------------------------------------------------------------------

    fun createPlaylist(name: String, first: Song? = null) {
        val clean = name.trim()
        if (clean.isEmpty() || playlists.any { it.name == clean }) return
        updatePlaylists(playlists + Playlist(clean, listOfNotNull(first)))
    }

    fun addToPlaylist(name: String, song: Song) {
        updatePlaylists(playlists.map {
            if (it.name == name && it.songs.none { s -> s.id == song.id }) it.copy(songs = it.songs + song) else it
        })
    }

    fun removeFromPlaylist(name: String, songId: String) {
        updatePlaylists(playlists.map {
            if (it.name == name) it.copy(songs = it.songs.filterNot { s -> s.id == songId }) else it
        })
    }

    fun deletePlaylist(name: String) {
        updatePlaylists(playlists.filterNot { it.name == name })
    }

    private fun updatePlaylists(list: List<Playlist>) {
        playlists = list
        playlistStore.save(list)
    }

    override fun onCleared() {
        controller?.removeListener(listener)
        controller?.release()
        controller = null
        super.onCleared()
    }

    companion object {
        val DefaultAccent = Color(0xFF4A2638)
    }
}
