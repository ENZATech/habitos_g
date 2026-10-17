package com.habitos.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val ObsidianBg = Color(0xFF0C0A09)
val ObsidianCard = Color(0xFF171412)
val ObsidianNav = Color(0xFF141210)
val ObsidianBorder = Color(0xFF292524)
val EmeraldPrimary = Color(0xFF10B981)
val TealSecondary = Color(0xFF0D9488)
val AmberStreak = Color(0xFFF59E0B)
val RoseAlert = Color(0xFFF43F5E)
val TextPrimary = Color(0xFFFAFAF9)
val TextMuted = Color(0xFFA8A29E)

private val HabitOSColorScheme = darkColorScheme(
    primary = EmeraldPrimary,
    onPrimary = Color(0xFF052E16),
    secondary = TealSecondary,
    onSecondary = Color.White,
    tertiary = AmberStreak,
    background = ObsidianBg,
    onBackground = TextPrimary,
    surface = ObsidianCard,
    onSurface = TextPrimary,
    surfaceVariant = ObsidianNav,
    onSurfaceVariant = TextMuted,
    outline = ObsidianBorder,
    error = RoseAlert
)

@Composable
fun HabitOSTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = HabitOSColorScheme,
        content = content
    )
}