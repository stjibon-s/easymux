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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.io.File

private const val HOME = "/data/data/com.termux/files/home"
private val SHARED_STORAGE = Environment.getExternalStorageDirectory().absolutePath

class FileManagerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EasyMuxTheme {
                FileManagerScreen(onBack = { finish() })
            }
        }
    }
}

@Composable
fun FileManagerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var rootPath by remember { mutableStateOf(HOME) }
    var currentPath by remember { mutableStateOf(HOME) }
    var entries by remember { mutableStateOf(listOf<File>()) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var showNewFileDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<File?>(null) }
    var deleteTarget by remember { mutableStateOf<File?>(null) }
    var newName by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf("") }
    var hasStoragePermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        )
    }
    val storagePermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasStoragePermission = granted
        if (granted) {
            rootPath = SHARED_STORAGE
            currentPath = SHARED_STORAGE
        }
    }

    fun refresh() {
        val dir = File(currentPath)
        entries = (dir.listFiles()?.toList() ?: listOf())
            .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
    }

    LaunchedEffect(currentPath) { refresh() }

    fun goUp() {
        if (currentPath != rootPath) {
            currentPath = File(currentPath).parent ?: rootPath
        }
    }

    fun switchStorage() {
        if (rootPath == HOME) {
            if (hasStoragePermission) {
                rootPath = SHARED_STORAGE
                currentPath = SHARED_STORAGE
            } else {
                storagePermissionLauncher.launch(android.Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        } else {
            rootPath = HOME
            currentPath = HOME
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                }
                Text("File Manager", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = { switchStorage() }) {
                    Text(if (rootPath == HOME) "Shared Storage" else "Termux Home")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { goUp() }, enabled = currentPath != rootPath) {
                    Icon(Icons.Filled.ArrowUpward, contentDescription = "Up")
                }
                Text(
                    text = (if (rootPath == HOME) "~" else "Storage") + currentPath.removePrefix(rootPath).ifBlank { "" },
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { showNewFolderDialog = true }) {
                    Icon(Icons.Filled.CreateNewFolder, contentDescription = "New Folder")
                }
                IconButton(onClick = { showNewFileDialog = true }) {
                    Icon(Icons.Filled.NoteAdd, contentDescription = "New File")
                }
            }

            if (statusMessage.isNotBlank()) {
                Text(statusMessage, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp))
            }

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
                items(entries) { entry ->
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        onClick = {
                            if (entry.isDirectory) {
                                currentPath = entry.absolutePath
                            }
                        }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (entry.isDirectory) Icons.Filled.Folder else Icons.Filled.Description,
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(entry.name, fontWeight = FontWeight.Medium)
                                if (!entry.isDirectory) {
                                    Text("${entry.length()} bytes", fontSize = 11.sp)
                                }
                            }
                            IconButton(onClick = {
                                renameTarget = entry
                                newName = entry.name
                            }) {
                                Icon(Icons.Filled.Edit, contentDescription = "Rename")
                            }
                            IconButton(onClick = { deleteTarget = entry }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showNewFolderDialog) {
        AlertDialog(
            onDismissRequest = { showNewFolderDialog = false; newName = "" },
            title = { Text("New Folder") },
            text = {
                OutlinedTextField(value = newName, onValueChange = { newName = it }, label = { Text("Name") })
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newName.isNotBlank()) {
                        File(currentPath, newName).mkdirs()
                        statusMessage = "Folder created"
                        refresh()
                    }
                    showNewFolderDialog = false
                    newName = ""
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showNewFolderDialog = false; newName = "" }) { Text("Cancel") }
            }
        )
    }

    if (showNewFileDialog) {
        AlertDialog(
            onDismissRequest = { showNewFileDialog = false; newName = "" },
            title = { Text("New File") },
            text = {
                OutlinedTextField(value = newName, onValueChange = { newName = it }, label = { Text("Name") })
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newName.isNotBlank()) {
                        File(currentPath, newName).createNewFile()
                        statusMessage = "File created"
                        refresh()
                    }
                    showNewFileDialog = false
                    newName = ""
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showNewFileDialog = false; newName = "" }) { Text("Cancel") }
            }
        )
    }

    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null; newName = "" },
            title = { Text("Rename") },
            text = {
                OutlinedTextField(value = newName, onValueChange = { newName = it }, label = { Text("New name") })
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newName.isNotBlank()) {
                        target.renameTo(File(target.parent, newName))
                        statusMessage = "Renamed"
                        refresh()
                    }
                    renameTarget = null
                    newName = ""
                }) { Text("Rename") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null; newName = "" }) { Text("Cancel") }
            }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete") },
            text = { Text("Delete \"${target.name}\"? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    target.deleteRecursively()
                    statusMessage = "Deleted"
                    refresh()
                    deleteTarget = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            }
        )
    }
}
