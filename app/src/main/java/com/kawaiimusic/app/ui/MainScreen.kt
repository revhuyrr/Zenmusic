package com.kawaiimusic.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kawaiimusic.app.Kind
import com.kawaiimusic.app.MusicItem
import com.kawaiimusic.app.PlayerViewModel
import com.kawaiimusic.app.Playlist
import com.kawaiimusic.app.Song
import com.kawaiimusic.app.net.SearchFilter
import com.kawaiimusic.app.toItem
import com.kawaiimusic.app.toSong
import com.kyant.backdrop.backdrops.LayerBackdrop
import java.util.Calendar

private enum class Tab(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Rounded.Home),
    Search("Search", Icons.Rounded.Search),
    Playlists("Playlists", Icons.Rounded.PlaylistPlay),
}

private const val APP_VERSION = "1.1.0"
private const val OWNER = "Zenith"

@Composable
fun MainScreen(vm: PlayerViewModel) {
    var tab by remember { mutableStateOf(Tab.Home) }
    var showPlayer by remember { mutableStateOf(false) }
    var openPlaylist by remember { mutableStateOf<String?>(null) }
    var pickerFor by remember { mutableStateOf<Song?>(null) }
    var showAbout by remember { mutableStateOf(false) }

    val backdrop = rememberGlassBackdrop()
    val density = LocalDensity.current
    var bottomPx by remember { mutableIntStateOf(0) }
    var topPx by remember { mutableIntStateOf(0) }
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    val searchChrome = tab == Tab.Search && !vm.detailOpen
    val topPad: Dp = when {
        vm.detailOpen -> statusTop + 64.dp
        searchChrome -> with(density) { topPx.toDp() } + 6.dp
        else -> statusTop + 8.dp
    }
    val bottomPad: Dp = with(density) { bottomPx.toDp() } + 12.dp

    BackHandler(enabled = showPlayer) { showPlayer = false }
    BackHandler(enabled = !showPlayer && vm.detailOpen) { vm.closeDetail() }
    BackHandler(enabled = !showPlayer && !vm.detailOpen && openPlaylist != null) { openPlaylist = null }

    val playAndShow: (List<Song>, Int) -> Unit = { list, index -> vm.playSongs(list, index); showPlayer = true }
    // Search results / Home shelves: play the tapped song, then songs related to it.
    val onItem: (MusicItem, List<MusicItem>) -> Unit = { item, siblings ->
        vm.onItemClick(item, siblings, asQueue = false)
        if (item.kind == Kind.SONG) showPlayer = true
    }
    // Album / artist / playlist pages: play the page in order.
    val onPageItem: (MusicItem, List<MusicItem>) -> Unit = { item, siblings ->
        vm.onItemClick(item, siblings, asQueue = true)
        if (item.kind == Kind.SONG) showPlayer = true
    }

    Box(Modifier.fillMaxSize()) {
        // ---- glass source: background + scrolling content -----------------------------------
        Box(Modifier.fillMaxSize().glassSource(backdrop)) {
            AmbientBackground(vm.current?.thumb, vm.accent)
            if (vm.detailOpen) {
                DetailScreen(vm, onPageItem, { pickerFor = it }, topPad, bottomPad)
            } else when (tab) {
                Tab.Home -> HomeTab(vm, onItem, topPad, bottomPad) { showAbout = true }
                Tab.Search -> SearchResults(vm, onItem, { pickerFor = it }, topPad, bottomPad)
                Tab.Playlists -> PlaylistsTab(vm, openPlaylist, { openPlaylist = it }, playAndShow, topPad, bottomPad)
            }
        }

        // ---- glass chrome (siblings of the source) ------------------------------------------
        if (vm.detailOpen) {
            Box(Modifier.statusBarsPadding().padding(start = 12.dp, top = 8.dp)) {
                GlassIconButton(backdrop, Icons.Rounded.ArrowBack, "Back", vm::closeDetail, size = 46.dp)
            }
        } else if (searchChrome) {
            SearchChrome(vm, backdrop, Modifier.onSizeChanged { topPx = it.height })
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .onSizeChanged { bottomPx = it.height }
                .navigationBarsPadding()
                .padding(horizontal = 12.dp)
                .padding(bottom = 8.dp)
        ) {
            if (vm.current != null) {
                MiniPlayer(vm, backdrop, onOpen = { showPlayer = true })
                Spacer(Modifier.height(8.dp))
            }
            TabBar(backdrop, tab) {
                tab = it
                openPlaylist = null
                vm.closeDetail()
            }
        }

        AnimatedVisibility(
            visible = showPlayer && vm.current != null,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
        ) {
            NowPlayingScreen(
                vm = vm,
                onClose = { showPlayer = false },
                onAddToPlaylist = { pickerFor = vm.current },
            )
        }
    }

    pickerFor?.let { song ->
        PlaylistPickerDialog(
            song = song,
            playlists = vm.playlists,
            onPick = { vm.addToPlaylist(it.name, song); pickerFor = null },
            onCreate = { vm.createPlaylist(it, song); pickerFor = null },
            onDismiss = { pickerFor = null },
        )
    }

    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            title = { Text("Kawai Music") },
            text = {
                Column {
                    Text("Made by $OWNER", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text("Version $APP_VERSION")
                    Spacer(Modifier.height(10.dp))
                    Text("Music and playlists from YouTube Music. Synced lyrics from LRCLIB.", fontSize = 13.sp)
                }
            },
            confirmButton = { TextButton(onClick = { showAbout = false }) { Text("OK") } },
        )
    }
}

// ---- bottom chrome ------------------------------------------------------------------------

@Composable
private fun TabBar(backdrop: LayerBackdrop, selectedTab: Tab, onSelect: (Tab) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .liquidGlass(backdrop, RoundedCornerShape(32.dp))
            .padding(6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        Tab.entries.forEach { t ->
            val selected = t == selectedTab
            Row(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(26.dp))
                    .background(if (selected) Color.White.copy(alpha = 0.22f) else Color.Transparent)
                    .clickable { onSelect(t) }
                    .padding(vertical = 11.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(t.icon, t.label, tint = if (selected) Color.White else Color.White.copy(alpha = 0.7f))
                if (selected) {
                    Spacer(Modifier.width(6.dp))
                    Text(t.label, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun MiniPlayer(vm: PlayerViewModel, backdrop: LayerBackdrop, onOpen: () -> Unit) {
    val song = vm.current ?: return
    Column(
        Modifier
            .fillMaxWidth()
            .liquidGlass(backdrop, RoundedCornerShape(28.dp), scrim = 0.2f)
            .clickable(onClick = onOpen)
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            ArtworkUrlBox(song.thumb, Modifier.size(48.dp), corner = 12.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(song.title, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    vm.playbackError ?: song.artist,
                    color = if (vm.playbackError != null) Color(0xFFFFB4B4) else Color.White.copy(alpha = 0.65f),
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (vm.isBuffering) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
            } else {
                IconAction(if (vm.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Play/Pause", vm::togglePlay)
            }
            IconAction(Icons.Rounded.SkipNext, "Next", vm::next)
        }
        // thin progress line
        val fraction = if (vm.durationMs > 0) (vm.positionMs.toFloat() / vm.durationMs).coerceIn(0f, 1f) else 0f
        Box(
            Modifier
                .padding(horizontal = 18.dp)
                .padding(bottom = 6.dp)
                .fillMaxWidth()
                .height(3.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.2f))
        ) {
            Box(Modifier.fillMaxWidth(fraction).height(3.dp).background(Color.White.copy(alpha = 0.9f)))
        }
    }
}

// ---- Home ---------------------------------------------------------------------------------

private fun greeting(): String {
    val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when {
        h < 5 -> "Good night"
        h < 12 -> "Good morning"
        h < 17 -> "Good afternoon"
        else -> "Good evening"
    }
}

@Composable
private fun HomeTab(
    vm: PlayerViewModel,
    onItem: (MusicItem, List<MusicItem>) -> Unit,
    topPad: Dp,
    bottomPad: Dp,
    onAbout: () -> Unit,
) {
    when {
        vm.home.isNotEmpty() -> LazyColumn(contentPadding = PaddingValues(top = topPad, bottom = bottomPad)) {
            item {
                Row(Modifier.padding(start = 20.dp, end = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(greeting(), color = Color.White.copy(alpha = 0.65f), fontSize = 15.sp)
                        Text("Home", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    IconAction(Icons.Rounded.Info, "About", onAbout)
                }
            }
            items(vm.home) { shelf ->
                Text(
                    shelf.title,
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 20.dp, top = 18.dp, bottom = 8.dp),
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(shelf.items) { item -> ItemCard(item) { onItem(item, shelf.items) } }
                }
            }
        }
        vm.homeLoading -> LoadingBox(topPad, bottomPad)
        else -> Box(Modifier.padding(top = topPad, bottom = bottomPad)) {
            CenterMessage(vm.homeError ?: "Nothing to show right now", "Try again") { vm.loadHome() }
        }
    }
}

@Composable
private fun LoadingBox(topPad: Dp = 0.dp, bottomPad: Dp = 0.dp) {
    Box(Modifier.fillMaxSize().padding(top = topPad, bottom = bottomPad), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = KawaiPinkSoft)
    }
}

// ---- Search -------------------------------------------------------------------------------

/** Search field + filter chips: real glass, drawn over the results as a sibling of the source. */
@Composable
private fun SearchChrome(vm: PlayerViewModel, backdrop: LayerBackdrop, modifier: Modifier) {
    Column(
        modifier
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(top = 12.dp, bottom = 6.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .liquidGlass(backdrop, RoundedCornerShape(50))
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Search, null, tint = Color.White.copy(alpha = 0.7f))
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                if (vm.query.isEmpty()) {
                    Text("Songs, albums, artists", color = Color.White.copy(alpha = 0.5f), fontSize = 16.sp)
                }
                BasicTextField(
                    value = vm.query,
                    onValueChange = vm::onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                    cursorBrush = SolidColor(KawaiPinkSoft),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { vm.search() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (vm.query.isNotEmpty()) {
                Icon(
                    Icons.Rounded.Close, "Clear", tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp).clickable { vm.onQueryChange("") },
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SearchFilter.entries.forEach { f ->
                val selected = f == vm.filter
                Text(
                    f.label,
                    color = if (selected) Color.White else Color.White.copy(alpha = 0.75f),
                    fontSize = 14.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier
                        .liquidGlass(backdrop, RoundedCornerShape(50), scrim = if (selected) 0.0f else 0.2f)
                        .background(if (selected) Color.White.copy(alpha = 0.2f) else Color.Transparent, RoundedCornerShape(50))
                        .clickable { vm.selectFilter(f) }
                        .padding(horizontal = 15.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun SearchResults(
    vm: PlayerViewModel,
    onItem: (MusicItem, List<MusicItem>) -> Unit,
    pickerOpen: (Song) -> Unit,
    topPad: Dp,
    bottomPad: Dp,
) {
    val padding = PaddingValues(start = 12.dp, end = 12.dp, top = topPad, bottom = bottomPad)
    when {
        vm.suggestions.isNotEmpty() && vm.query.isNotBlank() -> LazyColumn(contentPadding = padding) {
            items(vm.suggestions) { s ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { vm.search(s) }
                        .padding(horizontal = 8.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Search, null, tint = Color.White.copy(alpha = 0.5f))
                    Spacer(Modifier.width(12.dp))
                    Text(s, color = Color.White, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        vm.searching -> LoadingBox(topPad, bottomPad)
        vm.searchError != null -> Box(Modifier.padding(top = topPad, bottom = bottomPad)) {
            CenterMessage(vm.searchError!!, "Retry") { vm.search() }
        }
        vm.results.isNotEmpty() -> LazyColumn(contentPadding = padding) {
            items(vm.results) { item ->
                ItemRow(
                    item = item,
                    highlighted = item.kind == Kind.SONG && item.videoId == vm.current?.id,
                    onClick = { onItem(item, vm.results) },
                ) {
                    if (item.kind == Kind.SONG) {
                        val song = item.toSong()
                        if (song != null) IconAction(Icons.Rounded.MoreVert, "Add to playlist") { pickerOpen(song) }
                    }
                }
            }
        }
        else -> Box(Modifier.padding(top = topPad, bottom = bottomPad)) {
            CenterMessage(if (vm.searched) "No results" else "Search YouTube Music")
        }
    }
}

// ---- Detail (album / artist / playlist) -----------------------------------------------------

@Composable
private fun DetailScreen(
    vm: PlayerViewModel,
    onItem: (MusicItem, List<MusicItem>) -> Unit,
    pickerOpen: (Song) -> Unit,
    topPad: Dp,
    bottomPad: Dp,
) {
    val seed = vm.detailSeed
    val page = vm.detail
    val title = page?.title?.takeIf { it.isNotBlank() } ?: seed?.title ?: ""
    val thumb = page?.thumb ?: seed?.thumb

    when {
        vm.detailLoading -> LoadingBox(topPad, bottomPad)
        vm.detailError != null -> Box(Modifier.padding(top = topPad, bottom = bottomPad)) {
            CenterMessage(vm.detailError!!, "Back") { vm.closeDetail() }
        }
        page != null -> LazyColumn(contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = topPad, bottom = bottomPad)) {
            item {
                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    ArtworkUrlBox(thumb, Modifier.size(120.dp), corner = 16.dp, round = seed?.kind == Kind.ARTIST)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        if (page.subtitle.isNotBlank()) {
                            Text(page.subtitle, color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        val songs = page.items.filter { it.kind == Kind.SONG }
                        if (songs.isNotEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            Row(
                                Modifier
                                    .frost(RoundedCornerShape(50), 0.26f)
                                    .clickable { onItem(songs.first(), page.items) }
                                    .padding(horizontal = 18.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Rounded.PlayArrow, "Play", tint = Color.White)
                                Spacer(Modifier.width(6.dp))
                                Text("Play", color = Color.White, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
            if (page.items.isEmpty()) item { Text("Nothing here", color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(16.dp)) }
            items(page.items) { item ->
                ItemRow(
                    item = item,
                    highlighted = item.kind == Kind.SONG && item.videoId == vm.current?.id,
                    onClick = { onItem(item, page.items) },
                ) {
                    if (item.kind == Kind.SONG) {
                        val song = item.toSong()
                        if (song != null) IconAction(Icons.Rounded.MoreVert, "Add to playlist") { pickerOpen(song) }
                    }
                }
            }
        }
    }
}

// ---- Playlists ----------------------------------------------------------------------------

@Composable
private fun PlaylistsTab(
    vm: PlayerViewModel,
    openName: String?,
    onOpen: (String?) -> Unit,
    onPlay: (List<Song>, Int) -> Unit,
    topPad: Dp,
    bottomPad: Dp,
) {
    var creating by remember { mutableStateOf(false) }
    val open = vm.playlists.firstOrNull { it.name == openName }
    val padding = PaddingValues(start = 12.dp, end = 12.dp, top = topPad, bottom = bottomPad)

    if (open != null) {
        LazyColumn(contentPadding = padding) {
            item {
                Row(Modifier.padding(start = 8.dp, end = 4.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(open.name, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("${open.songs.size} songs", color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp)
                    }
                    IconAction(Icons.Rounded.Delete, "Delete playlist") { vm.deletePlaylist(open.name); onOpen(null) }
                }
            }
            if (open.songs.isEmpty()) {
                item { Text("No songs yet. Use the ⋮ menu on a song to add it here.", color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(16.dp)) }
            }
            items(open.songs, key = { it.id }) { s ->
                ItemRow(
                    item = s.toItem(),
                    highlighted = s.id == vm.current?.id,
                    onClick = { onPlay(open.songs, open.songs.indexOf(s)) },
                ) { IconAction(Icons.Rounded.Delete, "Remove") { vm.removeFromPlaylist(open.name, s.id) } }
            }
        }
        return
    }

    LazyColumn(contentPadding = padding) {
        item {
            Text(
                "Playlists",
                color = Color.White,
                fontSize = 34.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
            )
        }
        item {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { creating = true }.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(52.dp).frost(RoundedCornerShape(12.dp), 0.18f), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Add, null, tint = KawaiPinkSoft)
                }
                Spacer(Modifier.width(12.dp))
                Text("New playlist", color = KawaiPinkSoft, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            }
        }
        items(vm.playlists, key = { it.name }) { p ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { onOpen(p.name) }.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ArtworkUrlBox(p.songs.firstOrNull()?.thumb, Modifier.size(52.dp), corner = 12.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(p.name, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Text("${p.songs.size} songs", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
                }
            }
        }
    }

    if (creating) NameDialog(
        onConfirm = { vm.createPlaylist(it); creating = false },
        onDismiss = { creating = false },
    )
}

// ---- shared bits --------------------------------------------------------------------------

@Composable
private fun IconAction(icon: ImageVector, description: String, onClick: () -> Unit) {
    Icon(
        icon, description, tint = Color.White.copy(alpha = 0.75f),
        modifier = Modifier.clip(CircleShape).clickable(onClick = onClick).padding(8.dp),
    )
}

@Composable
private fun NameDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New playlist") },
        text = { OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Name") }) },
        confirmButton = { TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun PlaylistPickerDialog(
    song: Song,
    playlists: List<Playlist>,
    onPick: (Playlist) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var creating by remember { mutableStateOf(false) }
    if (creating) {
        NameDialog(onConfirm = onCreate, onDismiss = { creating = false })
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add \"${song.title}\"", maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column {
                playlists.forEach { p ->
                    Text(
                        "${p.name}  ·  ${p.songs.size}",
                        modifier = Modifier.fillMaxWidth().clickable { onPick(p) }.padding(vertical = 12.dp),
                    )
                }
                Text(
                    "+ New playlist",
                    color = KawaiPink,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.fillMaxWidth().clickable { creating = true }.padding(vertical = 12.dp),
                )
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
