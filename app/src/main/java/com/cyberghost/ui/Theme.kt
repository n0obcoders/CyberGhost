package com.cyberghost.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

val CgGreen = Color(0xFF00FF41)
val CgDimGreen = Color(0xFF00AA2A)
val CgCyan = Color(0xFF00D4FF)
val CgAmber = Color(0xFFFFB000)
val CgRed = Color(0xFFFF3333)
val CgMagenta = Color(0xFFFF44FF)
val CgBg = Color(0xFF0A0A0F)
val CgSurface = Color(0xFF12121A)
val CgCard = Color(0xFF1A1A25)
val CgGray = Color(0xFF6B6B80)
val CgWhite = Color(0xFFE0E0EC)
val CgBlue = Color(0xFF4488FF)

val CgColors = darkColorScheme(
    background = CgBg,
    surface = CgSurface,
    surfaceVariant = CgCard,
    primary = CgGreen,
    secondary = CgCyan,
    tertiary = CgAmber,
    error = CgRed,
    onBackground = CgWhite,
    onSurface = CgWhite,
    onPrimary = CgBg,
    onSecondary = CgBg,
    outline = CgGray,
    surfaceContainerHigh = CgCard
)

@Composable
fun CyberGhostTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CgColors,
        typography = Typography(
            headlineLarge = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, color = CgWhite),
            headlineMedium = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, color = CgWhite),
            titleLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = CgWhite),
            titleMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, color = CgWhite),
            bodyLarge = TextStyle(fontSize = 14.sp, color = CgWhite),
            bodyMedium = TextStyle(fontSize = 12.sp, color = CgGray),
            bodySmall = TextStyle(fontSize = 10.sp, color = CgGray),
            labelLarge = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CgWhite),
            labelMedium = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, color = CgGray),
            labelSmall = TextStyle(fontSize = 9.sp, color = CgGray)
        ),
        content = content
    )
}
