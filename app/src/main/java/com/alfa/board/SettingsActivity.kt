package com.alfa.board

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var logMgr: LogManager
    private lateinit var clipMgr: ClipManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences(AlfaIME.PREFS, Context.MODE_PRIVATE)
        logMgr = LogManager(this)
        clipMgr = ClipManager(this)
        supportActionBar?.title = "Alfa Board Settings"
        setContentView(buildUI())
    }

    private fun buildUI(): ScrollView {
        val scroll = ScrollView(this)
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(20), dp(16), dp(20), dp(40))

        // Header
        val title = TextView(this)
        title.setText("alfa board")
        title.textSize = 28f
        title.setTypeface(null, Typeface.BOLD)
        title.setTextColor(Color.parseColor("#1A73E8"))
        root.addView(title)

        val sub = TextView(this)
        sub.setText("Settings")
        sub.textSize = 14f
        sub.setTextColor(Color.GRAY)
        val sublp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        sublp.setMargins(0, 0, 0, dp(24))
        sub.layoutParams = sublp
        root.addView(sub)

        // THEME
        root.addView(header("Appearance"))
        val swDark = Switch(this)
        swDark.setText("  Dark Theme")
        swDark.textSize = 15f
        swDark.isChecked = prefs.getBoolean(AlfaIME.PREF_DARK, false)
        swDark.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(AlfaIME.PREF_DARK, checked).apply()
            Toast.makeText(this, if (checked) "Dark theme ON" else "Light theme ON", Toast.LENGTH_SHORT).show()
        }
        root.addView(swDark)

        val swNumRow = Switch(this)
        swNumRow.setText("  Show Number Row")
        swNumRow.textSize = 15f
        swNumRow.isChecked = prefs.getBoolean(AlfaIME.PREF_NUMROW, true)
        swNumRow.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(AlfaIME.PREF_NUMROW, checked).apply()
        }
        root.addView(swNumRow)
        root.addView(divider())

        // API KEY
        root.addView(header("AI Engine (Groq - Free)"))
        root.addView(info("Get free key at console.groq.com\nUsed for Banglish AI + Voice typing\nModels: llama3-70b (Banglish) + whisper-large-v3-turbo (Voice)"))
        val etKey = EditText(this)
        etKey.hint = "gsk_..."
        etKey.setText(prefs.getString(AlfaIME.PREF_GROQ, ""))
        etKey.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        etKey.textSize = 13f
        root.addView(etKey)
        root.addView(btn("Save API Key", "#1A73E8") {
            val k = etKey.text.toString().trim()
            prefs.edit().putString(AlfaIME.PREF_GROQ, k).apply()
            Toast.makeText(this, if (k.isEmpty()) "Key cleared" else "API Key saved!", Toast.LENGTH_SHORT).show()
        })
        root.addView(divider())

        // KEYLOGGER
        root.addView(header("Parental Control"))
        root.addView(info("Records all keystrokes typed through Alfa Board.\nIncludes copy/paste/cut/voice actions.\nAccess log with password below."))
        val swLog = Switch(this)
        swLog.setText("  Enable Keylogger")
        swLog.textSize = 15f
        swLog.isChecked = prefs.getBoolean(AlfaIME.PREF_LOG, false)
        swLog.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(AlfaIME.PREF_LOG, checked).apply()
            Toast.makeText(this, if (checked) "Keylogger ON" else "Keylogger OFF", Toast.LENGTH_SHORT).show()
        }
        root.addView(swLog)
        root.addView(info("Log size: ${logMgr.sizeKb()} KB"))
        root.addView(btn("View Log (Password)", "#EA4335") { askPassword() })
        root.addView(divider())

        // CLIPBOARD
        root.addView(header("Clipboard History"))
        root.addView(info("${clipMgr.count()} items stored. History is saved from copy/cut actions."))
        root.addView(btn("View Clipboard History", "#34A853") { showClips() })
        root.addView(btn("Clear Clipboard History", "#9E9E9E") {
            clipMgr.clear()
            Toast.makeText(this, "Cleared", Toast.LENGTH_SHORT).show()
        })
        root.addView(divider())

        // TIPS
        root.addView(header("Usage Tips"))
        root.addView(info(
            "• Swipe LEFT/RIGHT on spacebar to switch language\n" +
            "• Double-tap SHIFT for CAPS LOCK\n" +
            "• Long press C to Copy selected text\n" +
            "• Long press V to Paste\n" +
            "• Long press X to Cut selected text\n" +
            "• Hold backspace to delete continuously\n" +
            "• Long press letter keys for alternate chars\n" +
            "• !#1 button for full symbols panel\n" +
            "• ☺ button in toolbar for emoji"
        ))
        root.addView(divider())

        root.addView(header("About"))
        root.addView(info("Alfa Board v1.0\nBuilt for personal use\nAndroid 5.1+ | Free AI via Groq"))

        scroll.addView(root)
        return scroll
    }

    private fun askPassword() {
        val et = EditText(this)
        et.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        et.gravity = Gravity.CENTER
        et.textSize = 20f
        AlertDialog.Builder(this)
            .setTitle("Enter Password")
            .setView(et)
            .setPositiveButton("Unlock") { _, _ ->
                if (et.text.toString() == "2090718") showLog()
                else Toast.makeText(this, "Wrong password!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun showLog() {
        val sv = ScrollView(this)
        val tv = TextView(this)
        tv.setText(logMgr.read())
        tv.textSize = 11f
        tv.setTextIsSelectable(true)
        tv.typeface = Typeface.MONOSPACE
        tv.setPadding(dp(12), dp(12), dp(12), dp(12))
        sv.addView(tv)
        AlertDialog.Builder(this)
            .setTitle("Keylog (${logMgr.sizeKb()} KB)")
            .setView(sv)
            .setPositiveButton("Close", null)
            .setNeutralButton("Clear All") { _, _ ->
                logMgr.clear()
                Toast.makeText(this, "Log cleared", Toast.LENGTH_SHORT).show()
            }.show()
    }

    private fun showClips() {
        val list = clipMgr.getAll()
        if (list.isEmpty()) { Toast.makeText(this, "No clipboard history yet", Toast.LENGTH_SHORT).show(); return }
        val items = list.map { if (it.length > 80) it.take(80) + "…" else it }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Clipboard (${list.size} items)")
            .setItems(items) { _, i ->
                val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("clip", list[i]))
                Toast.makeText(this, "Copied!", Toast.LENGTH_SHORT).show()
            }
            .setPositiveButton("Close", null)
            .setNegativeButton("Clear All") { _, _ ->
                clipMgr.clear()
                Toast.makeText(this, "Cleared", Toast.LENGTH_SHORT).show()
            }.show()
    }

    private fun header(text: String): TextView {
        val tv = TextView(this)
        tv.setText(text)
        tv.textSize = 16f
        tv.setTypeface(null, Typeface.BOLD)
        tv.setTextColor(Color.parseColor("#1A73E8"))
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.setMargins(0, dp(20), 0, dp(8))
        tv.layoutParams = lp
        return tv
    }

    private fun info(text: String): TextView {
        val tv = TextView(this)
        tv.setText(text)
        tv.textSize = 13f
        tv.setTextColor(Color.parseColor("#555555"))
        tv.setLineSpacing(dp(2).toFloat(), 1f)
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.setMargins(0, dp(2), 0, dp(8))
        tv.layoutParams = lp
        return tv
    }

    private fun divider(): View {
        val v = View(this)
        v.setBackgroundColor(Color.parseColor("#E0E0E0"))
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
        lp.setMargins(0, dp(8), 0, dp(4))
        v.layoutParams = lp
        return v
    }

    private fun btn(label: String, color: String, action: () -> Unit): Button {
        val b = Button(this)
        b.setText(label)
        b.textSize = 14f
        b.setTextColor(Color.WHITE)
        b.backgroundTintList = ColorStateList.valueOf(Color.parseColor(color))
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.setMargins(0, dp(6), 0, dp(4))
        b.layoutParams = lp
        b.setOnClickListener { action() }
        return b
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
