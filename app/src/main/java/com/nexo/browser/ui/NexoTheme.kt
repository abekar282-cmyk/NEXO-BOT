package com.nexo.browser.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R

object NexoColors {
    val ObsidianBlack = Color(0xFF05070C)
    val DeepSlate = Color(0xFF0B101B)
    val CardSurface = Color(0xFF101726)
    val CardSurfaceHigh = Color(0xFF162035)
    val BorderSubtle = Color(0xFF22304D)
    val ElectricBlue = Color(0xFF2979FF)
    val ElectricBlueSoft = Color(0xFF1B3566)
    val BlazeOrange = Color(0xFFFF6D00)
    val BlazeOrangeSoft = Color(0xFF4A250A)
    val EmeraldOk = Color(0xFF00E676)
    val EmeraldSoft = Color(0xFF0A3B23)
    val CoralError = Color(0xFFFF5252)
    val CoralSoft = Color(0xFF47161B)
    val AmberWarn = Color(0xFFFFAB00)
    val TextPrimary = Color(0xFFF3F6FC)
    val TextSecondary = Color(0xFF98A6C3)
    val TextMuted = Color(0xFF647392)
    val IncognitoPurple = Color(0xFF9C4DFF)
}

val SpaceGroteskFamily = FontFamily(Font(R.font.space_grotesk, FontWeight.Normal))
val PlusJakartaFamily = FontFamily(Font(R.font.plus_jakarta_sans, FontWeight.Normal))
val JetBrainsMonoFamily = FontFamily(Font(R.font.jetbrains_mono, FontWeight.Normal))

private val NexoDarkColorScheme = darkColorScheme(
    primary = NexoColors.ElectricBlue,
    onPrimary = Color.White,
    primaryContainer = NexoColors.ElectricBlueSoft,
    onPrimaryContainer = Color(0xFFD6E4FF),
    secondary = NexoColors.BlazeOrange,
    onSecondary = Color.White,
    secondaryContainer = NexoColors.BlazeOrangeSoft,
    onSecondaryContainer = Color(0xFFFFD8BD),
    tertiary = NexoColors.EmeraldOk,
    onTertiary = Color(0xFF002110),
    background = NexoColors.ObsidianBlack,
    onBackground = NexoColors.TextPrimary,
    surface = NexoColors.DeepSlate,
    onSurface = NexoColors.TextPrimary,
    surfaceVariant = NexoColors.CardSurface,
    onSurfaceVariant = NexoColors.TextSecondary,
    surfaceContainerHigh = NexoColors.CardSurfaceHigh,
    outline = NexoColors.BorderSubtle,
    outlineVariant = Color(0xFF182238),
    error = NexoColors.CoralError,
    onError = Color.White,
    errorContainer = NexoColors.CoralSoft,
    onErrorContainer = Color(0xFFFFDAD6)
)

private val NexoTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = SpaceGroteskFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = SpaceGroteskFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    titleLarge = TextStyle(
        fontFamily = SpaceGroteskFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp
    ),
    titleMedium = TextStyle(
        fontFamily = PlusJakartaFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = PlusJakartaFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = PlusJakartaFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp
    ),
    labelLarge = TextStyle(
        fontFamily = PlusJakartaFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    labelMedium = TextStyle(
        fontFamily = JetBrainsMonoFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp
    ),
    labelSmall = TextStyle(
        fontFamily = JetBrainsMonoFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 10.sp,
        lineHeight = 14.sp
    )
)

private val NexoShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun NexoBrowserTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NexoDarkColorScheme,
        typography = NexoTypography,
        shapes = NexoShapes,
        content = content
    )
}
