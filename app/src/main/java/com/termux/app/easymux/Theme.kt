package com.termux.app.easymux

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
 * Sci-fi "HUD control room" style background: faint grid lines, slowly scrolling
 * number-grid columns along the left/right edges, and corner reticle brackets.
 * Inspired by sci-fi control-panel interfaces (not a literal falling-code matrix rain).
 * Deterministic, driven by a single looping time value — no manual per-frame state.
 */
@Composable
fun MatrixRainBackground() {
    val transition = rememberInfiniteTransition(label = "hud")
    val time by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(animation = tween(90000, easing = LinearEasing)),
        label = "hudTime"
    )
    val pulse by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(animation = tween(1800, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
        label = "hudPulse"
    )
    Canvas(modifier = Modifier.fillMaxSize().background(HackBackground)) {
        // Faint grid
        val gridSpacing = 40.dp.toPx()
        var gx = 0f
        while (gx < size.width) {
            drawLine(HackGreen.copy(alpha = 0.06f), Offset(gx, 0f), Offset(gx, size.height), strokeWidth = 1f)
            gx += gridSpacing
        }
        var gy = 0f
        while (gy < size.height) {
            drawLine(HackGreen.copy(alpha = 0.06f), Offset(0f, gy), Offset(size.width, gy), strokeWidth = 1f)
            gy += gridSpacing
        }

        // Scrolling number-grid columns along the left and right edges
        val colWidth = 16.dp.toPx()
        val rowHeight = 12.dp.toPx()
        val edgeColumns = 2
        val rows = (size.height / rowHeight).toInt() + 2
        for (side in 0..1) {
            for (c in 0 until edgeColumns) {
                val colSeed = side * 53 + c * 19 + 7
                val speed = 6f + (colSeed % 5)
                val x = if (side == 0) c * colWidth + 6.dp.toPx() else size.width - (c + 1) * colWidth - 6.dp.toPx()
                for (r in 0 until rows) {
                    val rowSeed = colSeed * 31 + r * 11
                    val scrollOffset = ((time * speed) % (rows * rowHeight))
                    val y = ((r * rowHeight - scrollOffset) % (rows * rowHeight) + rows * rowHeight) % (rows * rowHeight)
                    val blockOn = (rowSeed + (time.toInt() / 40)) % 3 != 0
                    if (blockOn) {
                        val alpha = 0.12f + 0.1f * ((rowSeed % 5) / 5f)
                        drawRect(
                            color = HackGreenDim.copy(alpha = alpha),
                            topLeft = Offset(x, y),
                            size = Size(colWidth - 3.dp.toPx(), rowHeight - 3.dp.toPx())
                        )
                    }
                }
            }
        }

        // Corner reticle brackets
        val bracketLen = 18.dp.toPx()
        val inset = 14.dp.toPx()
        val sw = 2.dp.toPx()
        val c = HackGreen.copy(alpha = pulse * 0.6f)
        // top-left
        drawLine(c, Offset(inset, inset), Offset(inset + bracketLen, inset), strokeWidth = sw)
        drawLine(c, Offset(inset, inset), Offset(inset, inset + bracketLen), strokeWidth = sw)
        // top-right
        drawLine(c, Offset(size.width - inset, inset), Offset(size.width - inset - bracketLen, inset), strokeWidth = sw)
        drawLine(c, Offset(size.width - inset, inset), Offset(size.width - inset, inset + bracketLen), strokeWidth = sw)
        // bottom-left
        drawLine(c, Offset(inset, size.height - inset), Offset(inset + bracketLen, size.height - inset), strokeWidth = sw)
        drawLine(c, Offset(inset, size.height - inset), Offset(inset, size.height - inset - bracketLen), strokeWidth = sw)
        // bottom-right
        drawLine(c, Offset(size.width - inset, size.height - inset), Offset(size.width - inset - bracketLen, size.height - inset), strokeWidth = sw)
        drawLine(c, Offset(size.width - inset, size.height - inset), Offset(size.width - inset, size.height - inset - bracketLen), strokeWidth = sw)
    }
}

private val hackLoaderPhrases = listOf(
    "Establishing secure connection",
    "Verifying package integrity",
    "Reading system state",
    "Processing request",
    "Writing changes",
    "Finalizing operation"
)

/**
 * "SYSTEM OVERRIDE IN PROGRESS"-style bordered loading panel: a title row, a cycling
 * list of status lines (typewriter reveal with a blinking cursor on the active line),
 * a bordered fill-bar, and a looping percentage readout. Used everywhere the app
 * shows a loading/running state instead of a plain Material progress bar.
 */
@Composable
fun HackLoader(modifier: Modifier = Modifier, label: String = "PROCESSING") {
    val transition = rememberInfiniteTransition(label = "loader")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 100f,
        animationSpec = infiniteRepeatable(animation = tween(5000, easing = LinearEasing)),
        label = "loaderProgress"
    )
    val cursorAlpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(animation = tween(450, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
        label = "loaderCursor"
    )
    val lineCount = hackLoaderPhrases.size
    val activeIndex = ((progress / 100f) * lineCount).toInt().coerceIn(0, lineCount - 1)

    androidx.compose.foundation.layout.Column(
        modifier = modifier
            .fillMaxWidth()
            .background(HackSurface)
            .border(1.dp, HackGreen)
            .padding(10.dp)
    ) {
        Row {
            Text("\u2620 ", color = HackGreen, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
            Text(
                "$label...",
                color = HackGreen,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(6.dp))
        for (i in 0..activeIndex) {
            val isLast = i == activeIndex
            Row {
                Text("> ${hackLoaderPhrases[i]}...", color = HackGreen, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                if (isLast) {
                    Text(
                        "_",
                        color = HackGreen.copy(alpha = cursorAlpha),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                }
            }
        }
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .border(1.dp, HackGreen)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction = (progress / 100f).coerceIn(0f, 1f))
                    .background(HackGreen)
            )
        }
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(4.dp))
        Text(
            "LOADING: ${progress.toInt()}%",
            color = HackGreen,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp
        )
    }
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
        Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent, contentColor = HackGreen) {
            SelectionContainer {
                content()
            }
        }
        PopupTerminalOverlay()
    }
}
