package com.termux.app.easymux

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val PREFIX = "/data/data/com.termux/files/usr"
private const val HOME = "/data/data/com.termux/files/home"
private val SHARED_STORAGE = Environment.getExternalStorageDirectory().absolutePath
private val BACKUP_DIR = "$SHARED_STORAGE/EasyMuxBackups"

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

class BackupActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EasyMuxTheme {
                BackupScreen(onBack = { finish() })
            }
        }
    }
}

@Composable
fun BackupScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED ||
                Build.VERSION.SDK_INT >= 30
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
    }
    var statusMessage by remember { mutableStateOf("") }
    var isBusy by remember { mutableStateOf(false) }
    var backups by remember { mutableStateOf(listOf<File>()) }
    val scope = rememberCoroutineScope()

    fun refreshBackups() {
        val dir = File(BACKUP_DIR)
        backups = (dir.listFiles()?.toList() ?: listOf())
            .filter { it.name.endsWith(".tar.gz") }
            .sortedByDescending { it.lastModified() }
    }

    LaunchedEffect(Unit) { refreshBackups() }

    fun doBackup() {
        isBusy = true
        statusMessage = "Backing up..."
        scope.launch {
            withContext(Dispatchers.IO) { File(BACKUP_DIR).mkdirs() }
            val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            val fileName = "easymux-backup-$timestamp.tar.gz"
            val output = withContext(Dispatchers.IO) {
                runTermuxCommand("tar", "-czf", "$BACKUP_DIR/$fileName", "-C", "$PREFIX/..", "home")
            }
            statusMessage = if (File("$BACKUP_DIR/$fileName").exists()) {
                "Backup created: $fileName"
            } else {
                "Backup failed: ${output.takeLast(200)}"
            }
            isBusy = false
            refreshBackups()
        }
    }

    fun doRestore(file: File) {
        isBusy = true
        statusMessage = "Restoring ${file.name}..."
        scope.launch {
            val output = withContext(Dispatchers.IO) {
                runTermuxCommand("tar", "-xzf", file.absolutePath, "-C", "$PREFIX/..")
            }
            statusMessage = "Restore finished: ${file.name}\n${output.takeLast(200)}"
            isBusy = false
        }
    }

    HackScreen {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                }
                Text("Backup & Restore", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            Text(
                "Backs up your Termux home folder to Shared Storage > EasyMuxBackups",
                fontSize = 12.sp,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            if (!hasPermission) {
                Button(
                    onClick = { permissionLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Grant Storage Permission") }
                Spacer(modifier = Modifier.height(12.dp))
            }

            Button(
                onClick = { doBackup() },
                enabled = !isBusy,
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (isBusy) "Working..." else "Create Backup Now") }

            if (statusMessage.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(statusMessage, fontSize = 12.sp)
            }
            if (isBusy) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            Spacer(modifier = Modifier.height(20.dp))
            Text("Previous Backups", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(backups) { file ->
                    ElevatedCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(file.name, fontSize = 13.sp)
                                Text("${file.length() / (1024 * 1024)} MB", fontSize = 11.sp)
                            }
                            Button(onClick = { doRestore(file) }, enabled = !isBusy) {
                                Text("Restore")
                            }
                        }
                    }
                }
            }
        }
    }
}
