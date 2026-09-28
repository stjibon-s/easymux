package com.termux.app.easymux

import android.content.Intent
import android.content.pm.ShortcutInfo
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.widget.Toast
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
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import java.io.File

private const val HOME = "/data/data/com.termux/files/home"
private const val SCRIPTS_DIR = "$HOME/.shortcuts"

class WidgetCreatorActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EasyMuxTheme {
                WidgetCreatorScreen(onBack = { finish() })
            }
        }
    }
}

@Composable
fun WidgetCreatorScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var scripts by remember { mutableStateOf(listOf<File>()) }
    var statusMessage by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        File(SCRIPTS_DIR).mkdirs()
        scripts = (File(SCRIPTS_DIR).listFiles()?.toList() ?: listOf())
            .filter { it.isFile }
            .sortedBy { it.name.lowercase() }
    }

    fun pinShortcut(file: File) {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
            statusMessage = "Your launcher doesn't support pinning shortcuts"
            return
        }
        val intent = Intent(context, ShortcutRunnerActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra("script_path", file.absolutePath)
        }
        val shortcut = ShortcutInfoCompat.Builder(context, "script_${file.name}")
            .setShortLabel(file.name)
            .setIcon(IconCompat.createWithResource(context, android.R.drawable.ic_menu_send))
            .setIntent(intent)
            .build()
        val pinnedSuccessfully = ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
        statusMessage = if (pinnedSuccessfully) "Pin request sent — confirm on your home screen" else "Could not request pin"
    }

    HackScreen {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                }
                Text("Home Screen Shortcuts", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            Text(
                "Pin any saved script as a one-tap shortcut on your phone's home screen.",
                fontSize = 12.sp,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            if (statusMessage.isNotBlank()) {
                Text(statusMessage, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
            }
            if (scripts.isEmpty()) {
                Text("No scripts found. Create one in Scripts first.", fontSize = 13.sp)
            }
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(scripts) { file ->
                    ElevatedCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(file.name, modifier = Modifier.weight(1f))
                            Button(onClick = { pinShortcut(file) }) {
                                Text("Pin to Home")
                            }
                        }
                    }
                }
            }
        }
    }
}
