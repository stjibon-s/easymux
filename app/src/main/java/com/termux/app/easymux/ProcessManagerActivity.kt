package com.termux.app.easymux

import android.os.Bundle
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

private const val PREFIX = "/data/data/com.termux/files/usr"
private const val HOME = "/data/data/com.termux/files/home"

private fun runTermuxCommand(vararg args: String): String {
    return try {
        val pb = ProcessBuilder(listOf("$PREFIX/bin/bash", "-c", args.joinToString(" ")))
        pb.redirectErrorStream(true)
        pb.environment()["PREFIX"] = PREFIX
        pb.environment()["HOME"] = HOME
        pb.environment()["LD_LIBRARY_PATH"] = "$PREFIX/lib"
        pb.environment()["PATH"] = "$PREFIX/bin:$PREFIX/bin/applets"
        pb.directory(File(HOME))
        val process = pb.start()
        val reader = BufferedReader(InputStreamReader(process.inputStream))
        val output = reader.readText()
        process.waitFor()
        output
    } catch (e: Exception) {
        "Error: ${e.message}"
    }
}

data class ProcInfo(val pid: String, val cpu: String, val mem: String, val name: String)

class ProcessManagerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                ProcessManagerScreen(onBack = { finish() })
            }
        }
    }
}

@Composable
fun ProcessManagerScreen(onBack: () -> Unit) {
    var processes by remember { mutableStateOf(listOf<ProcInfo>()) }
    var isLoading by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    fun refresh() {
        isLoading = true
        scope.launch {
            val output = withContext(Dispatchers.IO) { runTermuxCommand("ps", "-o", "pid,pcpu,pmem,comm") }
            val selfPid = Process.myPid().toString()
            processes = output.lines().drop(1).mapNotNull { line ->
                val parts = line.trim().split(Regex("\\s+"))
                if (parts.size >= 4 && parts[0] != selfPid) {
                    ProcInfo(parts[0], parts[1], parts[2], parts.drop(3).joinToString(" "))
                } else null
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { refresh() }

    fun killProcess(pid: String) {
        scope.launch {
            withContext(Dispatchers.IO) { runTermuxCommand("kill", "-9", pid) }
            statusMessage = "Sent kill signal to PID $pid"
            refresh()
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                }
                Text("Process Manager", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = { refresh() }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                }
            }
            Text(
                "Note: only Termux's own processes are visible without root.",
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            if (statusMessage.isNotBlank()) {
                Text(statusMessage, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (isLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

            LazyColumn(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
                items(processes) { proc ->
                    ElevatedCard(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(proc.name, fontWeight = FontWeight.Medium)
                                Text("PID ${proc.pid} · CPU ${proc.cpu}% · MEM ${proc.mem}%", fontSize = 11.sp)
                            }
                            TextButton(onClick = { killProcess(proc.pid) }) {
                                Text("Kill")
                            }
                        }
                    }
                }
            }
        }
    }
}
