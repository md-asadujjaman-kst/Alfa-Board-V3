package com.alfa.board

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.inputmethodservice.InputMethodService
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class AlfaIME : InputMethodService() {

    companion object {
        const val PREFS = "alfa_prefs"
        const val PREF_DARK = "dark"
        const val PREF_GROQ = "groq_key"
        const val PREF_LOG = "keylog"
        const val PREF_NUMROW = "numrow"
    }

    enum class Mode { ENG, BN, BANGLISH, EMOJI, SYMBOLS }

    private var mode = Mode.ENG
    private var shift = false
    private var capsLock = false
    private var shiftPressTime = 0L
    private val banglishBuf = StringBuilder()
    private lateinit var prefs: SharedPreferences
    private lateinit var logMgr: LogManager
    private lateinit var clipMgr: ClipManager
    private var mediaRec: MediaRecorder? = null
    private var recording = false
    private var rootView: LinearLayout? = null
    private var swipeStartX = 0f

    private var cBg = 0; private var cKey = 0; private var cFn = 0
    private var cTxt = 0; private var cTxtFn = 0; private var cAcc = 0
    private var cBar = 0; private var cBevel = 0; private var cHint = 0

    private val deleteHandler = Handler(Looper.getMainLooper())
    private var deleteRunnable: Runnable? = null

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        logMgr = LogManager(this)
        clipMgr = ClipManager(this)
    }

    override fun onCreateInputView(): View {
        loadTheme()
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(cBg)
        rootView = root
        buildKeyboard()
        return root
    }

    override fun onWindowShown() {
        super.onWindowShown()
        loadTheme()
        buildKeyboard()
    }

    private fun loadTheme() {
        val dark = prefs.getBoolean(PREF_DARK, false)
        if (dark) {
            cBg    = Color.parseColor("#0d0d0d")
            cKey   = Color.parseColor("#4d4d4d")
            cFn    = Color.parseColor("#1f1f1f")
            cBevel = Color.parseColor("#252525")
            cTxt   = Color.parseColor("#FFFFFF")
            cTxtFn = Color.parseColor("#CCFFFFFF")
            cHint  = Color.parseColor("#80FFFFFF")
            cAcc   = Color.parseColor("#5E97F6")
            cBar   = Color.parseColor("#21272B")
        } else {
            cBg    = Color.parseColor("#E8EAED")
            cKey   = Color.parseColor("#FFFFFF")
            cFn    = Color.parseColor("#CCCED5")
            cBevel = Color.parseColor("#A9ABAD")
            cTxt   = Color.parseColor("#37474F")
            cTxtFn = Color.parseColor("#CC37474F")
            cHint  = Color.parseColor("#B337474F")
            cAcc   = Color.parseColor("#1A73E8")
            cBar   = Color.parseColor("#E4E7E9")
        }
    }

    private fun buildKeyboard() {
        val root = rootView ?: return
        root.removeAllViews()
        root.setBackgroundColor(cBg)
        root.addView(buildToolbar())
        if (mode == Mode.EMOJI) { root.addView(buildEmojiPanel()); return }
        if (prefs.getBoolean(PREF_NUMROW, true)) root.addView(buildNumRow())
        when (mode) {
            Mode.ENG, Mode.BANGLISH -> buildQwerty(root)
            Mode.BN -> buildBangla(root)
            Mode.SYMBOLS -> buildSymbols(root)
            else -> buildQwerty(root)
        }
        if (mode == Mode.BANGLISH) root.addView(buildBanglishBar())
        root.addView(buildBottomRow())
    }

    private fun buildToolbar(): LinearLayout {
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.setBackgroundColor(cBar)
        bar.setPadding(dp(2), dp(2), dp(2), dp(2))
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.addView(toolBtn("\u2699") {
            val i = Intent(this, SettingsActivity::class.java)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(i)
        })
        bar.addView(toolBtn("\u2726") { showModeMenu() })
        bar.addView(toolBtn("\u263A") { mode = if (mode == Mode.EMOJI) Mode.ENG else Mode.EMOJI; buildKeyboard() })
        val sp = View(this); sp.layoutParams = LinearLayout.LayoutParams(0, 1, 1f); bar.addView(sp)
        bar.addView(toolBtn("\u229E") { showClipboard() })
        bar.addView(toolBtn(if (recording) "\u25A0" else "\u266A") { toggleVoice() })
        bar.addView(toolBtn("\u22EF") {
            Toast.makeText(this, "Swipe spacebar to switch language\nHold C/V/X to Copy/Paste/Cut", Toast.LENGTH_LONG).show()
        })
        return bar
    }

    private fun toolBtn(icon: String, action: () -> Unit): TextView {
        val tv = TextView(this)
        tv.setText(icon)
        tv.textSize = 17f
        tv.gravity = Gravity.CENTER
        tv.setTextColor(cTxtFn)
        tv.setPadding(dp(11), dp(7), dp(11), dp(7))
        tv.setOnClickListener { tv.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP); action() }
        return tv
    }

    private fun showModeMenu() {
        val modes = listOf("English" to Mode.ENG, "\u09AC\u09BE\u0982\u09B2\u09BE" to Mode.BN, "Banglish AI" to Mode.BANGLISH)
        val current = modes.indexOfFirst { it.second == mode }.let { if (it < 0) 0 else it }
        val next = modes[(current + 1) % modes.size]
        mode = next.second
        banglishBuf.clear()
        buildKeyboard()
        Toast.makeText(this, "Mode: ${next.first}", Toast.LENGTH_SHORT).show()
    }

    private fun buildNumRow(): LinearLayout {
        val nums = listOf("1","2","3","4","5","6","7","8","9","0")
        val alts = listOf("!","@","#","\$","%","^","&","*","(",")")
        return row {
            for (i in nums.indices) {
                val tv = makeKeyFn(nums[i], 1f)
                tv.setOnClickListener { type(nums[i]) }
                setupLongAlt(tv, alts[i])
                addView(tv)
            }
        }
    }

    private fun buildQwerty(root: LinearLayout) {
        val r1 = listOf("q","w","e","r","t","y","u","i","o","p")
        val r2 = listOf("a","s","d","f","g","h","j","k","l")
        val r3 = listOf("z","x","c","v","b","n","m")
        val altR2 = mapOf("a" to "@","s" to "#","d" to "\$","f" to "%","g" to "^","h" to "&","j" to "*","k" to "(","l" to ")")
        val altR3 = mapOf("z" to "`","x" to "\u00D7","c" to "\u00A9","v" to "\u221A","b" to "\u00B0","n" to "\u00F1","m" to "\u00B5")

        root.addView(row {
            for (k in r1) {
                val tv = makeKey(if (isUpper()) k.uppercase() else k, 1f)
                tv.setOnClickListener {
                    tv.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    showKeyPreview(tv, if (isUpper()) k.uppercase() else k)
                    type(if (isUpper()) k.uppercase() else k)
                    if (shift && !capsLock) { shift = false; buildKeyboard() }
                }
                addView(tv)
            }
        })

        root.addView(row {
            addView(spacerV(0.5f))
            for (k in r2) {
                val tv = makeKey(if (isUpper()) k.uppercase() else k, 1f)
                tv.setOnClickListener {
                    tv.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    showKeyPreview(tv, if (isUpper()) k.uppercase() else k)
                    type(if (isUpper()) k.uppercase() else k)
                    if (shift && !capsLock) { shift = false; buildKeyboard() }
                }
                altR2[k]?.let { setupLongAlt(tv, it) }
                addView(tv)
            }
            addView(spacerV(0.5f))
        })

        root.addView(row {
            val shiftLabel = when { capsLock -> "\u21EA"; shift -> "\u2B06"; else -> "\u21E7" }
            val shiftKey = makeKeyFn(shiftLabel, 1.5f)
            if (capsLock) shiftKey.setTextColor(cAcc)
            shiftKey.setOnClickListener {
                val now = System.currentTimeMillis()
                if (now - shiftPressTime < 500) { capsLock = !capsLock; shift = false }
                else { if (!capsLock) shift = !shift }
                shiftPressTime = now
                buildKeyboard()
            }
            addView(shiftKey)
            for (k in r3) {
                val tv = makeKey(if (isUpper()) k.uppercase() else k, 1f)
                tv.setOnClickListener {
                    tv.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    showKeyPreview(tv, if (isUpper()) k.uppercase() else k)
                    type(if (isUpper()) k.uppercase() else k)
                    if (shift && !capsLock) { shift = false; buildKeyboard() }
                }
                when (k) {
                    "c" -> setupLongAction(tv, "Copy") { copyText() }
                    "v" -> setupLongAction(tv, "Paste") { pasteText() }
                    "x" -> setupLongAction(tv, "Cut") { cutText() }
                    else -> altR3[k]?.let { setupLongAlt(tv, it) }
                }
                addView(tv)
            }
            val bsKey = makeKeyFn("\u232B", 1.5f)
            setupDeleteKey(bsKey)
            addView(bsKey)
        })
    }

    private fun isUpper() = shift || capsLock

    private fun buildBangla(root: LinearLayout) {
        // OpenBoard rowkeys_bengali exact layout from image
        // Row 1: vowels + matras
        val bn1 = listOf(
            "\u0985","\u0986","\u0987","\u0988","\u0995","\u0996","\u0997","\u0998","\u0999","\u21B5"
        )
        val bn1alt = mapOf(
            "\u0985" to "\u09CD","\u0986" to "\u09BE","\u0987" to "\u09BF","\u0988" to "\u09C0",
            "\u0995" to "\u0995\u09CD\u09B7","\u0996" to "","\u0997" to "\u0997\u09CD\u09B0",
            "\u0998" to "","\u0999" to "\u0999\u09CD\u0995"
        )
        // Row 2
        val bn2 = listOf("\u099A","\u099B","\u099C","\u099D","\u099E","\u099F","\u09A0","\u09A1","\u09A2","\u232B")
        val bn2alt = mapOf("\u099A" to "\u099A\u09CD\u099A","\u099C" to "\u099C\u09CD\u099E","\u099F" to "\u099F\u09CD\u099F")
        // Row 3
        val bn3 = listOf("\u09A3","\u09A4","\u09A5","\u09A6","\u09A7","\u09A8","\u09AA","\u09AB","\u09AC","\u09AD")
        val bn3alt = mapOf("\u09A4" to "\u09CE","\u09A6" to "\u09A6\u09CD\u09A6","\u09AC" to "\u09CD\u09AC","\u09A8" to "\u09A8\u09CD\u09A4")
        // Row 4
        val bn4 = listOf("\u09AE","\u09AF","\u09B0","\u09B2","\u09B6","\u09B7","\u09B8","\u09B9","\u09DF","\u0964")
        val bn4alt = mapOf("\u09B0" to "\u09C3","\u09B6" to "\u09B6\u09CD\u09B0","\u09B7" to "\u09B7\u09CD\u099F","\u09B8" to "\u09B8\u09CD\u09A4","\u0964" to "\u0965")
        // Row 5: matras + digits
        val bn5 = listOf("\u09BE","\u09BF","\u09C0","\u09C1","\u09C2","\u09C3","\u09C7","\u09CB","\u09CC","\u09CD")
        val bn5alt = mapOf("\u09BE" to "\u0986","\u09BF" to "\u0987","\u09C0" to "\u0988","\u09C1" to "\u0989","\u09C2" to "\u098A","\u09C7" to "\u098F","\u09CB" to "\u0993","\u09CC" to "\u0994")

        listOf(bn1 to bn1alt, bn2 to bn2alt, bn3 to bn3alt, bn4 to bn4alt, bn5 to bn5alt).forEachIndexed { ri, (rowKeys, altMap) ->
            root.addView(row {
                for (ch in rowKeys) {
                    when (ch) {
                        "\u232B" -> { val bsKey = makeKeyFn("\u232B", 1.2f); setupDeleteKey(bsKey); addView(bsKey) }
                        "\u21B5" -> {
                            val ent = makeKeyFn("\u21B5", 1.2f)
                            ent.setBackgroundColor(cAcc); ent.setTextColor(Color.WHITE)
                            ent.setOnClickListener { enter() }; addView(ent)
                        }
                        else -> {
                            val tv = makeKey(ch, 1f)
                            tv.setOnClickListener { type(ch); showKeyPreview(tv, ch) }
                            altMap[ch]?.let { if (it.isNotEmpty()) setupLongAlt(tv, it) }
                            addView(tv)
                        }
                    }
                }
            })
        }

        // Bangla digits row
        root.addView(row {
            for (n in listOf("\u09E6","\u09E7","\u09E8","\u09E9","\u09EA","\u09EB","\u09EC","\u09ED","\u09EE","\u09EF")) {
                val tv = makeKeyFn(n, 1f); tv.setOnClickListener { type(n) }; addView(tv)
            }
        })
    }

    private fun buildSymbols(root: LinearLayout) {
        val rows = listOf(
            listOf("~","`","!","@","#","\$","%","^","&","*"),
            listOf("-","\u2014","_","=","<",">","[","]","{","}"),
            listOf("\\","|","\u00A6","/","(",")","\"","'",";",":"),
            listOf("\u00A3","\u20AC","\u00A5","\u00A9","\u00AE","\u2122","\u00B0","\u00B1","\u00F7","\u00D7")
        )
        for (rowData in rows) {
            root.addView(row {
                for (s in rowData) {
                    val tv = makeKey(s, 1f)
                    tv.setOnClickListener { type(s); showKeyPreview(tv, s) }
                    addView(tv)
                }
            })
        }
        root.addView(row {
            addView(spKey("ABC", 2f, fn = true) { mode = Mode.ENG; buildKeyboard() })
            val sp = makeKey("Space", 4f); sp.setOnClickListener { type(" ") }; addView(sp)
            val bsKey = makeKeyFn("\u232B", 2f); setupDeleteKey(bsKey); addView(bsKey)
        })
    }

    private fun buildBanglishBar(): LinearLayout {
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.setBackgroundColor(cBar)
        bar.setPadding(dp(8), dp(4), dp(8), dp(4))
        bar.gravity = Gravity.CENTER_VERTICAL
        val hint = TextView(this)
        hint.setText(if (banglishBuf.isNotEmpty()) "\"$banglishBuf\"" else "Type English \u2192 AI converts to \u09AC\u09BE\u0982\u09B2\u09BE")
        hint.textSize = 11f
        hint.setTextColor(cHint)
        val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        hint.layoutParams = lp
        val convertBtn = makeKeyFn("Convert \u2726", 0f)
        convertBtn.textSize = 12f
        convertBtn.background = roundBg(cAcc, 14, cAcc)
        convertBtn.setTextColor(Color.WHITE)
        val blp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(32))
        convertBtn.layoutParams = blp
        convertBtn.setOnClickListener { convertBanglish() }
        bar.addView(hint); bar.addView(convertBtn)
        return bar
    }

    private fun buildEmojiPanel(): View {
        val container = LinearLayout(this)
        container.orientation = LinearLayout.VERTICAL
        val catBar = LinearLayout(this)
        catBar.orientation = LinearLayout.HORIZONTAL
        catBar.setBackgroundColor(cBar)
        for (icon in listOf("\u263A","\u270B","\u2764","\uD83C\uDF3F","\uD83C\uDF54","\u2708","\uD83D\uDCA1")) {
            val tv = TextView(this); tv.setText(icon); tv.textSize = 18f
            tv.gravity = Gravity.CENTER; tv.setPadding(dp(6), dp(6), dp(6), dp(6))
            tv.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            catBar.addView(tv)
        }
        container.addView(catBar)
        val sv = ScrollView(this)
        sv.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(200))
        val grid = GridLayout(this); grid.columnCount = 8; grid.setPadding(dp(4), dp(4), dp(4), dp(4))
        for (em in getSafeEmoji()) {
            val tv = TextView(this); tv.setText(em); tv.textSize = 24f; tv.gravity = Gravity.CENTER
            val glp = GridLayout.LayoutParams(); glp.width = dp(40); glp.height = dp(40)
            glp.setMargins(dp(2), dp(2), dp(2), dp(2)); tv.layoutParams = glp
            tv.setOnClickListener { type(em) }; grid.addView(tv)
        }
        sv.addView(grid); container.addView(sv)
        container.addView(row {
            addView(spKey("ABC", 2f, fn = true) { mode = Mode.ENG; buildKeyboard() })
            val sp = makeKey("Space", 5f); sp.setOnClickListener { type(" ") }; addView(sp)
            val bsKey = makeKeyFn("\u232B", 1f); setupDeleteKey(bsKey); addView(bsKey)
        })
        return container
    }

    private fun buildBottomRow(): LinearLayout {
        return row {
            val symBtn = makeKeyFn("!#1", 1.3f)
            symBtn.textSize = 13f
            symBtn.setOnClickListener { mode = if (mode == Mode.SYMBOLS) Mode.ENG else Mode.SYMBOLS; buildKeyboard() }
            addView(symBtn)
            val globeBtn = makeKeyFn("\u2295", 1f)
            globeBtn.setOnClickListener { showModeMenu() }
            addView(globeBtn)
            val comma = makeKey(",", 0.7f)
            comma.setOnClickListener { type(",") }
            setupLongAlt(comma, "'")
            addView(comma)
            val spaceBtn = makeKey(modeLabel(), 3.5f)
            spaceBtn.textSize = 12f; spaceBtn.setTextColor(cHint)
            spaceBtn.setOnClickListener { type(" ") }
            spaceBtn.setOnTouchListener { v, ev ->
                when (ev.action) {
                    MotionEvent.ACTION_DOWN -> { swipeStartX = ev.x; false }
                    MotionEvent.ACTION_UP -> {
                        val dx = ev.x - swipeStartX
                        when { dx > dp(60) -> { cycleMode(1); true }; dx < -dp(60) -> { cycleMode(-1); true }; else -> false }
                    }
                    else -> false
                }
            }
            addView(spaceBtn)
            val dot = makeKey(".", 0.7f)
            dot.setOnClickListener { type(".") }
            setupLongAlt(dot, "\u2026")
            addView(dot)
            val enterKey = makeKeyFn("\u21B5", 1.3f)
            enterKey.background = roundBg(cAcc, 6, cBevel)
            enterKey.setTextColor(Color.WHITE)
            enterKey.setOnClickListener { enter() }
            addView(enterKey)
        }
    }

    private fun modeLabel() = when (mode) {
        Mode.ENG -> "English"; Mode.BN -> "\u09AC\u09BE\u0982\u09B2\u09BE"
        Mode.BANGLISH -> "Banglish AI"; Mode.EMOJI -> "Emoji"; Mode.SYMBOLS -> "Symbols"
    }

    private fun cycleMode(dir: Int) {
        val modes = listOf(Mode.ENG, Mode.BANGLISH, Mode.BN)
        val idx = modes.indexOf(mode).let { if (it < 0) 0 else it }
        mode = modes[(idx + dir + modes.size) % modes.size]
        banglishBuf.clear(); buildKeyboard()
        Toast.makeText(this, modeLabel(), Toast.LENGTH_SHORT).show()
    }

    private var previewPopup: PopupWindow? = null

    private fun showKeyPreview(anchor: View, text: String) {
        previewPopup?.dismiss()
        if (text.length > 2) return
        val tv = TextView(this); tv.setText(text); tv.textSize = 24f
        tv.gravity = Gravity.CENTER; tv.setTextColor(cTxt)
        tv.setTypeface(null, Typeface.BOLD)
        tv.setPadding(dp(14), dp(10), dp(14), dp(10))
        tv.background = roundBg(cKey, 8, cBevel)
        val popup = PopupWindow(tv, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        popup.isFocusable = false
        try {
            val loc = IntArray(2); anchor.getLocationInWindow(loc)
            popup.showAtLocation(anchor, Gravity.NO_GRAVITY, loc[0] - dp(4), loc[1] - dp(50))
            previewPopup = popup
            Handler(Looper.getMainLooper()).postDelayed({ popup.dismiss() }, 400)
        } catch (_: Exception) {}
    }

    private fun showClipboard() {
        val list = clipMgr.getAll()
        if (list.isEmpty()) { Toast.makeText(this, "No clipboard history yet", Toast.LENGTH_SHORT).show(); return }
        val container = LinearLayout(this)
        container.orientation = LinearLayout.VERTICAL; container.setBackgroundColor(cBg)
        val sv = ScrollView(this); sv.layoutParams = ViewGroup.LayoutParams(dp(320), dp(280))
        val inner = LinearLayout(this); inner.orientation = LinearLayout.VERTICAL; inner.setPadding(dp(6), dp(6), dp(6), dp(6))
        val title = TextView(this); title.setText("Clipboard History"); title.textSize = 13f
        title.setTypeface(null, Typeface.BOLD); title.setTextColor(cTxt); title.setPadding(dp(8), dp(6), dp(8), dp(8))
        inner.addView(title)
        for (item in list.take(15)) {
            val tv = TextView(this)
            tv.setText(if (item.length > 50) item.take(50) + "\u2026" else item)
            tv.textSize = 12f; tv.setTextColor(cTxt); tv.setPadding(dp(10), dp(8), dp(10), dp(8))
            tv.background = roundBg(cKey, 6, cBevel)
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.setMargins(0, dp(2), 0, dp(2)); tv.layoutParams = lp
            tv.setOnClickListener { currentInputConnection?.commitText(item, 1) }
            inner.addView(tv)
        }
        sv.addView(inner); container.addView(sv)
        val popup = PopupWindow(container, dp(320), dp(300))
        popup.isFocusable = true; popup.isOutsideTouchable = true
        try { rootView?.let { popup.showAtLocation(it, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, 0, dp(200)) } }
        catch (_: Exception) {}
    }

    private fun type(ch: String) {
        currentInputConnection?.commitText(ch, 1)
        if (mode == Mode.BANGLISH && ch != " " && ch != "\n") banglishBuf.append(ch)
        else if (ch == " " && mode == Mode.BANGLISH) banglishBuf.append(" ")
        if (prefs.getBoolean(PREF_LOG, false)) logMgr.log(ch)
    }

    private fun deleteChar() {
        currentInputConnection?.deleteSurroundingText(1, 0)
        if (mode == Mode.BANGLISH && banglishBuf.isNotEmpty()) banglishBuf.deleteCharAt(banglishBuf.length - 1)
    }

    private fun enter() {
        currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
        currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
        if (mode == Mode.BANGLISH) { banglishBuf.clear(); buildKeyboard() }
    }

    private fun copyText() {
        val sel = currentInputConnection?.getSelectedText(0)
        if (sel != null && sel.isNotEmpty()) {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cm.setPrimaryClip(android.content.ClipData.newPlainText("copy", sel))
            clipMgr.add(sel.toString())
            if (prefs.getBoolean(PREF_LOG, false)) logMgr.log("[COPY] $sel")
            Toast.makeText(this, "Copied!", Toast.LENGTH_SHORT).show()
        } else Toast.makeText(this, "Select text first", Toast.LENGTH_SHORT).show()
    }

    private fun pasteText() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.primaryClip?.getItemAt(0)?.coerceToText(this)?.let {
            currentInputConnection?.commitText(it, 1)
            if (prefs.getBoolean(PREF_LOG, false)) logMgr.log("[PASTE] $it")
        }
    }

    private fun cutText() {
        val sel = currentInputConnection?.getSelectedText(0)
        if (sel != null && sel.isNotEmpty()) {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cm.setPrimaryClip(android.content.ClipData.newPlainText("cut", sel))
            currentInputConnection?.commitText("", 1)
            clipMgr.add(sel.toString())
            if (prefs.getBoolean(PREF_LOG, false)) logMgr.log("[CUT] $sel")
            Toast.makeText(this, "Cut!", Toast.LENGTH_SHORT).show()
        } else Toast.makeText(this, "Select text first", Toast.LENGTH_SHORT).show()
    }

    private fun setupDeleteKey(tv: TextView) {
        tv.setOnTouchListener { v, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    deleteChar()
                    deleteRunnable = object : Runnable { override fun run() { deleteChar(); deleteHandler.postDelayed(this, 75) } }
                    deleteHandler.postDelayed(deleteRunnable!!, 400)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    deleteRunnable?.let { deleteHandler.removeCallbacks(it) }; deleteRunnable = null; true
                }
                else -> false
            }
        }
    }

    private fun setupLongAlt(tv: TextView, alt: String) {
        val h = Handler(Looper.getMainLooper()); var fired = false
        tv.setOnTouchListener { v, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> { fired = false; h.postDelayed({ fired = true; v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS); type(alt); showKeyPreview(v as TextView, alt) }, 400); false }
                MotionEvent.ACTION_UP -> { h.removeCallbacksAndMessages(null); if (fired) { fired = false; true } else false }
                MotionEvent.ACTION_CANCEL -> { h.removeCallbacksAndMessages(null); false }
                else -> false
            }
        }
    }

    private fun setupLongAction(tv: TextView, label: String, action: () -> Unit) {
        val h = Handler(Looper.getMainLooper()); var fired = false
        tv.setOnTouchListener { v, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> { fired = false; h.postDelayed({ fired = true; v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS); Toast.makeText(this, label, Toast.LENGTH_SHORT).show(); action() }, 400); false }
                MotionEvent.ACTION_UP -> { h.removeCallbacksAndMessages(null); if (fired) { fired = false; true } else false }
                MotionEvent.ACTION_CANCEL -> { h.removeCallbacksAndMessages(null); false }
                else -> false
            }
        }
    }

    private fun convertBanglish() {
        val text = banglishBuf.toString().trim()
        if (text.isEmpty()) { Toast.makeText(this, "Type something first", Toast.LENGTH_SHORT).show(); return }
        val key = prefs.getString(PREF_GROQ, "") ?: ""
        if (key.isEmpty()) { Toast.makeText(this, "Add Groq API key in Settings", Toast.LENGTH_SHORT).show(); return }
        Toast.makeText(this, "AI converting...", Toast.LENGTH_SHORT).show()
        GroqClient(key).banglish(text) { result ->
            Handler(Looper.getMainLooper()).post {
                currentInputConnection?.deleteSurroundingText(text.length, 0)
                currentInputConnection?.commitText(result, 1)
                if (prefs.getBoolean(PREF_LOG, false)) logMgr.log("[BANGLISH] $text \u2192 $result")
                banglishBuf.clear(); buildKeyboard()
            }
        }
    }

    private fun toggleVoice() { if (recording) stopVoice() else startVoice() }

    private fun startVoice() {
        try {
            val f = java.io.File(cacheDir, "ab_rec.m4a"); if (f.exists()) f.delete()
            mediaRec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(this) else @Suppress("DEPRECATION") MediaRecorder()
            mediaRec!!.setAudioSource(MediaRecorder.AudioSource.MIC)
            mediaRec!!.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mediaRec!!.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            mediaRec!!.setAudioSamplingRate(16000)
            mediaRec!!.setOutputFile(f.absolutePath)
            mediaRec!!.prepare(); mediaRec!!.start()
            recording = true; buildKeyboard()
            Toast.makeText(this, "Recording... tap stop to finish", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Mic error - allow permission in Alfa Board app", Toast.LENGTH_LONG).show()
        }
    }

    private fun stopVoice() {
        try {
            mediaRec?.stop(); mediaRec?.release(); mediaRec = null; recording = false; buildKeyboard()
            val key = prefs.getString(PREF_GROQ, "") ?: ""
            if (key.isEmpty()) { Toast.makeText(this, "Add Groq API key in Settings", Toast.LENGTH_SHORT).show(); return }
            val f = java.io.File(cacheDir, "ab_rec.m4a")
            if (!f.exists() || f.length() < 500) { Toast.makeText(this, "Too short, try again", Toast.LENGTH_SHORT).show(); return }
            val lang = if (mode == Mode.BN || mode == Mode.BANGLISH) "bn" else "en"
            Toast.makeText(this, "Processing voice...", Toast.LENGTH_SHORT).show()
            GroqClient(key).transcribe(f, lang) { text ->
                Handler(Looper.getMainLooper()).post {
                    if (text.isNotEmpty()) { currentInputConnection?.commitText(text, 1); if (prefs.getBoolean(PREF_LOG, false)) logMgr.log("[VOICE] $text") }
                    else Toast.makeText(this, "Could not recognize speech", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) { mediaRec = null; recording = false; buildKeyboard() }
    }

    private fun row(block: LinearLayout.() -> Unit): LinearLayout {
        val r = LinearLayout(this); r.orientation = LinearLayout.HORIZONTAL; r.setPadding(dp(3), dp(2), dp(3), dp(2)); r.block(); return r
    }

    private fun makeKey(label: String, flex: Float): TextView {
        val tv = TextView(this); tv.setText(label)
        tv.textSize = when { label.length >= 6 -> 9f; label.length >= 4 -> 11f; label.length >= 3 -> 12f; label.length == 2 -> 14f; else -> 18f }
        tv.gravity = Gravity.CENTER; tv.setTextColor(cTxt); tv.background = roundBg(cKey, 6, cBevel); tv.isHapticFeedbackEnabled = true
        val lp = LinearLayout.LayoutParams(if (flex == 0f) LinearLayout.LayoutParams.WRAP_CONTENT else 0, dp(46), flex)
        lp.setMargins(dp(2), dp(2), dp(2), dp(2)); tv.layoutParams = lp; return tv
    }

    private fun makeKeyFn(label: String, flex: Float): TextView {
        val tv = makeKey(label, flex); tv.setTextColor(cTxtFn); tv.background = roundBg(cFn, 6, cBevel); return tv
    }

    private fun spKey(label: String, flex: Float, fn: Boolean = false, action: () -> Unit): TextView {
        val tv = if (fn) makeKeyFn(label, flex) else makeKey(label, flex)
        tv.setOnClickListener { tv.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP); action() }; return tv
    }

    private fun spacerV(flex: Float): View {
        val v = View(this); v.layoutParams = LinearLayout.LayoutParams(0, dp(46), flex); return v
    }

    private fun roundBg(color: Int, radiusDp: Int, bevelColor: Int): android.graphics.drawable.Drawable {
        val r = radiusDp * resources.displayMetrics.density
        val bevel = GradientDrawable(); bevel.shape = GradientDrawable.RECTANGLE; bevel.cornerRadius = r; bevel.setColor(bevelColor)
        val face = GradientDrawable(); face.shape = GradientDrawable.RECTANGLE; face.cornerRadius = r; face.setColor(color)
        val ld = LayerDrawable(arrayOf(bevel, face)); ld.setLayerInset(1, 0, 0, 0, dp(1)); return ld
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun getSafeEmoji() = listOf(
        "\uD83D\uDE00","\uD83D\uDE01","\uD83D\uDE02","\uD83D\uDE03","\uD83D\uDE04","\uD83D\uDE05","\uD83D\uDE06","\uD83D\uDE07","\uD83D\uDE08","\uD83D\uDE09",
        "\uD83D\uDE0A","\uD83D\uDE0B","\uD83D\uDE0C","\uD83D\uDE0D","\uD83D\uDE0E","\uD83D\uDE0F","\uD83D\uDE10","\uD83D\uDE11","\uD83D\uDE12","\uD83D\uDE13",
        "\uD83D\uDE14","\uD83D\uDE15","\uD83D\uDE16","\uD83D\uDE17","\uD83D\uDE18","\uD83D\uDE19","\uD83D\uDE1A","\uD83D\uDE1B","\uD83D\uDE1C","\uD83D\uDE1D",
        "\uD83D\uDE1E","\uD83D\uDE1F","\uD83D\uDE20","\uD83D\uDE21","\uD83D\uDE22","\uD83D\uDE23","\uD83D\uDE24","\uD83D\uDE25","\uD83D\uDE26","\uD83D\uDE28",
        "\uD83D\uDE2D","\uD83D\uDE2E","\uD83D\uDE31","\uD83D\uDE33","\uD83D\uDE35","\uD83D\uDE37","\uD83D\uDE38","\uD83D\uDE39","\uD83D\uDE3A","\uD83D\uDE3B",
        "\uD83D\uDC4D","\uD83D\uDC4E","\uD83D\uDC4F","\uD83D\uDC50","\uD83D\uDC46","\uD83D\uDC47","\uD83D\uDC48","\uD83D\uDC49","\uD83D\uDC4A","\uD83D\uDC4B",
        "\uD83D\uDC4C","\u270A","\u270B","\u270C","\uD83D\uDC85","\uD83D\uDCAA",
        "\u2764","\uD83D\uDC94","\uD83D\uDC95","\uD83D\uDC96","\uD83D\uDC97","\uD83D\uDC98","\uD83D\uDC99","\uD83D\uDC9A","\uD83D\uDC9B","\uD83D\uDC9C",
        "\uD83D\uDC9D","\uD83D\uDC9E","\uD83D\uDC9F","\u2B50","\u2728","\uD83D\uDD25","\uD83C\uDF08","\u2600","\uD83C\uDF19","\uD83C\uDF1F",
        "\uD83D\uDCF1","\uD83D\uDCBB","\uD83C\uDFA4","\uD83C\uDFA7","\uD83C\uDFB5","\uD83C\uDFB6","\uD83D\uDCF7","\uD83D\uDCA1","\uD83D\uDD14","\uD83D\uDD11",
        "\uD83C\uDF81","\uD83C\uDF82","\uD83C\uDF89","\uD83C\uDFC6","\uD83C\uDDE7\uD83C\uDDE9","\uD83D\uDCAF","\u2705","\u274C","\u26A0","\uD83D\uDD34",
        "\uD83D\uDFE2","\uD83D\uDD35","\uD83D\uDFE1","\uD83D\uDD36","\uD83D\uDCCC","\uD83D\uDD17","\uD83D\uDC00","\uD83D\uDC2C","\uD83C\uDF39","\uD83C\uDF4E"
    )

    override fun onDestroy() {
        super.onDestroy()
        deleteRunnable?.let { deleteHandler.removeCallbacks(it) }
        mediaRec?.release()
    }
}
