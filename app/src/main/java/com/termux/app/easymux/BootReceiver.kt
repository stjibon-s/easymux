package com.termux.app.easymux

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.io.File

private const val PREFIX = "/data/data/com.termux/files/usr"
private const val HOME = "/data/data/com.termux/files/home"
const val BOOT_PREFS_NAME = "easymux_boot_prefs"
const val BOOT_SCRIPT_KEY = "boot_script_path"

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val prefs = context.getSharedPreferences(BOOT_PREFS_NAME, Context.MODE_PRIVATE)
        val scriptPath = prefs.getString(BOOT_SCRIPT_KEY, null) ?: return
        val scriptFile = File(scriptPath)
        if (!scriptFile.exists()) return

        try {
            val pb = ProcessBuilder(listOf("$PREFIX/bin/bash", scriptPath))
            pb.environment()["PREFIX"] = PREFIX
            pb.environment()["HOME"] = HOME
            pb.environment()["LD_LIBRARY_PATH"] = "$PREFIX/lib"
            pb.environment()["PATH"] = "$PREFIX/bin:$PREFIX/bin/applets"
            pb.directory(File(HOME))
            pb.start()
        } catch (e: Exception) {
            // Silently ignore — there's no UI to report to at boot time.
        }
    }
}
