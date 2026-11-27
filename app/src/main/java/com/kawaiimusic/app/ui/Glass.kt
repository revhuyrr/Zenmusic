package com.kawaiimusic.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import coil.compose.AsyncImage
import com.kawaiimusic.app.sized
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Frosted "liquid glass" surface: a translucent white gradient with a bright, thin edge
 * highlight. Sits on top of [AmbientBackground], whose blurred artwork shows through.
 */
fun Modifier.glass(
    shape: Shape = RoundedCornerShape(24.dp),
    strength: Float = 0.14f,
): Modifier = this
    .clip(shape)
    .background(
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = strength + 0.07f),
                Color.White.copy(alpha = (strength - 0.04f).coerceAtLeast(0.02f)),
            )
        )
    )
    .border(
        width = 1.dp,
        brush = Brush.linearGradient(
            listOf(
                Color.White.copy(alpha = 0.55f),
                Color.White.copy(alpha = 0.08f),
                Color.White.copy(alpha = 0.30f),
            )
        ),
        shape = shape,
    )

/** Blurred, colour-tinted artwork backdrop (the Apple Music "ambient" look). */
@Composable
fun AmbientBackground(artworkUrl: String?, accent: Color, modifier: Modifier = Modifier) {
    val tint by animateColorAsState(accent, tween(900), label = "accent")
    Box(modifier.fillMaxSize().background(KawaiBase)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(tint.copy(alpha = 0.85f), KawaiBase)))
        )
        if (artworkUrl != null) {
            AsyncImage(
                model = artworkUrl.sized(256),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { scaleX = 1.5f; scaleY = 1.5f }
                    .blur(70.dp, BlurredEdgeTreatment.Unbounded)
                    .alpha(0.55f),
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.15f), Color.Black.copy(alpha = 0.55f))
                    )
                )
        )
    }
}

@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    tint: Color = Color.White,
    active: Boolean = false,
) {
    Box(
        modifier
            .size(size)
            .glass(CircleShape, strength = if (active) 0.30f else 0.14f)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}
