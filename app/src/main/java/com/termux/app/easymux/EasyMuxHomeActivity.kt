package com.termux.app.easymux

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.termux.app.TermuxActivity

class EasyMuxHomeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EasyMuxTheme {
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
    val icon: ImageVector,
    val comingSoon: Boolean = true
)

@Composable
fun EasyMuxHomeScreen(onOpenTerminal: () -> Unit) {
    val context = LocalContext.current
    val features = listOf(
        FeatureItem("Package Manager", Icons.Filled.Inventory2, comingSoon = false),
        FeatureItem("File Manager", Icons.Filled.Folder, comingSoon = false),
        FeatureItem("Scripts", Icons.Filled.Description, comingSoon = false),
        FeatureItem("Process Manager", Icons.Filled.Memory, comingSoon = false),
        FeatureItem("Network Tools", Icons.Filled.Wifi, comingSoon = false),
        FeatureItem("Termux:API", Icons.Filled.PhoneAndroid, comingSoon = false),
        FeatureItem("Linux Distros", Icons.Filled.Storage, comingSoon = false),
        FeatureItem("Security Tools", Icons.Filled.Shield, comingSoon = false),
        FeatureItem("System Info", Icons.Filled.Info, comingSoon = false),
        FeatureItem("Backup & Restore", Icons.Filled.Backup, comingSoon = false),
        FeatureItem("Auto-run on Boot", Icons.Filled.PowerSettingsNew, comingSoon = false)
    )

    fun onFeatureClick(feature: FeatureItem) {
        when (feature.title) {
            "Package Manager" -> context.startActivity(Intent(context, PackageManagerActivity::class.java))
            "File Manager" -> context.startActivity(Intent(context, FileManagerActivity::class.java))
            "Scripts" -> context.startActivity(Intent(context, ScriptsActivity::class.java))
            "Process Manager" -> context.startActivity(Intent(context, ProcessManagerActivity::class.java))
            "Network Tools" -> context.startActivity(Intent(context, NetworkToolsActivity::class.java))
            "Termux:API" -> context.startActivity(Intent(context, TermuxApiActivity::class.java))
            "Linux Distros" -> context.startActivity(Intent(context, ProotDistroActivity::class.java))
            "Security Tools" -> context.startActivity(Intent(context, SecurityToolsActivity::class.java))
            "System Info" -> context.startActivity(Intent(context, SystemInfoActivity::class.java))
            "Backup & Restore" -> context.startActivity(Intent(context, BackupActivity::class.java))
            "Auto-run on Boot" -> context.startActivity(Intent(context, BootConfigActivity::class.java))
            else -> Toast.makeText(context, "${feature.title} — coming soon", Toast.LENGTH_SHORT).show()
        }
    }

    HackScreen {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "EasyMux",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = HackGreen
                    )
                    Text(
                        text = "Termux, made simple",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = HackGreenDim
                    )
                }
                IconButton(onClick = {
                    Toast.makeText(context, "Settings — coming soon", Toast.LENGTH_SHORT).show()
                }) {
                    Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = HackGreen)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onOpenTerminal,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Icon(Icons.Filled.Terminal, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Open Terminal", fontFamily = FontFamily.Monospace, fontSize = 16.sp)
            }

            Spacer(modifier = Modifier.height(14.dp))

            val rows = features.chunked(2)
            Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
                rows.forEach { rowItems ->
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        rowItems.forEach { feature ->
                            FeatureTile(
                                feature = feature,
                                onClick = { onFeatureClick(feature) },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .padding(6.dp)
                            )
                        }
                        if (rowItems.size < 2) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FeatureTile(feature: FeatureItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ElevatedCard(
        onClick = onClick,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(feature.icon, contentDescription = null, modifier = Modifier.size(28.dp), tint = HackGreen)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = feature.title,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
            if (feature.comingSoon) {
                Spacer(modifier = Modifier.height(2.dp))
                Text("soon", fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = HackGreenDim)
            }
        }
    }
}
