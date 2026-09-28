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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import java.security.MessageDigest

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

private fun ensureInstalled(binaryName: String, packageName: String): String {
    val bin = File("$PREFIX/bin/$binaryName")
    return if (!bin.exists()) {
        runTermuxCommand("pkg", "install", "-y", packageName)
    } else ""
}

class SecurityToolsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EasyMuxTheme {
                SecurityToolsScreen(onBack = { finish() })
            }
        }
    }
}

@Composable
fun SecurityToolsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Recon", "Utilities", "Attack Builder")

    HackScreen {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                }
                Text("Security Tools", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            }
            Text(
                "Only use these on systems and networks you own or are authorized to test.",
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            TextButton(
                onClick = { context.startActivity(Intent(context, ScriptsActivity::class.java)) },
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                Text("Build Your Own Tool (opens Scripts)")
            }
            ScrollableTabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(selected = selectedTab == index, onClick = { selectedTab = index }, text = { Text(title) })
                }
            }
            when (selectedTab) {
                0 -> ReconTab()
                1 -> UtilitiesTab()
                2 -> AttackBuilderTab()
            }
        }
    }
}

@Composable
fun ReconOutputBlock(command: String, output: String) {
    if (output.isNotBlank()) {
        Spacer(modifier = Modifier.height(8.dp))
        Text("Command: $command", fontSize = 11.sp)
        Spacer(modifier = Modifier.height(4.dp))
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Text(output, fontSize = 12.sp, modifier = Modifier.padding(12.dp))
        }
    }
}

@Composable
fun ReconTab() {
    var target by remember { mutableStateOf("") }
    var ports by remember { mutableStateOf("1-1000") }
    var nmapOutput by remember { mutableStateOf("") }
    var isNmapLoading by remember { mutableStateOf(false) }

    var whatwebUrl by remember { mutableStateOf("") }
    var whatwebOutput by remember { mutableStateOf("") }
    var isWhatwebLoading by remember { mutableStateOf(false) }

    var niktoUrl by remember { mutableStateOf("") }
    var niktoOutput by remember { mutableStateOf("") }
    var isNiktoLoading by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Nmap — Port Scan", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = target, onValueChange = { target = it }, label = { Text("Target (IP or host)") }, modifier = Modifier.weight(1f), singleLine = true)
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedTextField(value = ports, onValueChange = { ports = it }, label = { Text("Ports") }, modifier = Modifier.width(100.dp), singleLine = true)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = {
            if (target.isBlank()) return@Button
            isNmapLoading = true
            scope.launch {
                withContext(Dispatchers.IO) { ensureInstalled("nmap", "nmap") }
                nmapOutput = withContext(Dispatchers.IO) { runTermuxCommand("nmap", "-p", ports, target) }
                isNmapLoading = false
            }
        }, enabled = !isNmapLoading, modifier = Modifier.fillMaxWidth()) { Text("Scan") }
        if (isNmapLoading) { Spacer(modifier = Modifier.height(8.dp)); HackLoader() }
        ReconOutputBlock("nmap -p $ports $target", nmapOutput)

        Spacer(modifier = Modifier.height(28.dp))
        Text("WhatWeb — Website Fingerprint", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = whatwebUrl, onValueChange = { whatwebUrl = it }, label = { Text("URL") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = {
            if (whatwebUrl.isBlank()) return@Button
            isWhatwebLoading = true
            scope.launch {
                withContext(Dispatchers.IO) { ensureInstalled("whatweb", "whatweb") }
                whatwebOutput = withContext(Dispatchers.IO) { runTermuxCommand("whatweb", whatwebUrl) }
                isWhatwebLoading = false
            }
        }, enabled = !isWhatwebLoading, modifier = Modifier.fillMaxWidth()) { Text("Identify") }
        if (isWhatwebLoading) { Spacer(modifier = Modifier.height(8.dp)); HackLoader() }
        ReconOutputBlock("whatweb $whatwebUrl", whatwebOutput)

        Spacer(modifier = Modifier.height(28.dp))
        Text("Nikto — Web Server Scan", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = niktoUrl, onValueChange = { niktoUrl = it }, label = { Text("URL") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(modifier = Modifier.height(8.dp))
        Text("This can take a few minutes", fontSize = 11.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Button(onClick = {
            if (niktoUrl.isBlank()) return@Button
            isNiktoLoading = true
            scope.launch {
                withContext(Dispatchers.IO) { ensureInstalled("nikto", "nikto") }
                niktoOutput = withContext(Dispatchers.IO) { runTermuxCommand("timeout", "120", "nikto", "-h", niktoUrl) }
                isNiktoLoading = false
            }
        }, enabled = !isNiktoLoading, modifier = Modifier.fillMaxWidth()) { Text("Scan") }
        if (isNiktoLoading) { Spacer(modifier = Modifier.height(8.dp)); HackLoader() }
        ReconOutputBlock("nikto -h $niktoUrl", niktoOutput)
        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
fun UtilitiesTab() {
    val context = LocalContext.current
    var hashInput by remember { mutableStateOf("") }
    var hashResults by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    var passwordInput by remember { mutableStateOf("") }
    var strengthResult by remember { mutableStateOf("") }

    fun computeHash(algorithm: String, text: String): String {
        val digest = MessageDigest.getInstance(algorithm)
        val bytes = digest.digest(text.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun checkPasswordStrength(pw: String): String {
        if (pw.isEmpty()) return "Enter a password"
        var score = 0
        if (pw.length >= 8) score++
        if (pw.length >= 12) score++
        if (pw.any { it.isUpperCase() }) score++
        if (pw.any { it.isLowerCase() }) score++
        if (pw.any { it.isDigit() }) score++
        if (pw.any { !it.isLetterOrDigit() }) score++
        return when {
            score <= 2 -> "Weak"
            score <= 4 -> "Moderate"
            else -> "Strong"
        } + " (score $score/6)"
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Hash Generator", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = hashInput, onValueChange = { hashInput = it }, label = { Text("Text") }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = {
            hashResults = mapOf(
                "MD5" to computeHash("MD5", hashInput),
                "SHA-1" to computeHash("SHA-1", hashInput),
                "SHA-256" to computeHash("SHA-256", hashInput)
            )
        }, modifier = Modifier.fillMaxWidth()) { Text("Generate Hashes") }
        hashResults.forEach { (algo, value) ->
            Spacer(modifier = Modifier.height(8.dp))
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(algo, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                    Text(value, fontSize = 11.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))
        Text("Password Strength Checker", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = passwordInput,
            onValueChange = { passwordInput = it; strengthResult = checkPasswordStrength(it) },
            label = { Text("Password") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        if (strengthResult.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(strengthResult, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
fun AttackBuilderTab() {
    val context = LocalContext.current

    fun copyAndOpenTerminal(command: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("EasyMux", command))
        context.startActivity(Intent(context, TermuxActivity::class.java))
    }

    var hydraTarget by remember { mutableStateOf("") }
    var hydraService by remember { mutableStateOf("ssh") }
    var hydraUser by remember { mutableStateOf("") }
    var hydraWordlist by remember { mutableStateOf("$HOME/wordlist.txt") }
    var hydraStatus by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text(
            "These tools can only build a command for you to review. You must paste and run it yourself in the Terminal — nothing runs automatically.",
            fontSize = 12.sp
        )
        Spacer(modifier = Modifier.height(20.dp))

        Text("Hydra — Login Brute-Force", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = hydraTarget, onValueChange = { hydraTarget = it }, label = { Text("Target host") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = hydraService, onValueChange = { hydraService = it }, label = { Text("Service (ssh, ftp, http-post-form...)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = hydraUser, onValueChange = { hydraUser = it }, label = { Text("Username") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = hydraWordlist, onValueChange = { hydraWordlist = it }, label = { Text("Password wordlist path") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = {
            if (hydraTarget.isBlank() || hydraUser.isBlank()) return@Button
            val cmd = "pkg install -y hydra && hydra -l $hydraUser -P $hydraWordlist $hydraService://$hydraTarget"
            hydraStatus = "Command copied — paste it in the Terminal that just opened"
            copyAndOpenTerminal(cmd)
        }, modifier = Modifier.fillMaxWidth()) { Text("Build Command & Open Terminal") }
        if (hydraStatus.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(hydraStatus, fontSize = 12.sp)
        }

        Spacer(modifier = Modifier.height(28.dp))
        Text("Aircrack-ng — WiFi Security Audit", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Requires an external WiFi adapter that supports monitor mode — most phone WiFi chips don't. Install and read the usage guide first:",
            fontSize = 12.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = {
            copyAndOpenTerminal("pkg install -y aircrack-ng && aircrack-ng --help")
        }, modifier = Modifier.fillMaxWidth()) { Text("Install & Show Usage") }

        Spacer(modifier = Modifier.height(28.dp))
        Text("Metasploit Framework", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))
        Text("A full interactive console, not a single command — install it and launch msfconsole yourself:", fontSize = 12.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = {
            copyAndOpenTerminal("pkg install -y unstable-repo && pkg install -y metasploit && msfconsole")
        }, modifier = Modifier.fillMaxWidth()) { Text("Build Command & Open Terminal") }
        Spacer(modifier = Modifier.height(20.dp))
    }
}
