package com.termux.app.easymux

import android.content.Context
import android.content.Intent
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

const val SETTINGS_PREFS_NAME = "easymux_settings"
const val PREF_MATRIX_RAIN_ENABLED = "matrix_rain_enabled"
const val PREF_POPUP_TERMINAL_ENABLED = "popup_terminal_enabled"

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EasyMuxTheme {
                SettingsScreen(onBack = { finish() })
            }
        }
    }
}

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(SETTINGS_PREFS_NAME, Context.MODE_PRIVATE) }
    var matrixEnabled by remember { mutableStateOf(prefs.getBoolean(PREF_MATRIX_RAIN_ENABLED, true)) }
    var popupTerminalEnabled by remember { mutableStateOf(prefs.getBoolean(PREF_POPUP_TERMINAL_ENABLED, true)) }

    fun setBool(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    HackScreen {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                }
                Text("Settings", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(12.dp))

            ElevatedCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Matrix Rain Background", fontWeight = FontWeight.Medium)
                        Text("Animated background effect on every screen", fontSize = 12.sp)
                    }
                    Switch(checked = matrixEnabled, onCheckedChange = {
                        matrixEnabled = it
                        setBool(PREF_MATRIX_RAIN_ENABLED, it)
                    })
                }
            }

            ElevatedCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Popup Terminal Button", fontWeight = FontWeight.Medium)
                        Text("Floating quick-command button on every screen", fontSize = 12.sp)
                    }
                    Switch(checked = popupTerminalEnabled, onCheckedChange = {
                        popupTerminalEnabled = it
                        setBool(PREF_POPUP_TERMINAL_ENABLED, it)
                    })
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            Text("Shortcuts", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))

            ElevatedCard(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                onClick = { context.startActivity(Intent(context, SystemInfoActivity::class.java)) }
            ) {
                Text("System Info", modifier = Modifier.padding(16.dp))
            }
            ElevatedCard(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                onClick = { context.startActivity(Intent(context, BackupActivity::class.java)) }
            ) {
                Text("Backup & Restore", modifier = Modifier.padding(16.dp))
            }


            Spacer(modifier = Modifier.height(20.dp))
            Text("About", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))
            Text("EasyMux — Termux, made simple.", fontSize = 12.sp)
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
