package com.termux.app.easymux

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val HackGreen = Color(0xFF39FF14)
val HackGreenDim = Color(0xFF1FA80E)
val HackBackground = Color(0xFF0A0E0A)
val HackSurface = Color(0xCC10160F)
val HackRed = Color(0xFFFF3B3B)

private val HackColorScheme = darkColorScheme(
    primary = HackGreen,
    onPrimary = Color.Black,
    secondary = HackGreenDim,
    onSecondary = Color.Black,
    background = HackBackground,
    onBackground = HackGreen,
    surface = HackSurface,
    onSurface = HackGreen,
    surfaceVariant = HackSurface,
    onSurfaceVariant = HackGreenDim,
    error = HackRed,
    onError = Color.Black,
    outline = HackGreenDim
)

private val HackTypography = Typography(
    bodyLarge = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 16.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 22.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 16.sp, fontWeight = FontWeight.Bold),
    labelLarge = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp, fontWeight = FontWeight.Bold)
)

@Composable
fun EasyMuxTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = HackColorScheme,
        typography = HackTypography,
        content = content
    )
}

/**
 * A green "hacking style" progress bar used everywhere the app shows a loading/running state.
 */
@Composable
fun HackProgress(modifier: Modifier = Modifier) {
    LinearProgressIndicator(
        modifier = modifier.fillMaxWidth(),
        color = HackGreen,
        trackColor = HackSurface
    )
}

/**
 * Blinking "> running..." style status line, monospace, green, with an animated cursor block.
 */
@Composable
fun HackStatusLine(text: String) {
    val transition = rememberInfiniteTransition(label = "cursor")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursorAlpha"
    )
    Row {
        Text("> $text", fontFamily = FontFamily.Monospace, color = HackGreen, fontSize = 13.sp)
        Text(
            "_",
            fontFamily = FontFamily.Monospace,
            color = HackGreen.copy(alpha = alpha),
            fontSize = 13.sp
        )
    }
}

/**
 * Animated "digital rain" background, drawn once behind every screen.
 * Deterministic per-column pattern driven by a single looping time value,
 * so no manual per-frame mutable state is needed.
 */
@Composable
fun MatrixRainBackground() {
    val transition = rememberInfiniteTransition(label = "matrix")
    val time by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(animation = tween(60000, easing = LinearEasing)),
        label = "matrixTime"
    )
    Canvas(modifier = Modifier.fillMaxSize().background(HackBackground)) {
        val columnWidth = 14.dp.toPx()
        val columns = (size.width / columnWidth).toInt() + 1
        val dotHeight = 10.dp.toPx()
        val dotWidth = 3.dp.toPx()
        for (i in 0 until columns) {
            val seed = i * 97 + 13
            val speed = 3f + (seed % 6)
            val streakLen = 6 + (seed % 10)
            val phase = (seed % 300).toFloat()
            val totalHeight = size.height + streakLen * 20f
            val y = ((time * speed + phase) % totalHeight) - streakLen * 20f
            val x = i * columnWidth
            for (j in 0 until streakLen) {
                val dotY = y - j * 20f
                if (dotY in 0f..size.height) {
                    val alpha = (1f - j.toFloat() / streakLen).coerceIn(0f, 1f) * 0.45f
                    drawRect(
                        color = HackGreen.copy(alpha = alpha),
                        topLeft = Offset(x, dotY),
                        size = Size(dotWidth, dotHeight)
                    )
                }
            }
        }
    }
}

/**
 * ASCII bracket-style "hacking" loading indicator, e.g. [####------] PROCESSING...
 * Used everywhere instead of a plain Material progress bar.
 */
@Composable
fun HackLoader(modifier: Modifier = Modifier, label: String = "PROCESSING") {
    val transition = rememberInfiniteTransition(label = "loader")
    val step by transition.animateFloat(
        initialValue = 0f,
        targetValue = 11f,
        animationSpec = infiniteRepeatable(animation = tween(1200, easing = LinearEasing)),
        label = "loaderStep"
    )
    val filled = step.toInt().coerceIn(0, 10)
    val bar = "[" + "#".repeat(filled) + "-".repeat(10 - filled) + "]"
    Text(
        "$bar $label...",
        fontFamily = FontFamily.Monospace,
        color = HackGreen,
        fontSize = 13.sp,
        modifier = modifier.fillMaxWidth()
    )
}

/**
 * A "glass" translucent card wrapper matching the hacking theme, drop-in replacement
 * for M3 ElevatedCard where a stronger see-through matrix-behind-card effect is wanted.
 */
@Composable
fun HackGlassBox(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .background(HackSurface)
    ) {
        content()
    }
}
@Composable
fun HackScreen(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        MatrixRainBackground()
        Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) {
            SelectionContainer {
                content()
            }
        }
        PopupTerminalOverlay()
    }
}
