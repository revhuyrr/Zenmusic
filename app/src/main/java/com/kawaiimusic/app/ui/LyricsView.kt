package com.kawaiimusic.app.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kawaiimusic.app.Lyrics
import kotlin.math.abs

/**
 * Apple Music-style lyrics: big bold left-aligned lines, the active line is bright and full
 * size, neighbouring lines fade and blur with distance, the list follows playback and a tap
 * seeks to that line.
 */
@Composable
fun LyricsView(
    lyrics: Lyrics,
    positionMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lines = lyrics.lines
    val activeIndex = if (lyrics.synced) lines.indexOfLast { it.timeMs <= positionMs + 200 } else -1
    val listState = rememberLazyListState()

    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0) listState.animateScrollToItem((activeIndex - 1).coerceAtLeast(0))
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(top = 8.dp, bottom = 320.dp),
    ) {
        itemsIndexed(lines) { index, line ->
            val synced = lyrics.synced
            val distance = if (synced && activeIndex >= 0) abs(index - activeIndex) else 0
            val upcomingOrActive = !synced || activeIndex < 0 || distance == 0

            val alpha by animateFloatAsState(
                targetValue = when {
                    !synced -> 0.92f
                    activeIndex < 0 -> 0.5f
                    distance == 0 -> 1f
                    else -> (0.5f - 0.1f * distance).coerceAtLeast(0.22f)
                },
                animationSpec = tween(350),
                label = "lyricAlpha",
            )
            val blurRadius by animateDpAsState(
                targetValue = if (!synced || activeIndex < 0 || distance == 0) 0.dp
                else (distance * 1.2f).coerceAtMost(4f).dp,
                animationSpec = tween(350),
                label = "lyricBlur",
            )
            val scale by animateFloatAsState(
                targetValue = if (!synced || upcomingOrActive) 1f else 0.94f,
                animationSpec = tween(350),
                label = "lyricScale",
            )

            val rowModifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .let { if (synced) it.clickable { onSeek(line.timeMs) } else it }
                .padding(vertical = 10.dp, horizontal = 4.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(0f, 0.5f)
                }
                .blur(blurRadius)

            Text(
                text = line.text.ifBlank { "♪" },
                color = Color.White.copy(alpha = alpha),
                fontSize = 30.sp,
                lineHeight = 38.sp,
                fontWeight = FontWeight.Bold,
                modifier = rowModifier,
            )
        }
    }
}
