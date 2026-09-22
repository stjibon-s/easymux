package com.termux.app.easymux

import android.content.Context
import android.net.wifi.WifiManager
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

private fun ensureInstalled(binaryName: String, packageName: String): String {
    val bin = File("$PREFIX/bin/$binaryName")
    return if (!bin.exists()) {
        runTermuxCommand("pkg", "install", "-y", packageName)
    } else ""
}

class NetworkToolsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EasyMuxTheme {
                NetworkToolsScreen(onBack = { finish() })
            }
        }
    }
}

@Composable
fun NetworkToolsScreen(onBack: () -> Unit) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Ping", "Port", "DNS", "Traceroute", "IP Info", "HTTP Test")

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                }
                Text("Network Tools", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            ScrollableTabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(selected = selectedTab == index, onClick = { selectedTab = index }, text = { Text(title) })
                }
            }
            when (selectedTab) {
                0 -> PingTab()
                1 -> PortTab()
                2 -> DnsTab()
                3 -> TracerouteTab()
                4 -> IpInfoTab()
                5 -> HttpTestTab()
            }
        }
    }
}

@Composable
fun OutputBlock(command: String, output: String) {
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
fun PingTab() {
    var host by remember { mutableStateOf("google.com") }
    var output by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = host, onValueChange = { host = it }, label = { Text("Host") }, modifier = Modifier.weight(1f), singleLine = true)
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = {
                isLoading = true
                scope.launch {
                    output = withContext(Dispatchers.IO) { runTermuxCommand("ping", "-c", "4", host) }
                    isLoading = false
                }
            }, enabled = !isLoading) { Text("Ping") }
        }
        if (isLoading) { Spacer(modifier = Modifier.height(8.dp)); LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
        OutputBlock("ping -c 4 $host", output)
    }
}

@Composable
fun PortTab() {
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = host, onValueChange = { host = it }, label = { Text("Host") }, modifier = Modifier.weight(1f), singleLine = true)
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedTextField(value = port, onValueChange = { port = it }, label = { Text("Port") }, modifier = Modifier.width(90.dp), singleLine = true)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = {
            if (host.isBlank() || port.isBlank()) return@Button
            isLoading = true
            scope.launch {
                output = withContext(Dispatchers.IO) { runTermuxCommand("nc", "-zv", "-w", "3", host, port) }
                if (output.isBlank()) output = "No response (port likely closed or host unreachable)"
                isLoading = false
            }
        }, enabled = !isLoading, modifier = Modifier.fillMaxWidth()) { Text("Check Port") }
        if (isLoading) { Spacer(modifier = Modifier.height(8.dp)); LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
        OutputBlock("nc -zv -w 3 $host $port", output)
    }
}

@Composable
fun DnsTab() {
    var host by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("DNS Lookup", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = host, onValueChange = { host = it }, label = { Text("Domain (e.g. google.com)") }, modifier = Modifier.weight(1f), singleLine = true)
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = {
                if (host.isBlank()) return@Button
                isLoading = true
                scope.launch {
                    withContext(Dispatchers.IO) { ensureInstalled("dig", "dnsutils") }
                    output = withContext(Dispatchers.IO) { runTermuxCommand("dig", host, "any", "+noall", "+answer") }
                    if (output.isBlank()) output = "No DNS records found"
                    isLoading = false
                }
            }, enabled = !isLoading) { Text("Lookup") }
        }
        if (isLoading) { Spacer(modifier = Modifier.height(8.dp)); LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
        OutputBlock("dig $host any +noall +answer", output)
    }
}

@Composable
fun TracerouteTab() {
    var host by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Traceroute", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = host, onValueChange = { host = it }, label = { Text("Host") }, modifier = Modifier.weight(1f), singleLine = true)
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = {
                if (host.isBlank()) return@Button
                isLoading = true
                scope.launch {
                    withContext(Dispatchers.IO) { ensureInstalled("traceroute", "traceroute") }
                    output = withContext(Dispatchers.IO) { runTermuxCommand("timeout", "25", "traceroute", "-m", "15", host) }
                    isLoading = false
                }
            }, enabled = !isLoading) { Text("Trace") }
        }
        Text("May take up to 25 seconds", fontSize = 11.sp)
        if (isLoading) { Spacer(modifier = Modifier.height(8.dp)); LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
        OutputBlock("traceroute -m 15 $host", output)
    }
}

@Composable
fun IpInfoTab() {
    val context = LocalContext.current
    var localIp by remember { mutableStateOf("Loading...") }
    var publicIpOutput by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        localIp = try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val ip = wifiManager.connectionInfo?.ipAddress ?: 0
            if (ip != 0) String.format("%d.%d.%d.%d", ip and 0xff, ip shr 8 and 0xff, ip shr 16 and 0xff, ip shr 24 and 0xff)
            else "Not connected to WiFi"
        } catch (e: Exception) {
            "Unavailable (${e.message})"
        }
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("IP Information", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Text("Local WiFi IP: $localIp", fontSize = 13.sp, modifier = Modifier.padding(12.dp))
        }
        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = {
            isLoading = true
            scope.launch {
                publicIpOutput = try {
                    withContext(Dispatchers.IO) { runTermuxCommand("curl", "-s", "ipinfo.io") }
                } catch (e: Exception) {
                    "Error: ${e.message}"
                }
                if (publicIpOutput.isBlank()) publicIpOutput = "Could not fetch (no internet or service unavailable)"
                isLoading = false
            }
        }, enabled = !isLoading, modifier = Modifier.fillMaxWidth()) { Text("Get Public IP Info") }
        if (isLoading) { Spacer(modifier = Modifier.height(8.dp)); LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
        OutputBlock("curl -s ipinfo.io", publicIpOutput)
    }
}

@Composable
fun HttpTestTab() {
    var url by remember { mutableStateOf("https://") }
    var output by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("HTTP Test", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("URL") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = {
            if (url.isBlank()) return@Button
            isLoading = true
            scope.launch {
                output = withContext(Dispatchers.IO) {
                    runTermuxCommand("curl", "-s", "-o", "/dev/null", "-w", "'HTTP %{http_code} - %{time_total}s response time'", url)
                }
                isLoading = false
            }
        }, enabled = !isLoading, modifier = Modifier.fillMaxWidth()) { Text("Test") }
        if (isLoading) { Spacer(modifier = Modifier.height(8.dp)); LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
        OutputBlock("curl -s -o /dev/null -w ... $url", output)
    }
}
