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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
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

data class PkgResult(val name: String, val description: String)

val pkgLineRegex = Regex("""^([a-zA-Z0-9][a-zA-Z0-9+.-]*)/\S+\s+\S+\s+\S+""")

class PackageManagerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EasyMuxTheme {
                PackageManagerScreen(onBack = { finish() })
            }
        }
    }
}

@Composable
fun PackageManagerScreen(onBack: () -> Unit) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Search", "Installed", "Mirror", "Repos")

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
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
                3 -> RepoTab()
            }
        }
    }
}

@Composable
fun SearchTab() {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(listOf<PkgResult>()) }
    var installedNames by remember { mutableStateOf(setOf<String>()) }
    var isLoading by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("") }
    var busyPackage by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    fun isPackageInstalled(name: String): Boolean {
        val output = runTermuxCommand("dpkg", "-s", name)
        return output.contains("Status: install ok installed")
    }

    fun doSearch() {
        if (query.isBlank()) return
        isLoading = true
        statusMessage = ""
        scope.launch {
            val output = withContext(Dispatchers.IO) { runTermuxCommand("pkg", "search", query) }
            val parsed = output.lines().mapNotNull { line ->
                val match = pkgLineRegex.find(line.trim())
                if (match != null) PkgResult(match.groupValues[1], line.trim()) else null
            }
            results = parsed
            installedNames = withContext(Dispatchers.IO) {
                parsed.filter { isPackageInstalled(it.name) }.map { it.name }.toSet()
            }
            isLoading = false
        }
    }

    fun doInstall(pkgName: String) {
        busyPackage = pkgName
        statusMessage = "Running: pkg install -y $pkgName"
        scope.launch {
            val output = withContext(Dispatchers.IO) { runTermuxCommand("pkg", "install", "-y", pkgName) }
            val nowInstalled = withContext(Dispatchers.IO) { isPackageInstalled(pkgName) }
            statusMessage = if (nowInstalled) {
                installedNames = installedNames + pkgName
                "$pkgName installed successfully"
            } else {
                "Failed: ${output.takeLast(200)}"
            }
            busyPackage = null
        }
    }

    fun doRun(pkgName: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("EasyMux", pkgName))
        statusMessage = "Command \"$pkgName\" copied — paste it in the Terminal that just opened (add any arguments you need)"
        context.startActivity(Intent(context, TermuxActivity::class.java))
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
                val isInstalled = installedNames.contains(pkg.name)
                ElevatedCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(pkg.name, fontWeight = FontWeight.Medium)
                            Text(pkg.description, fontSize = 12.sp, maxLines = 2)
                            Text(if (isInstalled) "Installed" else "Not installed", fontSize = 11.sp)
                        }
                        if (isInstalled) {
                            TextButton(onClick = { doRun(pkg.name) }) { Text("Run") }
                        } else {
                            Button(onClick = { doInstall(pkg.name) }, enabled = busyPackage == null) {
                                Text(if (busyPackage == pkg.name) "..." else "Install")
                            }
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    fun runInstalled(name: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("EasyMux", name))
        statusMessage = "Command \"$name\" copied — paste it in the Terminal that just opened"
        context.startActivity(Intent(context, TermuxActivity::class.java))
    }

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
                        TextButton(onClick = { runInstalled(pkg.name) }) {
                            Text("Run")
                        }
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

data class RepoOption(val displayName: String, val packageName: String, val description: String)

@Composable
fun RepoTab() {
    val repos = listOf(
        RepoOption("Root Repo", "root-repo", "Packages that need root access"),
        RepoOption("X11 Repo", "x11-repo", "Graphical Linux apps (X11 desktop)"),
        RepoOption("Science Repo", "science-repo", "Scientific computing packages"),
        RepoOption("Game Repo", "game-repo", "Games and game-related tools"),
        RepoOption("TUR Repo", "tur-repo", "Termux User Repository (extra packages)"),
        RepoOption("Unstable Repo", "unstable-repo", "Newer, less-tested package versions")
    )
    var enabledRepos by remember { mutableStateOf(setOf<String>()) }
    var busyRepo by remember { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    fun refresh() {
        scope.launch {
            enabledRepos = withContext(Dispatchers.IO) {
                repos.filter { repo ->
                    File("$PREFIX/etc/apt/sources.list.d").listFiles()
                        ?.any { it.name.contains(repo.packageName.removeSuffix("-repo"), ignoreCase = true) } == true
                }.map { it.packageName }.toSet()
            }
        }
    }

    LaunchedEffect(Unit) { refresh() }

    fun enableRepo(repo: RepoOption) {
        busyRepo = repo.packageName
        statusMessage = "Running: pkg install -y ${repo.packageName}"
        scope.launch {
            withContext(Dispatchers.IO) { runTermuxCommand("pkg", "install", "-y", repo.packageName) }
            statusMessage = "${repo.displayName} enabled"
            busyRepo = null
            refresh()
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Enable extra package repositories:", fontSize = 14.sp)
        Spacer(modifier = Modifier.height(12.dp))
        if (statusMessage.isNotBlank()) {
            Text(statusMessage, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(12.dp))
        }
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(repos) { repo ->
                val isEnabled = enabledRepos.contains(repo.packageName)
                ElevatedCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(repo.displayName, fontWeight = FontWeight.Medium)
                            Text(repo.description, fontSize = 12.sp)
                        }
                        if (isEnabled) {
                            Text("Enabled", fontSize = 12.sp)
                        } else {
                            Button(onClick = { enableRepo(repo) }, enabled = busyRepo == null) {
                                Text(if (busyRepo == repo.packageName) "..." else "Enable")
                            }
                        }
                    }
                }
            }
        }
    }
}
