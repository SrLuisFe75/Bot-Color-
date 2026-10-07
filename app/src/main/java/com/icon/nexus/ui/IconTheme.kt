package com.icon.nexus.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val IconColors = darkColorScheme(
    background = Color(0xFF0E1116),
    surface = Color(0xFF0E1116),
    primary = Color(0xFF7C9CFF),
    onPrimary = Color(0xFF0E1116),
    onBackground = Color(0xFFF2F4F8),
    onSurface = Color(0xFFF2F4F8),
)

@Composable
fun IconTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = IconColors,
        content = content,
    )
}
