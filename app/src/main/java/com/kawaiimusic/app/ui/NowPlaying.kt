package com.kawaiimusic.app.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.kawaiimusic.app.PlayerViewModel

@Composable
fun NowPlayingScreen(
    vm: PlayerViewModel,
    onClose: () -> Unit,
    onAddToPlaylist: () -> Unit,
) {
    val song = vm.current ?: return
    var showLyrics by remember { mutableStateOf(false) }
    var scrubbing by remember { mutableStateOf(false) }
    var scrubValue by remember { mutableFloatStateOf(0f) }
    val backdrop = rememberGlassBackdrop()

    Box(Modifier.fillMaxSize()) {
        // Glass source: only the ambient artwork lives in it, controls are siblings drawn after.
        Box(Modifier.fillMaxSize().glassSource(backdrop)) {
            AmbientBackground(song.thumb, vm.accent)
        }

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // grabber
            Box(Modifier.fillMaxWidth().clickable(onClick = onClose).padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .width(44.dp)
                        .height(5.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.45f))
                )
            }
            Spacer(Modifier.height(8.dp))

            Crossfade(targetState = showLyrics, modifier = Modifier.weight(1f).fillMaxWidth(), label = "mode") { lyricsMode ->
                if (lyricsMode) LyricsPane(vm) else ArtworkPane(vm)
            }

            Spacer(Modifier.height(12.dp))

            // title + actions
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        song.title,
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        vm.playbackError ?: song.artist,
                        color = if (vm.playbackError != null) Color(0xFFFFB4B4) else Color.White.copy(alpha = 0.65f),
                        fontSize = 17.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                GlassIconButton(backdrop, Icons.Rounded.PlaylistAdd, "Add to playlist", onAddToPlaylist, size = 44.dp)
            }

            // progress
            val duration = vm.durationMs.coerceAtLeast(1L)
            val shown = if (scrubbing) scrubValue else vm.positionMs.toFloat().coerceIn(0f, duration.toFloat())
            Slider(
                value = shown,
                onValueChange = { scrubbing = true; scrubValue = it },
                onValueChangeFinished = { vm.seekTo(scrubValue.toLong()); scrubbing = false },
                valueRange = 0f..duration.toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Color.White.copy(alpha = 0.25f),
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatTime(shown.toLong()), color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                Text("-" + formatTime(duration - shown.toLong()), color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
            }

            Spacer(Modifier.height(8.dp))

            // transport (glass circles)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassIconButton(backdrop, Icons.Rounded.SkipPrevious, "Previous", vm::previous, size = 62.dp)
                Box(contentAlignment = Alignment.Center) {
                    GlassIconButton(
                        backdrop,
                        if (vm.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        if (vm.isPlaying) "Pause" else "Play",
                        vm::togglePlay,
                        size = 80.dp,
                    )
                    if (vm.isBuffering) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(80.dp),
                        )
                    }
                }
                GlassIconButton(backdrop, Icons.Rounded.SkipNext, "Next", vm::next, size = 62.dp)
            }

            Spacer(Modifier.height(18.dp))

            // bottom glass capsule: shuffle / lyrics / repeat
            Row(
                Modifier
                    .fillMaxWidth()
                    .liquidGlass(backdrop, RoundedCornerShape(30.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CapsuleButton(Icons.Rounded.Shuffle, "Shuffle", vm.shuffle, vm::toggleShuffle)
                CapsuleButton(Icons.Rounded.Lyrics, "Lyrics", showLyrics) { showLyrics = !showLyrics }
                CapsuleButton(
                    if (vm.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                    "Repeat",
                    vm.repeatMode != Player.REPEAT_MODE_OFF,
                    vm::cycleRepeat,
                )
            }
        }
    }
}

@Composable
private fun CapsuleButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(if (active) Color.White.copy(alpha = 0.22f) else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = if (active) Color.White else Color.White.copy(alpha = 0.75f), modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun ArtworkPane(vm: PlayerViewModel) {
    // Apple Music behaviour: artwork shrinks when paused, grows when playing.
    val scale by animateFloatAsState(
        targetValue = if (vm.isPlaying) 1f else 0.82f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 300f),
        label = "artScale",
    )
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        ArtworkUrlBox(
            url = vm.current?.thumb,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .shadow(28.dp, RoundedCornerShape(18.dp), ambientColor = Color.Black, spotColor = Color.Black),
            corner = 18.dp,
        )
    }
}

@Composable
private fun LyricsPane(vm: PlayerViewModel) {
    val song = vm.current ?: return
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
            ArtworkUrlBox(song.thumb, Modifier.size(52.dp), corner = 8.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(song.title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song.artist, color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        val lyrics = vm.lyrics
        if (lyrics == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (vm.lyricsLoading) "Loading lyrics…" else "No lyrics found for this song",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LyricsView(
                lyrics = lyrics,
                positionMs = vm.positionMs,
                onSeek = vm::seekTo,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
