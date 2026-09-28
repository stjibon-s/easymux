package com.termux.app.easymux

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

private const val PREFIX = "/data/data/com.termux/files/usr"
private const val HOME = "/data/data/com.termux/files/home"

private fun runTermuxCommand(command: String): String {
    return try {
        val pb = ProcessBuilder(listOf("$PREFIX/bin/bash", "-c", command))
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

data class ToolField(val placeholder: String, val label: String)
data class CustomTool(val name: String, val template: String, val fields: List<ToolField>)

class ToolStorage(private val file: File) {
    fun load(): List<CustomTool> {
        if (!file.exists()) return emptyList()
        return try {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                val fieldsArr = obj.getJSONArray("fields")
                val fields = (0 until fieldsArr.length()).map { j ->
                    val f = fieldsArr.getJSONObject(j)
                    ToolField(f.getString("placeholder"), f.getString("label"))
                }
                CustomTool(obj.getString("name"), obj.getString("template"), fields)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun save(tools: List<CustomTool>) {
        val arr = JSONArray()
        tools.forEach { tool ->
            val obj = JSONObject()
            obj.put("name", tool.name)
            obj.put("template", tool.template)
            val fieldsArr = JSONArray()
            tool.fields.forEach { f ->
                val fo = JSONObject()
                fo.put("placeholder", f.placeholder)
                fo.put("label", f.label)
                fieldsArr.put(fo)
            }
            obj.put("fields", fieldsArr)
            arr.put(obj)
        }
        file.writeText(arr.toString())
    }
}

class CustomToolBuilderActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EasyMuxTheme {
                CustomToolBuilderScreen(onBack = { finish() }, storage = ToolStorage(File(filesDir, "custom_tools.json")))
            }
        }
    }
}

@Composable
fun CustomToolBuilderScreen(onBack: () -> Unit, storage: ToolStorage) {
    var tools by remember { mutableStateOf(storage.load()) }
    var showBuilder by remember { mutableStateOf(false) }
    var runningTool by remember { mutableStateOf<CustomTool?>(null) }

    fun refresh() { tools = storage.load() }

    HackScreen {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                }
                Text("My Tools", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = { showBuilder = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "New Tool")
                }
            }
            Text(
                "Build your own GUI for any command by defining {placeholder} fields.",
                fontSize = 12.sp,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            if (tools.isEmpty()) {
                Text("No custom tools yet. Tap + to create one.", fontSize = 13.sp)
            }

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(tools) { tool ->
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        onClick = { runningTool = tool }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(tool.name, fontWeight = FontWeight.Medium)
                                Text(tool.template, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                            }
                            IconButton(onClick = {
                                tools = tools.filter { it.name != tool.name }
                                storage.save(tools)
                            }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showBuilder) {
        ToolBuilderDialog(
            onDismiss = { showBuilder = false },
            onSave = { newTool ->
                tools = tools + newTool
                storage.save(tools)
                showBuilder = false
            }
        )
    }

    runningTool?.let { tool ->
        RunToolDialog(tool = tool, onDismiss = { runningTool = null })
    }
}

@Composable
fun ToolBuilderDialog(onDismiss: () -> Unit, onSave: (CustomTool) -> Unit) {
    var name by remember { mutableStateOf("") }
    var template by remember { mutableStateOf("") }
    var fields by remember { mutableStateOf(listOf<ToolField>()) }
    var newFieldPlaceholder by remember { mutableStateOf("") }
    var newFieldLabel by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Custom Tool") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Tool name") }, singleLine = true)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = template,
                    onValueChange = { template = it },
                    label = { Text("Command, e.g. nmap -p {port} {target}") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text("Fields (must match {placeholder} names above)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                fields.forEach { f ->
                    Text("• {${f.placeholder}} → \"${f.label}\"", fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row {
                    OutlinedTextField(
                        value = newFieldPlaceholder,
                        onValueChange = { newFieldPlaceholder = it },
                        label = { Text("placeholder") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    OutlinedTextField(
                        value = newFieldLabel,
                        onValueChange = { newFieldLabel = it },
                        label = { Text("Field label") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
                TextButton(onClick = {
                    if (newFieldPlaceholder.isNotBlank() && newFieldLabel.isNotBlank()) {
                        fields = fields + ToolField(newFieldPlaceholder, newFieldLabel)
                        newFieldPlaceholder = ""
                        newFieldLabel = ""
                    }
                }) { Text("+ Add Field") }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank() && template.isNotBlank()) {
                    onSave(CustomTool(name, template, fields))
                }
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun RunToolDialog(tool: CustomTool, onDismiss: () -> Unit) {
    var values by remember { mutableStateOf(tool.fields.associate { it.placeholder to "" }) }
    var output by remember { mutableStateOf("") }
    var isRunning by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun buildCommand(): String {
        var cmd = tool.template
        values.forEach { (k, v) -> cmd = cmd.replace("{$k}", v) }
        return cmd
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tool.name) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                tool.fields.forEach { field ->
                    OutlinedTextField(
                        value = values[field.placeholder] ?: "",
                        onValueChange = { values = values + (field.placeholder to it) },
                        label = { Text(field.label) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }
                Text("Command: ${buildCommand()}", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                if (isRunning) {
                    Spacer(modifier = Modifier.height(8.dp))
                    HackLoader()
                }
                if (output.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(output, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                isRunning = true
                val cmd = buildCommand()
                scope.launch {
                    output = withContext(Dispatchers.IO) { runTermuxCommand(cmd) }
                    isRunning = false
                }
            }) { Text("Run") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
