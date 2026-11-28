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
import coil.compose.AsyncImage
import com.kawaiimusic.app.sized
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur as backdropBlur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy

/*
 * Liquid glass, built on Kyant's `backdrop` library (the same one SimpMusic uses).
 *
 * Rule: a glass surface samples a *source* layer (Modifier.glassSource) and must be a SIBLING of
 * it - never a child. Nesting a glass surface inside its own source makes the layer render
 * itself and crashes the shader. So screens are laid out as
 *
 *     Box {
 *         Box(Modifier.glassSource(backdrop)) { background + scrolling content }
 *         ...glass chrome (tab bar, buttons) drawn after it...
 *     }
 */

/** The layer glass surfaces refract. */
@Composable
fun rememberGlassBackdrop(): LayerBackdrop = rememberLayerBackdrop {
    drawRect(KawaiBase)
    drawContent()
}

/** Marks a composable as the source that sibling glass surfaces blur and refract. */
fun Modifier.glassSource(backdrop: LayerBackdrop): Modifier = this.layerBackdrop(backdrop)

/** Frosted, refracting glass: backdrop blur + lens + vibrancy, with a soft dark scrim. */
fun Modifier.liquidGlass(
    backdrop: LayerBackdrop,
    shape: Shape = CircleShape,
    scrim: Float = 0.16f,
): Modifier = this.drawBackdrop(
    backdrop = backdrop,
    shape = { shape },
    effects = {
        vibrancy()
        colorControls(brightness = 0.05f, contrast = 1f, saturation = 1.5f)
        backdropBlur(10f.dp.toPx())
        lens(size.minDimension / 4f, size.minDimension / 2f, false)
    },
    onDrawBackdrop = { drawBackdrop -> drawBackdrop() },
    onDrawSurface = { drawRect(Color.Black.copy(alpha = scrim)) },
)

/**
 * Flat translucent panel for things that live INSIDE the scrolling content (and so cannot be
 * real glass): a white gradient with a bright edge.
 */
fun Modifier.frost(
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
    backdrop: LayerBackdrop,
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
            .liquidGlass(backdrop, CircleShape, scrim = if (active) 0.02f else 0.16f)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}
