package com.graphicarray.voicetyper

import android.Manifest
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var keyIn: EditText
    private lateinit var modelBtn: TextView
    private lateinit var modelNote: TextView
    private lateinit var tabClean: TextView
    private lateinit var tabRaw: TextView
    private lateinit var tabEn: TextView
    private lateinit var tabBn: TextView
    private lateinit var micStatus: TextView
    private lateinit var svcStatus: TextView
    private lateinit var kbOnStatus: TextView
    private lateinit var kbPickStatus: TextView
    private var mode = "cleanup"
    private var lang = "en"

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        prefs = Prefs(this); mode = prefs.mode; lang = prefs.lang
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
        titles.addView(text("Voice Typer", 20f, B.TEXT, true).apply { letterSpacing = -0.02f })
        titles.addView(text("by Graphicarray", 13f, B.CYAN, true).apply { setOnClickListener { openProfile() } })
        header.addView(titles)
        col.addView(header, lp(bottom = 16))

        // Language card
        val langCard = card()
        langCard.addView(sectionLabel("Language"))
        tabEn = tab("English") { setLang("en") }
        tabBn = tab("Bangla") { setLang("bn") }
        langCard.addView(segment(tabEn, tabBn))
        langCard.addView(helper("Language for the floating mic. The keyboard mic follows its layout."))
        col.addView(langCard, lp(bottom = 12))

        // Mode card
        val modeCard = card()
        modeCard.addView(sectionLabel("Voice mode"))
        tabClean = tab("Clean up") { setMode("cleanup") }
        tabRaw = tab("Transcribe") { setMode("transcribe") }
        modeCard.addView(segment(tabClean, tabRaw))
        modeCard.addView(helper("Clean up removes fillers and fixes grammar. Transcribe keeps your exact words."))
        col.addView(modeCard, lp(bottom = 12))

        // Gemini card
        val api = card()
        api.addView(sectionLabel("Gemini API key"))
        keyIn = input("Paste key from Google AI Studio", prefs.key).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setOnFocusChangeListener { v, f ->
                v.background = shape(B.ABYSS, dp(12).toFloat(), if (f) B.CYAN else B.EDGE, dp(1))
                if (!f) maybeLoadModels()
            }
        }
        api.addView(keyIn)
        api.addView(helper("Get a free key at aistudio.google.com. It stays on this phone."))
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

        // Setup card
        val setup = card()
        setup.addView(sectionLabel("Setup"))
        micStatus = text("", 14f, B.TEXT, true)
        setup.addView(row(micStatus, secondaryButton("Allow mic").apply {
            setOnClickListener { requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1) }
        }))
        svcStatus = text("", 14f, B.TEXT, true)
        setup.addView(row(svcStatus, secondaryButton("Turn on").apply {
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }).also { (it.layoutParams as LinearLayout.LayoutParams).topMargin = dp(12) })
        setup.addView(helper("In Accessibility, open Installed apps, pick Voice Typer and switch it on. Then tap any text field and press the mic."))
        col.addView(setup, lp(bottom = 12))

        // Keyboard card
        val kb = card()
        kb.addView(sectionLabel("Keyboard"))
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
        kb.addView(helper("Enable Voice Typer Keyboard, then choose it. Switch Jatio and English with the EN / বাং key. The mic follows the layout."))
        col.addView(kb, lp(bottom = 12))

        // Try it
        val tryCard = card()
        tryCard.addView(sectionLabel("Try it here"))
        tryCard.addView(input("Tap here, then tap the mic", "").apply { minLines = 3; gravity = Gravity.TOP })
        col.addView(tryCard, lp(bottom = 16))

        // Single primary button
        val save = primaryButton("Save").apply {
            setOnClickListener {
                prefs.key = keyIn.text.toString(); prefs.mode = mode; prefs.lang = lang
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
        footer.addView(chip("v1.2"))
        col.addView(footer)

        setContentView(ScrollView(this).apply {
            background = pageBg(this@MainActivity); fitsSystemWindows = true; isFillViewport = true; addView(col)
        })
        paintTabs(); setModelLabel()
    }

    override fun onResume() {
        super.onResume(); refreshStatus()
        mode = prefs.mode   // may be changed from the keyboard
        lang = prefs.lang; paintTabs()   // language may be changed from the mic button
    }
    override fun onRequestPermissionsResult(c: Int, p: Array<out String>, r: IntArray) { refreshStatus() }

    private fun setMode(m: String) { mode = m; prefs.mode = m; paintTabs() }
    private fun setLang(l: String) { lang = l; prefs.lang = l; paintTabs() }
    private fun paintTabs() {
        for ((t, on) in listOf(tabClean to (mode == "cleanup"), tabRaw to (mode == "transcribe"),
            tabEn to (lang == "en"), tabBn to (lang == "bn"))) {
            t.background = if (on) grad(1000f) else null
            t.setTextColor(if (on) B.ABYSS else B.SLATE)
            t.typeface = if (on) B.FONT_B else B.FONT
        }
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
        val enabled = (Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: "")
            .contains("$packageName/")
        mark(micStatus, "Microphone", mic); mark(svcStatus, "Mic button", enabled)
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
