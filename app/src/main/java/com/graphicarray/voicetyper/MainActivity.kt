package com.graphicarray.voicetyper

import android.Manifest
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var keyIn: EditText
    private lateinit var modelBtn: TextView
    private lateinit var transBtn: TextView
    private lateinit var modelNote: TextView
    private lateinit var tabClean: TextView
    private lateinit var tabRaw: TextView
    private lateinit var micStatus: TextView
    private lateinit var kbOnStatus: TextView
    private lateinit var kbPickStatus: TextView
    private var mode = "cleanup"

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        prefs = Prefs(this); mode = prefs.mode
        Suggest.init(this)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(16)) }

        // Header: linked logo tile, product name, "by Graphicarray" (linked)
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val logo = ImageView(this).apply {
            setImageResource(R.drawable.ic_logo); contentDescription = "Graphicarray on LinkedIn"
            clipToOutline = true
            outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(v: View, o: android.graphics.Outline) { o.setRoundRect(0, 0, v.width, v.height, dp(12).toFloat()) }
            }
            setOnClickListener { openProfile() }
        }
        header.addView(logo, LinearLayout.LayoutParams(dp(44), dp(44)))
        val titles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, 0, 0) }
        titles.addView(text("Ai Voice Typer", 20f, B.TEXT, true).apply { letterSpacing = -0.02f })
        titles.addView(text("by Graphicarray", 13f, B.CYAN, true).apply { setOnClickListener { openProfile() } })
        header.addView(titles)
        col.addView(header, lp(bottom = 16))

        // Mode card
        val modeCard = card()
        modeCard.addSection("Voice mode", "Clean up removes fillers and fixes grammar. Transcribe keeps your exact words.")
        tabClean = tab("Clean up") { setMode("cleanup") }
        tabRaw = tab("Transcribe") { setMode("transcribe") }
        modeCard.addView(segment(tabClean, tabRaw))
        col.addView(modeCard, lp(bottom = 12))

        // Translate card
        val tr = card()
        tr.addSection("Translate language", "The translate key on the keyboard turns all text in the field into this language, using Gemini. It needs your Gemini key below.")
        transBtn = text("", 14f, B.TEXT).apply {
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = shape(B.ABYSS, dp(12).toFloat(), B.EDGE, dp(1))
            setOnClickListener { pickTransLang() }
        }
        tr.addView(transBtn)
        col.addView(tr, lp(bottom = 12))
        setTransLabel()

        // Gemini card
        val api = card()
        api.addSection("Gemini API key", "Get a free key at aistudio.google.com. It stays on this phone.")
        keyIn = input("Paste key from Google AI Studio", prefs.key).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setOnFocusChangeListener { v, f ->
                v.background = shape(B.ABYSS, dp(12).toFloat(), if (f) B.CYAN else B.EDGE, dp(1))
                if (!f) maybeLoadModels()
            }
        }
        api.addView(keyIn)
        api.addView(sectionLabel("Model").apply { setPadding(0, dp(16), 0, dp(8)) })
        modelBtn = text("", 14f, B.TEXT).apply {
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = shape(B.ABYSS, dp(12).toFloat(), B.EDGE, dp(1))
            setOnClickListener { pickModel() }
        }
        api.addView(modelBtn)
        modelNote = helper(if (prefs.models.isEmpty()) "Enter your key, then tap Load models." else "${prefs.models.size} models available.")
        api.addView(row(modelNote, secondaryButton("Load models").apply { setOnClickListener { loadModels() } }))
        col.addView(api, lp(bottom = 12))

        // Microphone card
        val setup = card()
        setup.addSection("Microphone", "The keyboard mic needs this. Tap the mic key on the keyboard to speak.")
        micStatus = text("", 14f, B.TEXT, true)
        setup.addView(row(micStatus, secondaryButton("Allow mic").apply {
            setOnClickListener { requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1) }
        }))
        col.addView(setup, lp(bottom = 12))

        // Keyboard card
        val kb = card()
        kb.addSection("Keyboard", "Enable Ai Voice Typer Keyboard, then choose it. To switch Jatio and English, swipe the space key left or right. The mic follows the layout. The smiley key (top row, or next to ?123) opens emojis: pick a tab, tap an emoji, tap ABC to go back. The translate icon turns all text in the field into your chosen language. The gear opens this screen. The clipboard icon shows your copy history. On the English layout, long-press X to cut all text, C to copy all, V to paste.")
        kbOnStatus = text("", 14f, B.TEXT, true)
        kb.addView(row(kbOnStatus, secondaryButton("Enable").apply {
            setOnClickListener { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
        }))
        kbPickStatus = text("", 14f, B.TEXT, true)
        kb.addView(row(kbPickStatus, secondaryButton("Choose").apply {
            setOnClickListener {
                (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
            }
        }).also { (it.layoutParams as LinearLayout.LayoutParams).topMargin = dp(12) })
        col.addView(kb, lp(bottom = 12))

        // Preferences card: tap to open the popup
        val pf = card()
        val pfHead = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val pfTxt = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        pfTxt.addView(sectionLabel("Preferences").apply { setPadding(0, 0, 0, dp(2)) })
        pfTxt.addView(text("Auto-capitalization, number row, keyboard height", 12.5f, B.SLATE))
        pfHead.addView(pfTxt, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        pfHead.addView(text("›", 24f, B.CYAN, true).apply { setPadding(dp(12), 0, dp(4), 0) })
        pf.addView(pfHead)
        pf.setOnClickListener { showPrefs() }
        col.addView(pf, lp(bottom = 12))

        // Suggestions card
        val sg = card()
        sg.addSection("Suggestions", "Shows 3 word suggestions above the keys. It learns the words you type, separately for Bangla and English. Everything stays on this phone. It is never sent to Gemini or anywhere else. Nothing is learned in password fields, private (incognito) fields, or while the phone is locked.")
        val on = intArrayOf(android.R.attr.state_checked)
        val sw = Switch(this).apply {
            isChecked = prefs.suggest
            thumbTintList = ColorStateList(arrayOf(on, intArrayOf()), intArrayOf(B.CYAN, B.SLATE))
            trackTintList = ColorStateList(arrayOf(on, intArrayOf()), intArrayOf(B.EDGE, B.ABYSS))
            setOnCheckedChangeListener { _, checked -> prefs.suggest = checked }
        }
        sg.addView(row(text("Suggest next words", 14f, B.TEXT, true), sw))
        sg.addView(secondaryButton("Clear learned words").apply { setOnClickListener { confirmClear() } },
            lp(bottom = 0).apply { topMargin = dp(14) })
        col.addView(sg, lp(bottom = 12))

        // Try it
        val tryCard = card()
        tryCard.addView(sectionLabel("Try it here"))
        tryCard.addView(input("Tap here, then tap the mic", "").apply { minLines = 3; gravity = Gravity.TOP })
        col.addView(tryCard, lp(bottom = 16))

        // Single primary button
        val save = primaryButton("Save").apply {
            setOnClickListener {
                prefs.key = keyIn.text.toString(); prefs.mode = mode
                Toast.makeText(this@MainActivity, "Saved", Toast.LENGTH_SHORT).show()
                maybeLoadModels()
            }
        }
        col.addView(save, lp(bottom = 16))
        col.addView(text("Tip: speak, then tap the stop button.", 12.5f, B.SLATE).apply { gravity = Gravity.CENTER }, lp(bottom = 12))

        // Footer
        val footer = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        footer.addView(text("Graphicarray · Numan Ahmed", 12.5f, B.SLATE).apply { setOnClickListener { openProfile() } },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        footer.addView(chip("v" + (try { packageManager.getPackageInfo(packageName, 0).versionName } catch (_: Exception) { "" })))
        col.addView(footer)

        setContentView(ScrollView(this).apply {
            background = pageBg(this@MainActivity); fitsSystemWindows = true; isFillViewport = true; addView(col)
        })
        paintTabs(); setModelLabel()
    }

    override fun onResume() {
        super.onResume(); refreshStatus()
        mode = prefs.mode   // may be changed from the keyboard
        paintTabs()
    }
    override fun onRequestPermissionsResult(c: Int, p: Array<out String>, r: IntArray) { refreshStatus() }

    private fun setMode(m: String) { mode = m; prefs.mode = m; paintTabs() }
    private fun paintTabs() {
        for ((t, on) in listOf(tabClean to (mode == "cleanup"), tabRaw to (mode == "transcribe"))) {
            t.background = if (on) grad(1000f) else null
            t.setTextColor(if (on) B.ABYSS else B.SLATE)
            t.typeface = if (on) B.FONT_B else B.FONT
        }
    }

    // ---- Preferences popup ----
    private fun showPrefs() {
        val d = Dialog(this)
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val box = card()
        box.addView(sectionLabel("Preferences"))
        val on = intArrayOf(android.R.attr.state_checked)
        fun sw(checked: Boolean, change: (Boolean) -> Unit) = Switch(this).apply {
            isChecked = checked
            thumbTintList = ColorStateList(arrayOf(on, intArrayOf()), intArrayOf(B.CYAN, B.SLATE))
            trackTintList = ColorStateList(arrayOf(on, intArrayOf()), intArrayOf(B.EDGE, B.ABYSS))
            setOnCheckedChangeListener { _, c -> change(c) }
        }
        fun opt(title: String, sub: String, right: View?): LinearLayout {
            val r = LinearLayout(this)
            r.gravity = Gravity.CENTER_VERTICAL
            r.setPadding(0, dp(10), 0, dp(10))
            val t = LinearLayout(this)
            t.orientation = LinearLayout.VERTICAL
            t.addView(text(title, 15f, B.TEXT, true))
            t.addView(text(sub, 12.5f, B.SLATE))
            r.addView(t, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            if (right != null) r.addView(right)
            return r
        }
        fun divider() = View(this).apply { setBackgroundColor(B.EDGE) }
        val inner = LinearLayout(this)
        inner.orientation = LinearLayout.VERTICAL
        val full = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        inner.addView(opt("Auto-capitalization", "Capitalize the first word of each sentence (English layout)",
            sw(prefs.autoCap) { prefs.autoCap = it }), full)
        inner.addView(divider(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)))
        inner.addView(opt("Enable number row", "Adds an extra row of digits",
            sw(prefs.numberRow) { prefs.numberRow = it }), full)
        inner.addView(divider(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)))

        // Height block (dimmed while resizing is off)
        val heightBox = LinearLayout(this)
        heightBox.orientation = LinearLayout.VERTICAL
        val hv = text("", 13f, B.CYAN, true)
        val seek = SeekBar(this)
        seek.max = 12   // 70% .. 130%, step 5
        seek.progress = (prefs.kbHeight - 70) / 5
        seek.progressTintList = ColorStateList.valueOf(B.CYAN)
        seek.thumbTintList = ColorStateList.valueOf(B.CYAN)
        seek.progressBackgroundTintList = ColorStateList.valueOf(B.EDGE)
        hv.text = "${70 + 5 * seek.progress}%"
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                hv.text = "${70 + 5 * p}%"
                if (fromUser) prefs.kbHeight = 70 + 5 * p
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        fun dim(enabled: Boolean) { seek.isEnabled = enabled; heightBox.alpha = if (enabled) 1f else 0.4f }
        inner.addView(opt("Enable keyboard resizing", "Lets you change the keyboard height",
            sw(prefs.resize) { prefs.resize = it; dim(it) }), full)
        inner.addView(divider(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)))
        val hh = LinearLayout(this)
        hh.gravity = Gravity.CENTER_VERTICAL
        hh.addView(text("Keyboard Height (Portrait)", 15f, B.TEXT, true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        hh.addView(hv)
        heightBox.addView(hh, full)
        heightBox.addView(seek, full)
        heightBox.setPadding(0, dp(10), 0, dp(4))
        inner.addView(heightBox, full)
        dim(prefs.resize)

        box.addView(ScrollView(this).apply { addView(inner) }, full)
        box.addView(secondaryButton("Done").apply { setOnClickListener { d.dismiss() } },
            lp(bottom = 0).apply { topMargin = dp(12) })
        d.setContentView(box)
        d.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        d.show()
        d.window?.setLayout((resources.displayMetrics.widthPixels * 0.9).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    // ---- Suggestions ----
    private fun confirmClear() {
        val d = Dialog(this)
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val box = card()
        box.addView(sectionLabel("Clear learned words"))
        box.addView(text("This deletes the words and word pairs the keyboard learned from your typing, in Bangla, English and Banglish. The built-in starter words stay.", 14f, B.TEXT))
        val btns = LinearLayout(this).apply { gravity = Gravity.END or Gravity.CENTER_VERTICAL }
        btns.addView(secondaryButton("Cancel").apply { setOnClickListener { d.dismiss() } })
        btns.addView(primaryButton("Clear").apply {
            setOnClickListener {
                d.dismiss()
                Suggest.clearAll { Toast.makeText(this@MainActivity, "Learned words cleared", Toast.LENGTH_SHORT).show() }
            }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { leftMargin = dp(8) })
        box.addView(btns, lp(bottom = 0).apply { topMargin = dp(16) })
        d.setContentView(box)
        d.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        d.show()
        d.window?.setLayout((resources.displayMetrics.widthPixels * 0.9).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    // ---- Translate language ----
    private fun setTransLabel() { transBtn.text = prefs.transLang + "   ▾" }

    private fun pickTransLang() {
        val d = Dialog(this)
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val box = card()
        box.addView(sectionLabel("Translate into"))
        val inner = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        for (l in Gemini.TRANSLATE_LANGS) {
            val sel = l == prefs.transLang
            inner.addView(text(l, 14f, if (sel) B.ABYSS else B.TEXT, sel).apply {
                setPadding(dp(16), dp(12), dp(16), dp(12))
                background = if (sel) grad(1000f) else null
                setOnClickListener { prefs.transLang = l; setTransLabel(); d.dismiss() }
            }, lp(bottom = 4))
        }
        val sv = ScrollView(this).apply { addView(inner) }
        box.addView(sv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, minOf(Gemini.TRANSLATE_LANGS.size * dp(52), dp(380))))
        d.setContentView(box)
        d.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        d.show()
        d.window?.setLayout((resources.displayMetrics.widthPixels * 0.9).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    // ---- Gemini models ----
    private fun setModelLabel() { modelBtn.text = prefs.model + "   ▾" }

    private fun maybeLoadModels() {
        val k = keyIn.text.toString().trim()
        if (k.isNotBlank() && (k != prefs.key || prefs.models.isEmpty())) loadModels()
    }

    private fun loadModels() {
        val k = keyIn.text.toString().trim()
        if (k.isBlank()) { note("Add your key first.", B.AMBER); return }
        note("Loading models…", B.SLATE)
        Thread {
            try {
                val list = Gemini.listModels(k)
                runOnUiThread {
                    if (list.isEmpty()) { note("No models found for this key.", B.AMBER); return@runOnUiThread }
                    prefs.key = k; prefs.models = list
                    if (prefs.model !in list) {
                        prefs.model = list.firstOrNull { it.contains("2.5-flash") && !it.contains("lite") } ?: list.first()
                    }
                    setModelLabel(); note("${list.size} models loaded. Tap the model to choose.", B.GREEN)
                }
            } catch (e: Exception) {
                runOnUiThread { note("Couldn't load: " + (e.message ?: "check key and internet").take(90), B.AMBER) }
            }
        }.start()
    }

    private fun note(t: String, color: Int) { modelNote.text = t; modelNote.setTextColor(color) }

    private fun pickModel() {
        val list = prefs.models
        if (list.isEmpty()) { Toast.makeText(this, "Add your key, then tap Load models", Toast.LENGTH_SHORT).show(); return }
        val d = Dialog(this)
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val box = card()
        box.addView(sectionLabel("Choose model"))
        val inner = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        for (m in list) {
            val sel = m == prefs.model
            inner.addView(text(m, 14f, if (sel) B.ABYSS else B.TEXT, sel).apply {
                setPadding(dp(16), dp(12), dp(16), dp(12))
                background = if (sel) grad(1000f) else null
                setOnClickListener { prefs.model = m; setModelLabel(); d.dismiss() }
            }, lp(bottom = 4))
        }
        val sv = ScrollView(this).apply { addView(inner) }
        box.addView(sv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, minOf(list.size * dp(52), dp(380))))
        d.setContentView(box)
        d.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        d.show()
        d.window?.setLayout((resources.displayMetrics.widthPixels * 0.9).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun refreshStatus() {
        val mic = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        mark(micStatus, "Microphone", mic)
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val kbOn = imm.enabledInputMethodList.any { it.packageName == packageName }
        val kbSel = (Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) ?: "")
            .startsWith("$packageName/")
        mark2(kbOnStatus, if (kbOn) "Keyboard on" else "Keyboard off", kbOn)
        mark2(kbPickStatus, if (kbSel) "Keyboard selected" else "Not selected", kbSel)
    }
    private fun mark2(v: TextView, label: String, ok: Boolean) {
        v.text = (if (ok) "●  " else "○  ") + label
        v.setTextColor(if (ok) B.GREEN else B.AMBER)
    }
    private fun mark(v: TextView, label: String, ok: Boolean) {
        v.text = (if (ok) "●  " else "○  ") + label + if (ok) " ready" else " off"
        v.setTextColor(if (ok) B.GREEN else B.AMBER)
    }

    // ---- small view builders ----
    private fun lp(bottom: Int) = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        .apply { bottomMargin = dp(bottom) }
    private fun tab(t: String, click: () -> Unit) = text(t, 14f, B.SLATE).apply {
        gravity = Gravity.CENTER; setPadding(0, dp(10), 0, dp(10)); setOnClickListener { click() }
    }
    private fun segment(a: TextView, b: TextView) = LinearLayout(this).apply {
        background = shape(B.ABYSS, 1000f, B.EDGE, dp(1)); setPadding(dp(3), dp(3), dp(3), dp(3))
        addView(a, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(b, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    }
    private fun row(left: View, right: View) = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        addView(left, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)); addView(right)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }
    private fun input(hint: String, value: String) = EditText(this).apply {
        setText(value); this.hint = hint; textSize = 14f; typeface = B.FONT
        setTextColor(B.TEXT); setHintTextColor(B.MUTE); setPadding(dp(14), dp(12), dp(14), dp(12))
        background = shape(B.ABYSS, dp(12).toFloat(), B.EDGE, dp(1))
        setOnFocusChangeListener { v, f -> v.background = shape(B.ABYSS, dp(12).toFloat(), if (f) B.CYAN else B.EDGE, dp(1)) }
    }
}
