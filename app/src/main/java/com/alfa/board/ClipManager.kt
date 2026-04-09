package com.alfa.board

import android.content.Context
import android.content.ClipboardManager
import android.content.ClipData
import android.content.SharedPreferences
import org.json.JSONArray

class ClipManager(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("ab_clips", Context.MODE_PRIVATE)
    private val KEY = "clips"

    // Call this to manually add an item (from copy/cut/paste actions)
    fun add(text: String) {
        if (text.isBlank() || text.length > 10000) return
        val list = getAll().toMutableList()
        // Remove duplicate
        list.removeAll { it == text }
        list.add(0, text)
        if (list.size > 50) list.subList(50, list.size).clear()
        val arr = JSONArray()
        list.forEach { arr.put(it) }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    fun getAll(): List<String> {
        return try {
            val arr = JSONArray(prefs.getString(KEY, "[]") ?: "[]")
            (0 until arr.length()).map { arr.getString(it) }
        } catch (_: Exception) { emptyList() }
    }

    fun clear() { prefs.edit().remove(KEY).apply() }

    fun count(): Int = getAll().size
}
