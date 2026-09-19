package com.termux.app.easymux

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
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

data class PkgResult(val name: String, val description: String)

val pkgLineRegex = Regex("""^([a-zA-Z0-9][a-zA-Z0-9+.-]*)/\S+\s+\S+\s+\S+""")

class PackageManagerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                PackageManagerScreen(onBack = { finish() })
            }
        }
    }
}

@Composable
fun PackageManagerScreen(onBack: () -> Unit) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Search", "Installed", "Mirror")

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                }
                Text("Package Manager", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }
            when (selectedTab) {
                0 -> SearchTab()
                1 -> InstalledTab()
                2 -> MirrorTab()
            }
        }
    }
}

@Composable
fun SearchTab() {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(listOf<PkgResult>()) }
    var isLoading by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("") }
    var busyPackage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun doSearch() {
        if (query.isBlank()) return
        isLoading = true
        statusMessage = ""
        scope.launch {
            val output = withContext(Dispatchers.IO) { runTermuxCommand("pkg", "search", query) }
            results = output.lines().mapNotNull { line ->
                val match = pkgLineRegex.find(line.trim())
                if (match != null) PkgResult(match.groupValues[1], line.trim()) else null
            }
            isLoading = false
        }
    }

    fun doInstall(pkgName: String) {
        busyPackage = pkgName
        statusMessage = "Installing $pkgName..."
        scope.launch {
            val output = withContext(Dispatchers.IO) { runTermuxCommand("pkg", "install", "-y", pkgName) }
            statusMessage = if (output.contains("E:", ignoreCase = false)) {
                "Failed: ${output.takeLast(200)}"
            } else {
                "$pkgName installed successfully"
            }
            busyPackage = null
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search packages") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(onClick = { doSearch() }) {
                Icon(Icons.Filled.Search, contentDescription = "Search")
            }
        }
        if (statusMessage.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(statusMessage, fontSize = 13.sp)
        }
        Spacer(modifier = Modifier.height(12.dp))
        if (isLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(results) { pkg ->
                ElevatedCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(pkg.name, fontWeight = FontWeight.Medium)
                            Text(pkg.description, fontSize = 12.sp, maxLines = 2)
                        }
                        Button(onClick = { doInstall(pkg.name) }, enabled = busyPackage == null) {
                            Text(if (busyPackage == pkg.name) "..." else "Install")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InstalledTab() {
    var installed by remember { mutableStateOf(listOf<PkgResult>()) }
    var isLoading by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("") }
    var busyPackage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun refresh() {
        isLoading = true
        scope.launch {
            val output = withContext(Dispatchers.IO) { runTermuxCommand("pkg", "list-installed") }
            installed = output.lines().mapNotNull { line ->
                val match = pkgLineRegex.find(line.trim())
                if (match != null) PkgResult(match.groupValues[1], line.trim()) else null
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { refresh() }

    fun updateOne(name: String) {
        busyPackage = name
        statusMessage = "Updating $name..."
        scope.launch {
            withContext(Dispatchers.IO) { runTermuxCommand("apt", "install", "--only-upgrade", "-y", name) }
            statusMessage = "$name updated"
            busyPackage = null
            refresh()
        }
    }

    fun uninstallOne(name: String) {
        busyPackage = name
        statusMessage = "Removing $name..."
        scope.launch {
            withContext(Dispatchers.IO) { runTermuxCommand("pkg", "uninstall", "-y", name) }
            statusMessage = "$name removed"
            busyPackage = null
            refresh()
        }
    }

    fun updateAll() {
        busyPackage = "__all__"
        statusMessage = "Updating all packages..."
        scope.launch {
            withContext(Dispatchers.IO) { runTermuxCommand("pkg", "upgrade", "-y") }
            statusMessage = "All packages updated"
            busyPackage = null
            refresh()
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Button(
            onClick = { updateAll() },
            enabled = busyPackage == null,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (busyPackage == "__all__") "Updating all..." else "Update All")
        }
        if (statusMessage.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(statusMessage, fontSize = 13.sp)
        }
        Spacer(modifier = Modifier.height(12.dp))
        if (isLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(installed) { pkg ->
                ElevatedCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(pkg.name, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                        TextButton(onClick = { updateOne(pkg.name) }, enabled = busyPackage == null) {
                            Text("Update")
                        }
                        TextButton(onClick = { uninstallOne(pkg.name) }, enabled = busyPackage == null) {
                            Text("Remove")
                        }
                    }
                }
            }
        }
    }
}

data class MirrorOption(val label: String, val url: String)

@Composable
fun MirrorTab() {
    val mirrors = listOf(
        MirrorOption("Default (packages.termux.dev)", "https://packages.termux.dev/apt/termux-main"),
        MirrorOption("Grimler mirror", "https://grimler.se/termux-main")
    )
    var statusMessage by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun applyMirror(mirror: MirrorOption) {
        busy = true
        statusMessage = "Switching to ${mirror.label}..."
        scope.launch {
            withContext(Dispatchers.IO) {
                val sourcesFile = File("$PREFIX/etc/apt/sources.list")
                sourcesFile.writeText("deb ${mirror.url} stable main\n")
                runTermuxCommand("apt", "update")
            }
            statusMessage = "Switched to ${mirror.label} and refreshed package list"
            busy = false
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Choose a package mirror if downloads are slow or failing:", fontSize = 14.sp)
        Spacer(modifier = Modifier.height(12.dp))
        if (statusMessage.isNotBlank()) {
            Text(statusMessage, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(12.dp))
        }
        if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        mirrors.forEach { mirror ->
            ElevatedCard(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                onClick = { if (!busy) applyMirror(mirror) }
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(mirror.label, fontWeight = FontWeight.Medium)
                    Text(mirror.url, fontSize = 12.sp)
                }
            }
        }
    }
}
