package com.termux.app.easymux

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
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
        val out = reader.readText()
        process.waitFor()
        out
    } catch (e: Exception) {
        "Error: ${e.message}"
    }
}

class CronActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EasyMuxTheme {
                CronScreen(onBack = { finish() })
            }
        }
    }
}

@Composable
fun CronScreen(onBack: () -> Unit) {
    var isCronieInstalled by remember { mutableStateOf(true) }
    var isCrondRunning by remember { mutableStateOf(false) }
    var jobs by remember { mutableStateOf(listOf<String>()) }
    var showAddDialog by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("") }
    var isBusy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun refresh() {
        scope.launch {
            isCronieInstalled = withContext(Dispatchers.IO) { File("$PREFIX/bin/crond").exists() }
            if (isCronieInstalled) {
                val pgrep = withContext(Dispatchers.IO) { runTermuxCommand("pgrep", "crond") }
                isCrondRunning = pgrep.trim().isNotBlank()
                val list = withContext(Dispatchers.IO) { runTermuxCommand("crontab", "-l") }
                jobs = list.lines().filter { it.isNotBlank() && !it.trim().startsWith("#") }
            }
        }
    }

    LaunchedEffect(Unit) { refresh() }

    fun setupCron() {
        isBusy = true
        statusMessage = "Running: pkg install -y cronie && crond"
        scope.launch {
            withContext(Dispatchers.IO) { runTermuxCommand("pkg", "install", "-y", "cronie") }
            withContext(Dispatchers.IO) { runTermuxCommand("crond") }
            statusMessage = "cron is set up and running"
            isBusy = false
            refresh()
        }
    }

    fun addJob(schedule: String, command: String) {
        isBusy = true
        scope.launch {
            withContext(Dispatchers.IO) {
                runTermuxCommand("(crontab -l 2>/dev/null; echo \"$schedule $command\") | crontab -")
            }
            statusMessage = "Task added"
            isBusy = false
            refresh()
        }
    }

    fun removeJob(line: String) {
        isBusy = true
        scope.launch {
            withContext(Dispatchers.IO) {
                val escaped = line.replace("\"", "\\\"")
                runTermuxCommand("crontab -l | grep -vF \"$escaped\" | crontab -")
            }
            statusMessage = "Task removed"
            isBusy = false
            refresh()
        }
    }

    HackScreen {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                }
                Text("Scheduled Tasks", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (isCronieInstalled) {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Filled.Add, contentDescription = "Add Task")
                    }
                }
            }

            if (!isCronieInstalled) {
                Text("Cron is not set up yet.", fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp))
                Button(onClick = { setupCron() }, enabled = !isBusy, modifier = Modifier.fillMaxWidth()) {
                    Text("Set Up Cron")
                }
            } else {
                Text(
                    "crond is ${if (isCrondRunning) "running" else "not running"}. Note: crond stops when Termux/EasyMux is fully closed unless run via a boot script.",
                    fontSize = 11.sp,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                if (!isCrondRunning) {
                    Button(onClick = { setupCron() }, enabled = !isBusy, modifier = Modifier.fillMaxWidth()) {
                        Text("Start crond")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            if (isBusy) {
                Spacer(modifier = Modifier.height(8.dp))
                HackLoader()
            }
            if (statusMessage.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(statusMessage, fontSize = 12.sp)
            }

            Spacer(modifier = Modifier.height(12.dp))
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(jobs) { job ->
                    ElevatedCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(job, fontFamily = FontFamily.Monospace, fontSize = 12.sp, modifier = Modifier.weight(1f))
                            TextButton(onClick = { removeJob(job) }) { Text("Remove") }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddCronDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { schedule, command ->
                addJob(schedule, command)
                showAddDialog = false
            }
        )
    }
}

@Composable
fun AddCronDialog(onDismiss: () -> Unit, onAdd: (String, String) -> Unit) {
    var schedule by remember { mutableStateOf("0 * * * *") }
    var command by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Scheduled Task") },
        text = {
            Column {
                OutlinedTextField(
                    value = schedule,
                    onValueChange = { schedule = it },
                    label = { Text("Schedule (cron format)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Text("e.g. \"0 * * * *\" = every hour, \"0 0 * * *\" = daily at midnight", fontSize = 10.sp)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = command,
                    onValueChange = { command = it },
                    label = { Text("Command or script path") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (schedule.isNotBlank() && command.isNotBlank()) onAdd(schedule, command)
            }) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
