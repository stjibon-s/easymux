package com.termux.app.easymux

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import java.io.File

private const val HOME = "/data/data/com.termux/files/home"
private const val SCRIPTS_DIR = "$HOME/.shortcuts"

class BootConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EasyMuxTheme {
                BootConfigScreen(onBack = { finish() })
            }
        }
    }
}

@Composable
fun BootConfigScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(BOOT_PREFS_NAME, Context.MODE_PRIVATE) }
    var selectedScript by remember { mutableStateOf(prefs.getString(BOOT_SCRIPT_KEY, null)) }
    var scripts by remember { mutableStateOf(listOf<File>()) }

    LaunchedEffect(Unit) {
        File(SCRIPTS_DIR).mkdirs()
        scripts = (File(SCRIPTS_DIR).listFiles()?.toList() ?: listOf())
            .filter { it.isFile }
            .sortedBy { it.name.lowercase() }
    }

    fun setBootScript(path: String?) {
        prefs.edit().apply {
            if (path == null) remove(BOOT_SCRIPT_KEY) else putString(BOOT_SCRIPT_KEY, path)
        }.apply()
        selectedScript = path
    }

    HackScreen {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                }
                Text("Auto-run on Boot", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            Text(
                "Choose one script (from Scripts) to run automatically when your phone starts.",
                fontSize = 12.sp,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            if (selectedScript != null) {
                ElevatedCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Currently set:", fontSize = 11.sp)
                            Text(File(selectedScript!!).name, fontWeight = FontWeight.Medium)
                        }
                        TextButton(onClick = { setBootScript(null) }) { Text("Clear") }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (scripts.isEmpty()) {
                Text("No scripts found. Create one in Scripts first.", fontSize = 13.sp)
            }

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(scripts) { file ->
                    val isSelected = selectedScript == file.absolutePath
                    ElevatedCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(file.name, modifier = Modifier.weight(1f))
                            Button(
                                onClick = { setBootScript(file.absolutePath) },
                                enabled = !isSelected
                            ) {
                                Text(if (isSelected) "Selected" else "Use this")
                            }
                        }
                    }
                }
            }
        }
    }
}
