package com.kawaiimusic.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.kawaiimusic.app.Kind
import com.kawaiimusic.app.MusicItem
import com.kawaiimusic.app.sized

/** Remote artwork with a pink placeholder shown while loading / when there is none. */
@Composable
fun ArtworkUrlBox(url: String?, modifier: Modifier, corner: Dp = 10.dp, round: Boolean = false) {
    val shape = if (round) CircleShape else RoundedCornerShape(corner)
    Box(
        modifier
            .clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFF6B2D48), Color(0xFFFF6FA0)))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.MusicNote, null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(28.dp))
        if (url != null) {
            AsyncImage(
                model = url.sized(544),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
fun ItemRow(
    item: MusicItem,
    highlighted: Boolean,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        Modifier
            .fillMaxWidth()
            .let { if (highlighted) it.frost(RoundedCornerShape(16.dp), 0.12f) else it }
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtworkUrlBox(item.thumb, Modifier.size(52.dp), corner = 10.dp, round = item.kind == Kind.ARTIST)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.title,
                color = if (highlighted) KawaiPinkSoft else Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val sub = if (item.kind == Kind.SONG) item.artist.ifBlank { item.subtitle } else item.subtitle
            if (sub.isNotBlank()) {
                Text(sub, color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        trailing()
    }
}

@Composable
fun ItemCard(item: MusicItem, onClick: () -> Unit) {
    Column(
        Modifier
            .width(148.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(4.dp)
    ) {
        ArtworkUrlBox(
            item.thumb,
            Modifier.size(140.dp),
            corner = 14.dp,
            round = item.kind == Kind.ARTIST,
        )
        Spacer(Modifier.size(6.dp))
        Text(item.title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        val sub = if (item.kind == Kind.SONG) item.artist.ifBlank { item.subtitle } else item.subtitle
        if (sub.isNotBlank()) {
            Text(sub, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun CenterMessage(text: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Text(text, color = Color.White.copy(alpha = 0.75f), fontSize = 16.sp)
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.size(14.dp))
                Text(
                    actionLabel,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .frost(RoundedCornerShape(50), 0.22f)
                        .clickable(onClick = onAction)
                        .padding(horizontal = 24.dp, vertical = 10.dp),
                )
            }
        }
    }
}
