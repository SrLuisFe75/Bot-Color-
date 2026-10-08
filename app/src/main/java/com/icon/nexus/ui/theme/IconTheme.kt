package com.icon.nexus.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontLoadingStrategy
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.icon.nexus.R

@OptIn(ExperimentalTextApi::class)
private fun outfit(weight: FontWeight): Font = Font(
    resId = R.font.outfit_wght,
    weight = weight,
    loadingStrategy = FontLoadingStrategy.Async,
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight.weight),
    ),
)

private val Outfit = FontFamily(
    outfit(FontWeight.Light),
    outfit(FontWeight.Normal),
    outfit(FontWeight.Medium),
    outfit(FontWeight.SemiBold),
)

private fun style(
    weight: FontWeight,
    size: Int,
    line: Int,
    tracking: Float = 0f,
): TextStyle = TextStyle(
    fontFamily = Outfit,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.sp,
)

private val IconTypography = Typography(
    displayLarge = style(FontWeight.Light, 57, 64, -0.5f),
    displayMedium = style(FontWeight.Light, 45, 52, -0.4f),
    displaySmall = style(FontWeight.Normal, 36, 44, -0.2f),
    headlineLarge = style(FontWeight.Normal, 32, 40),
    headlineMedium = style(FontWeight.Normal, 28, 36),
    headlineSmall = style(FontWeight.Normal, 24, 32),
    titleLarge = style(FontWeight.Medium, 22, 28),
    titleMedium = style(FontWeight.Medium, 16, 24, 0.1f),
    titleSmall = style(FontWeight.Medium, 14, 20, 0.1f),
    bodyLarge = style(FontWeight.Normal, 16, 24),
    bodyMedium = style(FontWeight.Normal, 14, 20),
    bodySmall = style(FontWeight.Normal, 12, 16),
    labelLarge = style(FontWeight.Medium, 14, 20, 0.4f),
    labelMedium = style(FontWeight.Medium, 12, 16, 0.4f),
    labelSmall = style(FontWeight.Medium, 11, 16, 0.4f),
)

private val IconColors = darkColorScheme(
    background = Color(IconPalette.FIELD),
    surface = Color(IconPalette.FIELD),
    surfaceVariant = Color(IconPalette.FIELD_RAISED),
    primary = Color(IconPalette.CORE),
    onPrimary = Color(IconPalette.FIELD),
    secondary = Color(IconPalette.CYAN),
    tertiary = Color(IconPalette.VIOLET),
    onBackground = Color(IconPalette.ON_FIELD),
    onSurface = Color(IconPalette.ON_FIELD),
    onSurfaceVariant = Color(IconPalette.ON_FIELD),
)

@Composable
fun IconTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = IconColors,
        typography = IconTypography,
        content = content,
    )
}
