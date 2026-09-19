package com.termux.app.easymux

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
private const val SCRIPTS_DIR = "$HOME/.shortcuts"

private fun runScript(path: String): String {
    return try {
        val pb = ProcessBuilder(listOf("$PREFIX/bin/bash", path))
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

class ScriptsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        File(SCRIPTS_DIR).mkdirs()
        setContent {
            MaterialTheme {
                ScriptsScreen(onBack = { finish() })
            }
        }
    }
}

@Composable
fun ScriptsScreen(onBack: () -> Unit) {
    var scripts by remember { mutableStateOf(listOf<File>()) }
    var showNewDialog by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<File?>(null) }
    var deleteTarget by remember { mutableStateOf<File?>(null) }
    var scriptName by remember { mutableStateOf("") }
    var scriptContent by remember { mutableStateOf("") }
    var runningOutput by remember { mutableStateOf<String?>(null) }
    var runningName by remember { mutableStateOf("") }
    var isRunning by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun refresh() {
        scripts = (File(SCRIPTS_DIR).listFiles()?.toList() ?: listOf())
            .filter { it.isFile }
            .sortedBy { it.name.lowercase() }
    }

    LaunchedEffect(Unit) { refresh() }

    fun runScriptFile(file: File) {
        isRunning = true
        runningName = file.name
        runningOutput = null
        scope.launch {
            val output = withContext(Dispatchers.IO) { runScript(file.absolutePath) }
            runningOutput = output
            isRunning = false
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
                Text("Scripts", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = {
                    scriptName = ""
                    scriptContent = "#!/data/data/com.termux/files/usr/bin/bash\n\n"
                    showNewDialog = true
                }) {
                    Icon(Icons.Filled.Add, contentDescription = "New Script")
                }
            }

            LazyColumn(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
                items(scripts) { file ->
                    ElevatedCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Description, contentDescription = null)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(file.name, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                            IconButton(onClick = {
                                editTarget = file
                                scriptName = file.name
                                scriptContent = file.readText()
                            }) {
                                Icon(Icons.Filled.Edit, contentDescription = "Edit")
                            }
                            IconButton(onClick = { deleteTarget = file }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete")
                            }
                            Button(onClick = { runScriptFile(file) }, enabled = !isRunning) {
                                Text("Run")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showNewDialog || editTarget != null) {
        AlertDialog(
            onDismissRequest = { showNewDialog = false; editTarget = null },
            title = { Text(if (editTarget != null) "Edit Script" else "New Script") },
            text = {
                Column {
                    OutlinedTextField(
                        value = scriptName,
                        onValueChange = { scriptName = it },
                        label = { Text("Script name (e.g. backup.sh)") },
                        enabled = editTarget == null,
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = scriptContent,
                        onValueChange = { scriptContent = it },
                        label = { Text("Script content") },
                        modifier = Modifier.fillMaxWidth().height(200.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val targetFile = editTarget ?: File(SCRIPTS_DIR, scriptName)
                    if (scriptName.isNotBlank()) {
                        targetFile.writeText(scriptContent)
                        targetFile.setExecutable(true)
                        refresh()
                    }
                    showNewDialog = false
                    editTarget = null
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showNewDialog = false; editTarget = null }) { Text("Cancel") }
            }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete Script") },
            text = { Text("Delete \"${target.name}\"?") },
            confirmButton = {
                TextButton(onClick = {
                    target.delete()
                    refresh()
                    deleteTarget = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            }
        )
    }

    if (isRunning || runningOutput != null) {
        AlertDialog(
            onDismissRequest = { if (!isRunning) runningOutput = null },
            title = { Text("Output: $runningName") },
            text = {
                if (isRunning) {
                    Column {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Running...")
                    }
                } else {
                    Text(runningOutput ?: "", fontSize = 12.sp)
                }
            },
            confirmButton = {
                if (!isRunning) {
                    TextButton(onClick = { runningOutput = null }) { Text("Close") }
                }
            }
        )
    }
}
