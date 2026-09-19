package com.termux.app.easymux

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.termux.app.TermuxActivity
import android.content.Context

class EasyMuxHomeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                EasyMuxHomeScreen(
                    onOpenTerminal = {
                        startActivity(Intent(this, TermuxActivity::class.java))
                    }
                )
            }
        }
    }
}

data class FeatureItem(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val comingSoon: Boolean = true
)

@Composable
fun EasyMuxHomeScreen(onOpenTerminal: () -> Unit) {
    val context = LocalContext.current
    val features = listOf(
        FeatureItem("Package Manager", "Install & manage packages visually", Icons.Filled.Inventory2),
        FeatureItem("File Manager", "Browse and manage files graphically", Icons.Filled.Folder),
        FeatureItem("Scripts", "Save and run your own scripts", Icons.Filled.Description),
        FeatureItem("Process Manager", "View and control running processes", Icons.Filled.Memory),
        FeatureItem("Network Tools", "Ping, port scan, and more", Icons.Filled.Wifi),
        FeatureItem("Termux:API", "Battery, clipboard, vibrate, volume, share", Icons.Filled.PhoneAndroid),
        FeatureItem("Settings", "Configure EasyMux", Icons.Filled.Settings)
    )

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(20.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "EasyMux", fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Text(text = "Termux, made simple", fontSize = 15.sp)
            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onOpenTerminal,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Icon(Icons.Filled.Terminal, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Open Terminal", fontSize = 18.sp)
            }

            Spacer(modifier = Modifier.height(28.dp))
            Text(text = "Features", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(12.dp))

            features.forEach { feature ->
                FeatureCard(
                    feature = feature,
                    onClick = {
                        if (feature.title == "Package Manager") {
                            context.startActivity(Intent(context, PackageManagerActivity::class.java))
                        } else if (feature.title == "File Manager") {
                            context.startActivity(Intent(context, FileManagerActivity::class.java))
                        } else if (feature.title == "Scripts") {
                            context.startActivity(Intent(context, ScriptsActivity::class.java))
                        } else if (feature.title == "Process Manager") {
                            context.startActivity(Intent(context, ProcessManagerActivity::class.java))
                        } else if (feature.title == "Network Tools") {
                            context.startActivity(Intent(context, NetworkToolsActivity::class.java))
                        } else if (feature.comingSoon) {
                            Toast.makeText(context, "${feature.title} — coming soon", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
fun FeatureCard(feature: FeatureItem, onClick: () -> Unit) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(feature.icon, contentDescription = null, modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = feature.title, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text(text = feature.description, fontSize = 13.sp)
            }
            if (feature.comingSoon) {
                AssistChip(onClick = {}, label = { Text("Soon", fontSize = 11.sp) })
            }
        }
    }
}
