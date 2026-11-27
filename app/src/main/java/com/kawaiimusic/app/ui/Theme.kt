package com.kawaiimusic.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val KawaiPink = Color(0xFFFF4080)
val KawaiPinkSoft = Color(0xFFFFB8D2)
val KawaiBase = Color(0xFF0E0B10)

@Composable
fun KawaiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = KawaiPink,
            onPrimary = Color.White,
            background = KawaiBase,
            onBackground = Color.White,
            surface = Color(0xFF1B151B),
            onSurface = Color.White,
        ),
        content = content,
    )
}

fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}
