package com.termux.app.easymux

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import java.io.File

private const val PREFIX = "/data/data/com.termux/files/usr"
private const val HOME = "/data/data/com.termux/files/home"

/**
 * Invisible activity used as the target of pinned home-screen shortcuts.
 * Receives the script path as an extra, runs it, shows a toast, and finishes immediately.
 */
class ShortcutRunnerActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scriptPath = intent.getStringExtra("script_path")
        if (scriptPath != null && File(scriptPath).exists()) {
            Toast.makeText(this, "Running ${File(scriptPath).name}...", Toast.LENGTH_SHORT).show()
            try {
                val pb = ProcessBuilder(listOf("$PREFIX/bin/bash", scriptPath))
                pb.environment()["PREFIX"] = PREFIX
                pb.environment()["HOME"] = HOME
                pb.environment()["LD_LIBRARY_PATH"] = "$PREFIX/lib"
                pb.environment()["PATH"] = "$PREFIX/bin:$PREFIX/bin/applets"
                pb.directory(File(HOME))
                pb.start()
            } catch (e: Exception) {
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(this, "Script not found", Toast.LENGTH_SHORT).show()
        }
        finish()
    }
}
