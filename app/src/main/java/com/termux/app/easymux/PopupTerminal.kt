package com.termux.app.easymux

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
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
 * Floating in-app "quick terminal" — runs one-shot commands (not a full interactive PTY,
 * so programs like vim/nano won't work here; use "Open Terminal" for those).
 * Draggable button and draggable/resizable panel, shown on top of any screen via HackScreen.
 */
@Composable
fun PopupTerminalOverlay() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE) }
    val enabled = remember { prefs.getBoolean(PREF_POPUP_TERMINAL_ENABLED, true) }
    if (!enabled) return

    var isOpen by remember { mutableStateOf(false) }
    var buttonOffset by remember { mutableStateOf(Offset.Zero) }
    var panelOffset by remember { mutableStateOf(Offset(40f, 300f)) }
    var panelSize by remember { mutableStateOf(DpSize(280.dp, 320.dp)) }
    var command by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("EasyMux Quick Terminal\nRun one-shot commands here.\n") }
    var isRunning by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun runCommand() {
        if (command.isBlank() || isRunning) return
        val cmdToRun = command
        isRunning = true
        scope.launch {
            val result = withContext(Dispatchers.IO) { runPopupCommand(cmdToRun) }
            output = "$output\n$ $cmdToRun\n$result"
            command = ""
            isRunning = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (isOpen) {
            Box(
                modifier = Modifier
                    .offset { IntOffset(panelOffset.x.roundToInt(), panelOffset.y.roundToInt()) }
                    .size(panelSize.width, panelSize.height)
                    .background(HackSurface)
                    .border(1.dp, HackGreen)
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(Unit) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    panelOffset += dragAmount
                                }
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Quick Terminal",
                            color = HackGreen,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { isOpen = false }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Filled.Close, contentDescription = "Close", tint = HackGreen)
                        }
                    }
                    Box(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        Text(output, color = HackGreen, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                    }
                    if (isRunning) {
                        HackLoader()
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("$ ", color = HackGreen, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                        BasicTextField(
                            value = command,
                            onValueChange = { command = it },
                            textStyle = TextStyle(color = HackGreen, fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                            cursorBrush = SolidColor(HackGreen),
                            modifier = Modifier.weight(1f).padding(4.dp)
                        )
                        IconButton(onClick = { runCommand() }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = "Run", tint = HackGreen)
                        }
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.End)
                            .size(22.dp)
                            .pointerInput(Unit) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    val dxDp = with(this) { dragAmount.x.toDp() }
                                    val dyDp = with(this) { dragAmount.y.toDp() }
                                    panelSize = DpSize(
                                        (panelSize.width + dxDp).coerceIn(180.dp, 600.dp),
                                        (panelSize.height + dyDp).coerceIn(200.dp, 700.dp)
                                    )
                                }
                            }
                    ) {
                        Icon(Icons.Filled.ZoomOutMap, contentDescription = "Resize", tint = HackGreenDim)
                    }
                }
            }
        } else {
            FloatingActionButton(
                onClick = { isOpen = true },
                containerColor = HackGreen,
                contentColor = Color.Black,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp)
                    .offset { IntOffset(buttonOffset.x.roundToInt(), buttonOffset.y.roundToInt()) }
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            buttonOffset += dragAmount
                        }
                    }
            ) {
                Icon(Icons.Filled.Terminal, contentDescription = "Quick Terminal")
            }
        }
    }
}
