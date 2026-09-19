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
import java.io.File

private const val HOME = "/data/data/com.termux/files/home"

class FileManagerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                FileManagerScreen(onBack = { finish() })
            }
        }
    }
}

@Composable
fun FileManagerScreen(onBack: () -> Unit) {
    var currentPath by remember { mutableStateOf(HOME) }
    var entries by remember { mutableStateOf(listOf<File>()) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var showNewFileDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<File?>(null) }
    var deleteTarget by remember { mutableStateOf<File?>(null) }
    var newName by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf("") }

    fun refresh() {
        val dir = File(currentPath)
        entries = (dir.listFiles()?.toList() ?: listOf())
            .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
    }

    LaunchedEffect(currentPath) { refresh() }

    fun goUp() {
        if (currentPath != HOME) {
            currentPath = File(currentPath).parent ?: HOME
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
                Text("File Manager", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { goUp() }, enabled = currentPath != HOME) {
                    Icon(Icons.Filled.ArrowUpward, contentDescription = "Up")
                }
                Text(
                    text = currentPath.removePrefix(HOME).ifBlank { "~" },
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
