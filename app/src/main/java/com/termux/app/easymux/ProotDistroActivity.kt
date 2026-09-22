package com.termux.app.easymux

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.termux.app.TermuxActivity
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

private fun isDistroInstalled(alias: String): Boolean {
    return try {
        val dir = File("$PREFIX/var/lib/proot-distro/installed-rootfs/$alias")
        dir.exists() && dir.isDirectory
    } catch (e: Exception) {
        false
    }
}

data class DistroInfo(val displayName: String, val alias: String)

class ProotDistroActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EasyMuxTheme {
                ProotDistroScreen(onBack = { finish() })
            }
        }
    }
}

@Composable
fun ProotDistroScreen(onBack: () -> Unit) {
    val context = LocalContext.current
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
                installedDistros = withContext(Dispatchers.IO) {
                    distros.filter { isDistroInstalled(it.alias) }.map { it.alias }.toSet()
                }
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
            val nowInstalled = withContext(Dispatchers.IO) { isDistroInstalled(distro.alias) }
            statusMessage = if (nowInstalled) {
                "${distro.displayName} installed successfully"
            } else {
                "Failed: ${output.takeLast(200)}"
            }
            busyDistro = null
            refresh()
        }
    }

    fun uninstallDistro(distro: DistroInfo) {
        busyDistro = distro.alias
        statusMessage = "Running: proot-distro remove ${distro.alias}"
        scope.launch {
            withContext(Dispatchers.IO) { runTermuxCommand("proot-distro", "remove", "-y", distro.alias) }
            statusMessage = "${distro.displayName} removed"
            busyDistro = null
            refresh()
        }
    }

    fun openDistro(distro: DistroInfo) {
        val command = "proot-distro login ${distro.alias}"
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("EasyMux", command))
        statusMessage = "Command copied: $command — paste it in the terminal that just opened"
        context.startActivity(Intent(context, TermuxActivity::class.java))
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                }
                Text("Linux Distros", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = { refresh() }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                }
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
                                    openDistro(distro)
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
