package com.termux.app.easymux

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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

data class DistroInfo(val displayName: String, val alias: String)

class ProotDistroActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                ProotDistroScreen(onBack = { finish() })
            }
        }
    }
}

@Composable
fun ProotDistroScreen(onBack: () -> Unit) {
    val distros = listOf(
        DistroInfo("Ubuntu", "ubuntu"),
        DistroInfo("Debian", "debian"),
        DistroInfo("Kali Linux", "kali"),
        DistroInfo("Alpine", "alpine")
    )
    var installedDistros by remember { mutableStateOf(setOf<String>()) }
    var busyDistro by remember { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf("") }
    var isProotInstalled by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    fun refresh() {
        scope.launch {
            val binExists = withContext(Dispatchers.IO) { File("$PREFIX/bin/proot-distro").exists() }
            isProotInstalled = binExists
            if (binExists) {
                val output = withContext(Dispatchers.IO) { runTermuxCommand("proot-distro", "list", "--installed") }
                installedDistros = distros.filter { output.contains(it.alias, ignoreCase = true) }.map { it.alias }.toSet()
            }
        }
    }

    LaunchedEffect(Unit) { refresh() }

    fun installProotIfNeeded(onDone: () -> Unit) {
        scope.launch {
            statusMessage = "Running: pkg install -y proot-distro"
            withContext(Dispatchers.IO) { runTermuxCommand("pkg", "install", "-y", "proot-distro") }
            isProotInstalled = true
            statusMessage = ""
            onDone()
        }
    }

    fun installDistro(distro: DistroInfo) {
        busyDistro = distro.alias
        statusMessage = "Running: proot-distro install ${distro.alias}"
        scope.launch {
            val output = withContext(Dispatchers.IO) { runTermuxCommand("proot-distro", "install", distro.alias) }
            statusMessage = if (output.contains("error", ignoreCase = true) && !output.contains("already installed")) {
                "Failed: ${output.takeLast(200)}"
            } else {
                "${distro.displayName} installed"
            }
            busyDistro = null
            refresh()
        }
    }

    fun uninstallDistro(distro: DistroInfo) {
        busyDistro = distro.alias
        statusMessage = "Running: proot-distro remove ${distro.alias}"
        scope.launch {
            withContext(Dispatchers.IO) { runTermuxCommand("proot-distro", "remove", distro.alias) }
            statusMessage = "${distro.displayName} removed"
            busyDistro = null
            refresh()
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                }
                Text("Linux Distros", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            Text(
                "Run a full Linux distro inside Termux, no root required.",
                fontSize = 12.sp,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            if (!isProotInstalled) {
                Button(onClick = { installProotIfNeeded { } }, modifier = Modifier.fillMaxWidth()) {
                    Text("Install proot-distro (required, one-time)")
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (statusMessage.isNotBlank()) {
                Text(statusMessage, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
            }

            distros.forEach { distro ->
                val isInstalled = installedDistros.contains(distro.alias)
                ElevatedCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(distro.displayName, fontWeight = FontWeight.Medium)
                            Text(if (isInstalled) "Installed" else "Not installed", fontSize = 12.sp)
                        }
                        if (isInstalled) {
                            TextButton(onClick = { uninstallDistro(distro) }, enabled = busyDistro == null) {
                                Text("Remove")
                            }
                        }
                        Button(
                            onClick = {
                                if (!isProotInstalled) {
                                    installProotIfNeeded { installDistro(distro) }
                                } else if (isInstalled) {
                                    // Open terminal and let user run: proot-distro login <alias>
                                    statusMessage = "To enter: open Terminal and run 'proot-distro login ${distro.alias}'"
                                } else {
                                    installDistro(distro)
                                }
                            },
                            enabled = busyDistro == null
                        ) {
                            Text(
                                when {
                                    busyDistro == distro.alias -> "..."
                                    isInstalled -> "Open"
                                    else -> "Install"
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
