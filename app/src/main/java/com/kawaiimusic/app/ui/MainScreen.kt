package com.kawaiimusic.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Home
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
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

private enum class Tab(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Rounded.Home),
    Search("Search", Icons.Rounded.Search),
    Playlists("Playlists", Icons.Rounded.PlaylistPlay),
}

@Composable
fun MainScreen(vm: PlayerViewModel) {
    var tab by remember { mutableStateOf(Tab.Home) }
    var showPlayer by remember { mutableStateOf(false) }
    var openPlaylist by remember { mutableStateOf<String?>(null) }
    var pickerFor by remember { mutableStateOf<Song?>(null) }

    BackHandler(enabled = showPlayer) { showPlayer = false }
    BackHandler(enabled = !showPlayer && vm.detailOpen) { vm.closeDetail() }
    BackHandler(enabled = !showPlayer && !vm.detailOpen && openPlaylist != null) { openPlaylist = null }

    val playAndShow: (List<Song>, Int) -> Unit = { list, index -> vm.playSongs(list, index); showPlayer = true }
    val onItem: (MusicItem, List<MusicItem>) -> Unit = { item, siblings ->
        vm.onItemClick(item, siblings)
        if (item.kind == Kind.SONG) showPlayer = true
    }

    Box(Modifier.fillMaxSize()) {
        AmbientBackground(vm.current?.thumb, vm.accent)

        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            if (vm.detailOpen) {
                DetailScreen(vm, onItem, pickerOpen = { pickerFor = it }, modifier = Modifier.weight(1f))
            } else {
                Text(
                    text = if (tab == Tab.Playlists && openPlaylist != null) openPlaylist!! else tab.label,
                    color = Color.White,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp),
                )
                Box(Modifier.weight(1f)) {
                    when (tab) {
                        Tab.Home -> HomeTab(vm, onItem)
                        Tab.Search -> SearchTab(vm, onItem, pickerOpen = { pickerFor = it })
                        Tab.Playlists -> PlaylistsTab(vm, openPlaylist, { openPlaylist = it }, playAndShow)
                    }
                }
            }

            // mini player + glass tab bar
            Column(
                Modifier
                    .padding(horizontal = 12.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 8.dp)
            ) {
                if (vm.current != null) {
                    MiniPlayer(vm, onOpen = { showPlayer = true })
                    Spacer(Modifier.height(8.dp))
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .glass(RoundedCornerShape(30.dp), 0.14f)
                        .padding(6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    Tab.entries.forEach { t ->
                        val selected = t == tab
                        Row(
                            Modifier
                                .weight(1f)
                                .let { if (selected) it.glass(RoundedCornerShape(24.dp), 0.22f) else it }
                                .clickable {
                                    tab = t
                                    openPlaylist = null
                                    vm.closeDetail()
                                }
                                .padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(t.icon, t.label, tint = if (selected) KawaiPinkSoft else Color.White.copy(alpha = 0.7f))
                            if (selected) {
                                Spacer(Modifier.width(6.dp))
                                Text(t.label, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            }
                        }
                    }
                }
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
}

// ---- Home ---------------------------------------------------------------------------------

@Composable
private fun HomeTab(vm: PlayerViewModel, onItem: (MusicItem, List<MusicItem>) -> Unit) {
    when {
        vm.home.isNotEmpty() -> LazyColumn(contentPadding = PaddingValues(bottom = 12.dp)) {
            items(vm.home) { shelf ->
                Text(
                    shelf.title,
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp),
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(shelf.items) { item -> ItemCard(item) { onItem(item, shelf.items) } }
                }
            }
        }
        vm.homeLoading -> LoadingBox()
        else -> CenterMessage(vm.homeError ?: "Nothing to show right now", "Try again") { vm.loadHome() }
    }
}

@Composable
private fun LoadingBox() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = KawaiPinkSoft)
    }
}

// ---- Search -------------------------------------------------------------------------------

@Composable
private fun SearchTab(
    vm: PlayerViewModel,
    onItem: (MusicItem, List<MusicItem>) -> Unit,
    pickerOpen: (Song) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .glass(RoundedCornerShape(50), 0.16f)
                .padding(horizontal = 18.dp, vertical = 14.dp)
        ) {
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

        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SearchFilter.entries.forEach { f ->
                val selected = f == vm.filter
                Text(
                    f.label,
                    color = if (selected) Color.White else Color.White.copy(alpha = 0.7f),
                    fontSize = 14.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier
                        .glass(RoundedCornerShape(50), if (selected) 0.30f else 0.10f)
                        .clickable { vm.setFilter(f) }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                )
            }
        }

        Box(Modifier.weight(1f)) {
            when {
                vm.suggestions.isNotEmpty() && vm.query.isNotBlank() -> LazyColumn(contentPadding = PaddingValues(horizontal = 12.dp)) {
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
                vm.searching -> LoadingBox()
                vm.searchError != null -> CenterMessage(vm.searchError!!, "Retry") { vm.search() }
                vm.results.isNotEmpty() -> LazyColumn(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
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
                vm.searched -> CenterMessage("No results")
                else -> CenterMessage("Search YouTube Music")
            }
        }
    }
}

// ---- Detail (album / artist / playlist) -----------------------------------------------------

@Composable
private fun DetailScreen(
    vm: PlayerViewModel,
    onItem: (MusicItem, List<MusicItem>) -> Unit,
    pickerOpen: (Song) -> Unit,
    modifier: Modifier = Modifier,
) {
    val seed = vm.detailSeed
    val page = vm.detail
    val title = page?.title?.takeIf { it.isNotBlank() } ?: seed?.title ?: ""
    val thumb = page?.thumb ?: seed?.thumb

    Column(modifier.fillMaxSize()) {
        Row(Modifier.padding(start = 8.dp, top = 12.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassIconButton(Icons.Rounded.ArrowBack, "Back", vm::closeDetail, size = 42.dp)
            Spacer(Modifier.width(12.dp))
            Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        when {
            vm.detailLoading -> LoadingBox()
            vm.detailError != null -> CenterMessage(vm.detailError!!, "Back") { vm.closeDetail() }
            page != null -> LazyColumn(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                item {
                    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        ArtworkUrlBox(thumb, Modifier.width(110.dp).height(110.dp), corner = 14.dp, round = seed?.kind == Kind.ARTIST)
                        Spacer(Modifier.width(16.dp))
                        Column {
                            if (page.subtitle.isNotBlank()) {
                                Text(page.subtitle, color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
                            }
                            val songs = page.items.filter { it.kind == Kind.SONG }
                            if (songs.isNotEmpty()) {
                                Spacer(Modifier.height(10.dp))
                                Row(
                                    Modifier
                                        .glass(RoundedCornerShape(50), 0.26f)
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
}

// ---- shared bits --------------------------------------------------------------------------

@Composable
private fun IconAction(icon: ImageVector, description: String, onClick: () -> Unit) {
    Icon(
        icon, description, tint = Color.White.copy(alpha = 0.7f),
        modifier = Modifier.clickable(onClick = onClick).padding(8.dp),
    )
}

@Composable
private fun MiniPlayer(vm: PlayerViewModel, onOpen: () -> Unit) {
    val song = vm.current ?: return
    Row(
        Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(24.dp), 0.18f)
            .clickable(onClick = onOpen)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtworkUrlBox(song.thumb, Modifier.width(48.dp).height(48.dp), corner = 10.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                vm.playbackError ?: song.artist,
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (vm.isBuffering) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.width(24.dp).height(24.dp))
        } else {
            IconAction(if (vm.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Play/Pause", vm::togglePlay)
        }
        IconAction(Icons.Rounded.SkipNext, "Next", vm::next)
    }
}

// ---- Playlists ----------------------------------------------------------------------------

@Composable
private fun PlaylistsTab(
    vm: PlayerViewModel,
    openName: String?,
    onOpen: (String?) -> Unit,
    onPlay: (List<Song>, Int) -> Unit,
) {
    var creating by remember { mutableStateOf(false) }
    val open = vm.playlists.firstOrNull { it.name == openName }

    if (open != null) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${open.songs.size} songs", color = Color.White.copy(alpha = 0.6f), modifier = Modifier.weight(1f))
                IconAction(Icons.Rounded.Delete, "Delete playlist") { vm.deletePlaylist(open.name); onOpen(null) }
            }
            if (open.songs.isEmpty()) {
                CenterMessage("No songs yet. Use the ⋮ menu on a song to add it here.")
            } else {
                LazyColumn(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                    items(open.songs, key = { it.id }) { s ->
                        ItemRow(
                            item = s.toItem(),
                            highlighted = s.id == vm.current?.id,
                            onClick = { onPlay(open.songs, open.songs.indexOf(s)) },
                        ) { IconAction(Icons.Rounded.Delete, "Remove") { vm.removeFromPlaylist(open.name, s.id) } }
                    }
                }
            }
        }
        return
    }

    LazyColumn(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
        item {
            Row(
                Modifier.fillMaxWidth().clickable { creating = true }.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Add, null, tint = KawaiPinkSoft)
                Spacer(Modifier.width(12.dp))
                Text("New playlist", color = KawaiPinkSoft, fontWeight = FontWeight.SemiBold)
            }
        }
        items(vm.playlists, key = { it.name }) { p ->
            Row(
                Modifier.fillMaxWidth().clickable { onOpen(p.name) }.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.PlaylistPlay, null, tint = Color.White.copy(alpha = 0.8f))
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
