package com.termux.app.easymux

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
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
        val out = reader.readText()
        process.waitFor()
        out
    } catch (e: Exception) {
        "Error: ${e.message}"
    }
}

class SshActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EasyMuxTheme {
                SshScreen(onBack = { finish() })
            }
        }
    }
}

@Composable
fun SshScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var isServerRunning by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("") }
    var isBusy by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("") }
    var localIp by remember { mutableStateOf("Unknown") }
    var publicKeyText by remember { mutableStateOf("") }

    var connectHost by remember { mutableStateOf("") }
    var connectPort by remember { mutableStateOf("22") }
    var connectUser by remember { mutableStateOf("") }

    val scope = rememberCoroutineScope()

    fun refreshStatus() {
        scope.launch {
            val pgrep = withContext(Dispatchers.IO) { runTermuxCommand("pgrep", "sshd") }
            isServerRunning = pgrep.trim().isNotBlank()
            username = withContext(Dispatchers.IO) { runTermuxCommand("whoami") }.trim()
        }
    }

    LaunchedEffect(Unit) {
        refreshStatus()
        localIp = try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val ip = wifiManager.connectionInfo?.ipAddress ?: 0
            if (ip != 0) String.format("%d.%d.%d.%d", ip and 0xff, ip shr 8 and 0xff, ip shr 16 and 0xff, ip shr 24 and 0xff)
            else "Not connected to WiFi"
        } catch (e: Exception) {
            "Unavailable"
        }
    }

    fun startServer() {
        isBusy = true
        statusMessage = "Running: pkg install -y openssh && sshd"
        scope.launch {
            withContext(Dispatchers.IO) { runTermuxCommand("pkg", "install", "-y", "openssh") }
            withContext(Dispatchers.IO) { runTermuxCommand("sshd") }
            statusMessage = "sshd started (default port 8022). Set a password with 'passwd' in Terminal if you haven't."
            isBusy = false
            refreshStatus()
        }
    }

    fun stopServer() {
        isBusy = true
        scope.launch {
            withContext(Dispatchers.IO) { runTermuxCommand("pkill", "sshd") }
            statusMessage = "sshd stopped"
            isBusy = false
            refreshStatus()
        }
    }

    fun generateKey() {
        isBusy = true
        statusMessage = "Generating key..."
        scope.launch {
            withContext(Dispatchers.IO) { runTermuxCommand("pkg", "install", "-y", "openssh") }
            withContext(Dispatchers.IO) {
                runTermuxCommand("test", "-f", "$HOME/.ssh/id_ed25519", "||", "ssh-keygen", "-t", "ed25519", "-f", "$HOME/.ssh/id_ed25519", "-N", "\"\"")
            }
            publicKeyText = withContext(Dispatchers.IO) { runTermuxCommand("cat", "$HOME/.ssh/id_ed25519.pub") }
            statusMessage = "Key ready"
            isBusy = false
        }
    }

    fun copyPublicKey() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("EasyMux", publicKeyText))
        statusMessage = "Public key copied to clipboard"
    }

    fun connect() {
        if (connectHost.isBlank()) return
        val user = connectUser.ifBlank { "root" }
        val cmd = "ssh $user@$connectHost -p $connectPort"
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("EasyMux", cmd))
        statusMessage = "Command copied — paste it in the Terminal that just opened"
        context.startActivity(Intent(context, TermuxActivity::class.java))
    }

    HackScreen {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                }
                Text("SSH", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(12.dp))

            Text("Server", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Status: ${if (isServerRunning) "Running" else "Stopped"}", fontSize = 13.sp)
                    Text("User: $username", fontSize = 13.sp)
                    Text("Local IP: $localIp", fontSize = 13.sp)
                    Text("Port: 8022 (default Termux sshd port)", fontSize = 13.sp)
                    Text("Connect from another device: ssh $username@$localIp -p 8022", fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row {
                Button(onClick = { startServer() }, enabled = !isBusy && !isServerRunning, modifier = Modifier.weight(1f)) {
                    Text("Start")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = { stopServer() }, enabled = !isBusy && isServerRunning, modifier = Modifier.weight(1f)) {
                    Text("Stop")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text("SSH Key", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = { generateKey() }, enabled = !isBusy, modifier = Modifier.fillMaxWidth()) {
                Text("Generate / Show Public Key")
            }
            if (publicKeyText.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Text(publicKeyText, fontSize = 10.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(12.dp))
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = { copyPublicKey() }) { Text("Copy Public Key") }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text("Connect to a Remote Server (Client)", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(value = connectHost, onValueChange = { connectHost = it }, label = { Text("Host") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Spacer(modifier = Modifier.height(8.dp))
            Row {
                OutlinedTextField(value = connectUser, onValueChange = { connectUser = it }, label = { Text("User") }, modifier = Modifier.weight(1f), singleLine = true)
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedTextField(value = connectPort, onValueChange = { connectPort = it }, label = { Text("Port") }, modifier = Modifier.width(90.dp), singleLine = true)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = { connect() }, modifier = Modifier.fillMaxWidth()) {
                Text("Build Command & Open Terminal")
            }

            if (isBusy) {
                Spacer(modifier = Modifier.height(8.dp))
                HackLoader()
            }
            if (statusMessage.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(statusMessage, fontSize = 12.sp)
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
