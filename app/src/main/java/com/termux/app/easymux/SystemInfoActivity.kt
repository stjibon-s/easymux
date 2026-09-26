package com.termux.app.easymux

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.StatFs
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
        val output = reader.readText()
        process.waitFor()
        output
    } catch (e: Exception) {
        "Error: ${e.message}"
    }
}

class SystemInfoActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EasyMuxTheme {
                SystemInfoScreen(onBack = { finish() })
            }
        }
    }
}

@Composable
fun InfoCard(title: String, lines: List<String>) {
    ElevatedCard(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(title, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(6.dp))
            lines.forEach { line ->
                Text(line, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun SystemInfoScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var unameOutput by remember { mutableStateOf("Loading...") }
    var appVersion by remember { mutableStateOf("Unknown") }
    val scope = rememberCoroutineScope()

    val storageStat = remember { StatFs(HOME) }
    val freeGb = remember { storageStat.availableBytes / (1024.0 * 1024 * 1024) }
    val totalGb = remember { storageStat.totalBytes / (1024.0 * 1024 * 1024) }

    LaunchedEffect(Unit) {
        unameOutput = withContext(Dispatchers.IO) { runTermuxCommand("uname", "-a") }
        appVersion = try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            "${pInfo.versionName} (build ${pInfo.longVersionCode})"
        } catch (e: PackageManager.NameNotFoundException) {
            "Unknown"
        }
    }

    HackScreen {
    Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
            }
            Text("System Info", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(12.dp))

        InfoCard(
            "Device",
            listOf(
                "Model: ${Build.MANUFACTURER} ${Build.MODEL}",
                "Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                "ABI: ${Build.SUPPORTED_ABIS.joinToString(", ")}"
            )
        )
        InfoCard(
            "Storage (Termux Home)",
            listOf(
                "Free: %.2f GB".format(freeGb),
                "Total: %.2f GB".format(totalGb)
            )
        )
        InfoCard("EasyMux", listOf("Version: $appVersion"))
        InfoCard("Termux Kernel (uname -a)", listOf(unameOutput.ifBlank { "..." }))

        Spacer(modifier = Modifier.height(20.dp))
    }
    }
}
