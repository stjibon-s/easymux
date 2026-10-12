package com.termux.app.easymux

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import kotlin.math.roundToInt

private const val PREFIX = "/data/data/com.termux/files/usr"
private const val HOME = "/data/data/com.termux/files/home"

private fun runPopupCommand(command: String): String {
    return try {
        val pb = ProcessBuilder(listOf("$PREFIX/bin/bash", "-c", command))
        pb.redirectErrorStream(true)
        pb.environment()["PREFIX"] = PREFIX
        pb.environment()["HOME"] = HOME
        pb.environment()["LD_LIBRARY_PATH"] = "$PREFIX/lib"
        pb.environment()["PATH"] = "$PREFIX/bin:$PREFIX/bin/applets"
        pb.directory(File(HOME))
        val process = pb.start()
        val reader = BufferedReader(InputStreamReader(process.inputStream))
        val out = reader.readText()
        process.waitFor()
        out
    } catch (e: Exception) {
        "Error: ${e.message}"
    }
}

/**
 * App-wide singleton state so the popup terminal survives moving between screens
 * (different Activities) — it's the same process-wide object everywhere, so its
 * open/closed state, position, size, and output log persist across navigation.
 */
object PopupTerminalState {
    var isOpen by mutableStateOf(false)
    var buttonOffset by mutableStateOf(Offset(-1f, -1f)) // sentinel: not yet placed
    var panelOffset by mutableStateOf(Offset(-1f, -1f))  // sentinel: not yet placed
    var panelSize by mutableStateOf(DpSize(300.dp, 340.dp))
    var command by mutableStateOf("")
    var output by mutableStateOf("EasyMux Quick Terminal\nRun one-shot commands here.\n")
    var isRunning by mutableStateOf(false)
}

/**
 * Floating in-app "quick terminal" — runs one-shot commands (not a full interactive PTY,
 * so programs like vim/nano won't work here; use "Open Terminal" for those).
 * The button and panel both stay clamped fully inside the screen — dragging to an edge
 * stops right at the edge instead of going off-screen. Panel supports YouTube-PiP-style
 * drag-to-move plus two-finger pinch-to-zoom (resize) in one gesture.
 */
@Composable
fun PopupTerminalOverlay() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE) }
    val enabled = remember { prefs.getBoolean(PREF_POPUP_TERMINAL_ENABLED, true) }
    if (!enabled) return

    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    fun runCommand() {
        val cmdToRun = PopupTerminalState.command
        if (cmdToRun.isBlank() || PopupTerminalState.isRunning) return
        PopupTerminalState.isRunning = true
        scope.launch {
            val result = withContext(Dispatchers.IO) { runPopupCommand(cmdToRun) }
            PopupTerminalState.output = "${PopupTerminalState.output}\n$ $cmdToRun\n$result"
            PopupTerminalState.command = ""
            PopupTerminalState.isRunning = false
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val maxWidthPx = with(density) { maxWidth.toPx() }
        val maxHeightPx = with(density) { maxHeight.toPx() }
        val buttonSizePx = with(density) { 56.dp.toPx() }
        val edgeMargin = with(density) { 16.dp.toPx() }

        // Place defaults the first time we know the real screen size.
        if (PopupTerminalState.buttonOffset.x < 0f) {
            PopupTerminalState.buttonOffset = Offset(
                maxWidthPx - buttonSizePx - edgeMargin,
                maxHeightPx - buttonSizePx - edgeMargin
            )
        }
        if (PopupTerminalState.panelOffset.x < 0f) {
            PopupTerminalState.panelOffset = Offset(edgeMargin, edgeMargin * 4)
        }

        // Keep whatever is currently shown clamped inside the (possibly new) screen bounds.
        val clampedButtonX = PopupTerminalState.buttonOffset.x.coerceIn(0f, (maxWidthPx - buttonSizePx).coerceAtLeast(0f))
        val clampedButtonY = PopupTerminalState.buttonOffset.y.coerceIn(0f, (maxHeightPx - buttonSizePx).coerceAtLeast(0f))
        if (clampedButtonX != PopupTerminalState.buttonOffset.x || clampedButtonY != PopupTerminalState.buttonOffset.y) {
            PopupTerminalState.buttonOffset = Offset(clampedButtonX, clampedButtonY)
        }

        val panelWidthPx = with(density) { PopupTerminalState.panelSize.width.toPx() }
        val panelHeightPx = with(density) { PopupTerminalState.panelSize.height.toPx() }
        val clampedPanelX = PopupTerminalState.panelOffset.x.coerceIn(0f, (maxWidthPx - panelWidthPx).coerceAtLeast(0f))
        val clampedPanelY = PopupTerminalState.panelOffset.y.coerceIn(0f, (maxHeightPx - panelHeightPx).coerceAtLeast(0f))
        if (clampedPanelX != PopupTerminalState.panelOffset.x || clampedPanelY != PopupTerminalState.panelOffset.y) {
            PopupTerminalState.panelOffset = Offset(clampedPanelX, clampedPanelY)
        }

        if (PopupTerminalState.isOpen) {
            Box(
                modifier = Modifier
                    .offset { IntOffset(PopupTerminalState.panelOffset.x.roundToInt(), PopupTerminalState.panelOffset.y.roundToInt()) }
                    .size(PopupTerminalState.panelSize.width, PopupTerminalState.panelSize.height)
                    .background(HackSurface)
                    .border(1.dp, HackGreen)
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val newWidthPx = (panelWidthPx * zoom).coerceIn(with(density) { 200.dp.toPx() }, with(density) { 520.dp.toPx() })
                            val newHeightPx = (panelHeightPx * zoom).coerceIn(with(density) { 220.dp.toPx() }, with(density) { 620.dp.toPx() })
                            PopupTerminalState.panelSize = with(density) { DpSize(newWidthPx.toDp(), newHeightPx.toDp()) }

                            val proposedX = PopupTerminalState.panelOffset.x + pan.x
                            val proposedY = PopupTerminalState.panelOffset.y + pan.y
                            PopupTerminalState.panelOffset = Offset(
                                proposedX.coerceIn(0f, (maxWidthPx - newWidthPx).coerceAtLeast(0f)),
                                proposedY.coerceIn(0f, (maxHeightPx - newHeightPx).coerceAtLeast(0f))
                            )
                        }
                    }
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Quick Terminal",
                            color = HackGreen,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { PopupTerminalState.isOpen = false }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Filled.Close, contentDescription = "Close", tint = HackGreen)
                        }
                    }
                    Box(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        Text(PopupTerminalState.output, color = HackGreen, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                    }
                    if (PopupTerminalState.isRunning) {
                        HackLoader()
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("$ ", color = HackGreen, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                        BasicTextField(
                            value = PopupTerminalState.command,
                            onValueChange = { PopupTerminalState.command = it },
                            textStyle = TextStyle(color = HackGreen, fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                            cursorBrush = SolidColor(HackGreen),
                            modifier = Modifier.weight(1f).padding(4.dp)
                        )
                        IconButton(onClick = { runCommand() }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = "Run", tint = HackGreen)
                        }
                    }
                    Text(
                        "Drag to move \u2022 pinch to resize",
                        color = HackGreenDim,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp
                    )
                }
            }
        } else {
            FloatingActionButton(
                onClick = { PopupTerminalState.isOpen = true },
                containerColor = HackGreen,
                contentColor = Color.Black,
                modifier = Modifier
                    .offset { IntOffset(PopupTerminalState.buttonOffset.x.roundToInt(), PopupTerminalState.buttonOffset.y.roundToInt()) }
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val proposedX = PopupTerminalState.buttonOffset.x + dragAmount.x
                            val proposedY = PopupTerminalState.buttonOffset.y + dragAmount.y
                            PopupTerminalState.buttonOffset = Offset(
                                proposedX.coerceIn(0f, (maxWidthPx - buttonSizePx).coerceAtLeast(0f)),
                                proposedY.coerceIn(0f, (maxHeightPx - buttonSizePx).coerceAtLeast(0f))
                            )
                        }
                    }
            ) {
                Icon(Icons.Filled.Terminal, contentDescription = "Quick Terminal")
            }
        }
    }
}
