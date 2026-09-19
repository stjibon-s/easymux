package com.termux.app.easymux

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.location.LocationManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

class TermuxApiActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                TermuxApiScreen(onBack = { finish() })
            }
        }
    }
}

@Composable
fun TermuxApiScreen(onBack: () -> Unit) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Battery", "Clipboard", "Vibrate", "Volume", "Share", "Torch", "Location", "Notification", "Brightness")

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                }
                Text("Termux:API Hub", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            ScrollableTabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(selected = selectedTab == index, onClick = { selectedTab = index }, text = { Text(title) })
                }
            }
            when (selectedTab) {
                0 -> BatteryTab()
                1 -> ClipboardTab()
                2 -> VibrateTab()
                3 -> VolumeTab()
                4 -> ShareTab()
                5 -> TorchTab()
                6 -> LocationTab()
                7 -> NotificationTab()
                8 -> BrightnessTab()
            }
        }
    }
}

@Composable
fun BatteryTab() {
    val context = LocalContext.current
    var level by remember { mutableStateOf(-1) }
    var status by remember { mutableStateOf("Unknown") }
    var temp by remember { mutableStateOf("Unknown") }
    var plugged by remember { mutableStateOf("Unknown") }

    var batteryError by remember { mutableStateOf("") }
    fun refresh() {
        try {
            val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus = context.registerReceiver(null as BroadcastReceiver?, intentFilter)
            batteryStatus?.let {
                val lvl = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                level = if (lvl >= 0 && scale > 0) (lvl * 100 / scale) else -1
                val st = it.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                status = when (st) {
                    BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
                    BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
                    BatteryManager.BATTERY_STATUS_FULL -> "Full"
                    BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not charging"
                    else -> "Unknown"
                }
                val t = it.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
                temp = if (t >= 0) "${t / 10.0}°C" else "Unknown"
                val plug = it.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
                plugged = when (plug) {
                    BatteryManager.BATTERY_PLUGGED_AC -> "AC Charger"
                    BatteryManager.BATTERY_PLUGGED_USB -> "USB"
                    BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
                    else -> "Not plugged"
                }
            }
            batteryError = ""
        } catch (e: Exception) {
            batteryError = "Error: ${e.message}"
        }
    }

    LaunchedEffect(Unit) { refresh() }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Level: $level%", fontSize = 15.sp)
                Text("Status: $status", fontSize = 15.sp)
                Text("Temperature: $temp", fontSize = 15.sp)
                Text("Power source: $plugged", fontSize = 15.sp)
            }
        }
        if (batteryError.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(batteryError, fontSize = 12.sp)
        }
        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = { refresh() }) { Text("Refresh") }
    }
}

@Composable
fun ClipboardTab() {
    val context = LocalContext.current
    var textToCopy by remember { mutableStateOf("") }
    var clipboardContent by remember { mutableStateOf("") }
    val clipboardManager = remember { context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Set Clipboard", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = textToCopy, onValueChange = { textToCopy = it }, label = { Text("Text") }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = {
            clipboardManager.setPrimaryClip(ClipData.newPlainText("EasyMux", textToCopy))
        }, modifier = Modifier.fillMaxWidth()) { Text("Copy to Clipboard") }

        Spacer(modifier = Modifier.height(24.dp))
        Text("Get Clipboard", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = {
            clipboardContent = clipboardManager.primaryClip?.getItemAt(0)?.text?.toString() ?: "Clipboard is empty"
        }, modifier = Modifier.fillMaxWidth()) { Text("Read Clipboard") }
        if (clipboardContent.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Text(clipboardContent, fontSize = 13.sp, modifier = Modifier.padding(12.dp))
            }
        }
    }
}

@Composable
fun VibrateTab() {
    val context = LocalContext.current
    var durationMs by remember { mutableStateOf("500") }
    val vibrator = remember { context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Vibrate", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = durationMs, onValueChange = { durationMs = it }, label = { Text("Duration (ms)") }, singleLine = true)
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = {
            val ms = durationMs.toLongOrNull() ?: 500L
            vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        }, modifier = Modifier.fillMaxWidth()) { Text("Vibrate") }
    }
}

@Composable
fun VolumeTab() {
    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    var currentVolume by remember { mutableStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()) }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Media Volume", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))
        Text("${currentVolume.toInt()} / $maxVolume")
        Slider(
            value = currentVolume,
            onValueChange = {
                currentVolume = it
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, it.toInt(), 0)
            },
            valueRange = 0f..maxVolume.toFloat(),
            steps = maxVolume - 1
        )
    }
}

@Composable
fun ShareTab() {
    val context = LocalContext.current
    var textToShare by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Share Text", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = textToShare, onValueChange = { textToShare = it }, label = { Text("Text to share") }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = {
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, textToShare)
            }
            context.startActivity(Intent.createChooser(sendIntent, "Share via"))
        }, modifier = Modifier.fillMaxWidth()) { Text("Share") }
    }
}

@Composable
fun TorchTab() {
    val context = LocalContext.current
    var isOn by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf("") }
    val cameraManager = remember { context.getSystemService(Context.CAMERA_SERVICE) as CameraManager }
    val cameraId = remember {
        try { cameraManager.cameraIdList.firstOrNull { id -> cameraManager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true } } catch (e: Exception) { null }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Torch / Flashlight", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(12.dp))
        if (cameraId == null) {
            Text("No flashlight found on this device")
        } else {
            Button(onClick = {
                try {
                    isOn = !isOn
                    cameraManager.setTorchMode(cameraId, isOn)
                    errorMsg = ""
                } catch (e: Exception) {
                    errorMsg = "Error: ${e.message}"
                }
            }, modifier = Modifier.fillMaxWidth()) { Text(if (isOn) "Turn Off" else "Turn On") }
        }
        if (errorMsg.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(errorMsg, fontSize = 12.sp)
        }
    }
}

@Composable
fun LocationTab() {
    val context = LocalContext.current
    var locationText by remember { mutableStateOf("") }
    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Location", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(12.dp))
        if (!hasPermission) {
            Button(onClick = { launcher.launch(android.Manifest.permission.ACCESS_FINE_LOCATION) }, modifier = Modifier.fillMaxWidth()) {
                Text("Grant Location Permission")
            }
        } else {
            Button(onClick = {
                try {
                    val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                    val providers = lm.getProviders(true)
                    var loc: android.location.Location? = null
                    for (p in providers) {
                        val l = lm.getLastKnownLocation(p)
                        if (l != null) { loc = l; break }
                    }
                    locationText = if (loc != null) "Lat: ${loc.latitude}\nLon: ${loc.longitude}\nAccuracy: ${loc.accuracy}m"
                    else "No last known location available. Open Maps once to cache a location."
                } catch (e: Exception) {
                    locationText = "Error: ${e.message}"
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("Get Last Known Location") }
        }
        if (locationText.isNotBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Text(locationText, fontSize = 13.sp, modifier = Modifier.padding(12.dp))
            }
        }
    }
}

@Composable
fun NotificationTab() {
    val context = LocalContext.current
    var title by remember { mutableStateOf("EasyMux") }
    var message by remember { mutableStateOf("") }
    var hasPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= 33)
                ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            else true
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Send Notification", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(12.dp))
        if (!hasPermission) {
            Button(onClick = { launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS) }, modifier = Modifier.fillMaxWidth()) {
                Text("Grant Notification Permission")
            }
        } else {
            OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(value = message, onValueChange = { message = it }, label = { Text("Message") }, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = {
                val channelId = "easymux_channel"
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (Build.VERSION.SDK_INT >= 26) {
                    val channel = NotificationChannel(channelId, "EasyMux Notifications", NotificationManager.IMPORTANCE_DEFAULT)
                    manager.createNotificationChannel(channel)
                }
                val notification = NotificationCompat.Builder(context, channelId)
                    .setContentTitle(title)
                    .setContentText(message)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .build()
                manager.notify(1001, notification)
            }, modifier = Modifier.fillMaxWidth()) { Text("Send") }
        }
    }
}

@Composable
fun BrightnessTab() {
    val context = LocalContext.current
    var canWrite by remember { mutableStateOf(Settings.System.canWrite(context)) }
    var brightness by remember {
        mutableStateOf(
            try { Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS).toFloat() }
            catch (e: Exception) { 128f }
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Screen Brightness", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(12.dp))
        if (!canWrite) {
            Button(onClick = {
                val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}"))
                context.startActivity(intent)
            }, modifier = Modifier.fillMaxWidth()) { Text("Grant Settings Permission") }
        } else {
            Text("${brightness.toInt()} / 255")
            Slider(
                value = brightness,
                onValueChange = {
                    brightness = it
                    Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, it.toInt())
                },
                valueRange = 0f..255f
            )
        }
    }
}
