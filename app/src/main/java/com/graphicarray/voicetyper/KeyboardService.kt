package com.graphicarray.voicetyper

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.text.TextUtils
import android.widget.ScrollView
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/** Graphicarray keyboard: Jatio (Bangla) + English, with a Gemini voice key. */
class KeyboardService : InputMethodService() {
    private enum class V { IDLE, REC, BUSY }
    private enum class Kind { LETTER, SPECIAL, MIC }
    private enum class Shift { OFF, ONCE, LOCK }

    private val main = Handler(Looper.getMainLooper())
    private val recorder = WavRecorder()
    private lateinit var prefs: Prefs

    private var vstate = V.IDLE
    private var bangla = true
    private var page = 0   // 0 letters, 1 symbols, 2 more symbols
    private val symbols: Boolean get() = page != 0
    private var shift = Shift.OFF
    private var lastShiftTap = 0L
    private var passwordField = false

    // Next-word suggestions (all on this device, see Suggest.kt)
    private lateinit var sugRow: LinearLayout
    private lateinit var sugHolder: FrameLayout
    private val sugCells = ArrayList<TextView>()
    private var sugWords: List<String> = emptyList()
    private var sugOn = false        // suggestions are shown in this field
    private var learnOn = false      // and we may learn from it
    private var selActive = false    // text is selected: no suggestions
    private var sugSeq = 0
    private val sugTask = Runnable { runSuggest() }

    private lateinit var frame: FrameLayout
    private lateinit var root: LinearLayout
    private lateinit var pvBubble: TextView
    private var pvOwner: Key? = null
    private lateinit var clips: ClipStore
    private var cm: ClipboardManager? = null
    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener { captureClip(false) }
    private lateinit var clipKey: Key
    private lateinit var translateKey: Key
    private lateinit var backKey: Key
    private val topKeys = ArrayList<Key>()   // clip, translate, clean up, settings: shown while not typing
    private var typingMode = false           // true: back key + suggestions. false: all icons
    private var busyLabel = "Processing…"
    private lateinit var clipPanel: LinearLayout
    private lateinit var clipList: LinearLayout
    private var clipOpen = false
    private lateinit var rows: LinearLayout
    private lateinit var pill: TextView
    private lateinit var modeKey: Key
    private var micKey: Key? = null
    private var shiftKey: Key? = null
    private class LK(val tv: TextView, val hint: TextView?, val c: Layouts.Cell)
    private val letters = ArrayList<LK>()
    private var pulse: ObjectAnimator? = null
    private var flashing = false
    private var repeatTask: Runnable? = null
    private val resetPill = Runnable { flashing = false; refreshPill() }

    // ---------- Lifecycle ----------
    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        bangla = prefs.layout != "en"
        clips = ClipStore(this)
        Suggest.init(this)
        cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        try { cm?.addPrimaryClipChangedListener(clipListener) } catch (_: Exception) {}
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onCreateInputView(): View {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = shape(B.ABYSS, 0f)
            setPadding(dp(4), dp(4), dp(4), dp(8))
        }
        val strip = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        pill = text("", 12f, B.CYAN, true).apply { setPadding(dp(12), dp(5), dp(12), dp(5)) }
        val mdk = Key(Kind.SPECIAL, null, R.drawable.ic_cleanup, 14f, 1f, iconDp = 18)
        bind(mdk) {
            prefs.mode = if (prefs.mode == "cleanup") "transcribe" else "cleanup"
            refreshMode()
            flash(if (prefs.mode == "cleanup") "Clean up" else "Transcribe", B.CYAN)
        }
        modeKey = mdk
        strip.addView(pill, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        // Typing mode: [back] [suggestions] [mic]. Icon mode: [clip] [translate] [clean up] [settings] ... [mic]
        val bkk = Key(Kind.SPECIAL, null, R.drawable.ic_back, 14f, 1f, iconDp = 18)
        bind(bkk) { typingMode = false; applySugVisibility() }
        backKey = bkk
        strip.addView(bkk.box, LinearLayout.LayoutParams(dp(38), dp(32)).apply { leftMargin = dp(6) })
        val ck = Key(Kind.SPECIAL, null, R.drawable.ic_clip, 14f, 1f, iconDp = 18)
        bind(ck) { toggleClips() }
        clipKey = ck
        val tk = Key(Kind.SPECIAL, null, R.drawable.ic_translate, 14f, 1f, iconDp = 18)
        bind(tk) { onTranslate() }
        translateKey = tk
        val gk = Key(Kind.SPECIAL, null, R.drawable.ic_settings, 14f, 1f, iconDp = 18)
        bind(gk) { openApp() }
        topKeys.clear()
        for (k in listOf(ck, tk, mdk, gk)) {
            topKeys.add(k)
            strip.addView(k.box, LinearLayout.LayoutParams(dp(38), dp(32)).apply { leftMargin = dp(6) })
        }
        sugRow = buildSugRow()
        sugHolder = FrameLayout(this)
        sugHolder.addView(sugRow, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(32)))
        strip.addView(sugHolder, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(6); rightMargin = dp(6) })
        val mk = Key(Kind.MIC, null, R.drawable.ic_mic, 14f, 1f, iconDp = 18)
        bind(mk) { onMic() }
        micKey = mk
        strip.addView(mk.box, LinearLayout.LayoutParams(dp(50), dp(32)))
        rows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(strip, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(rows, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        clipPanel = buildClipPanel()
        root.addView(clipPanel, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(224)))
        render()
        paintMic()
        refreshPill()
        refreshMode()

        // Key preview bubble lives in an overlay above the keys.
        pvBubble = TextView(this).apply {
            textSize = 30f; setTextColor(B.TEXT); gravity = Gravity.CENTER; includeFontPadding = false
            typeface = B.FONT; background = shape(B.PANEL, dp(12).toFloat(), B.CYAN, dp(1))
            elevation = dp(8).toFloat(); visibility = View.GONE
        }
        frame = FrameLayout(this)
        frame.addView(root, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        frame.addView(pvBubble, FrameLayout.LayoutParams(dp(52), dp(54), Gravity.TOP or Gravity.START))
        return frame
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        val t = info?.inputType ?: 0
        val cls = t and InputType.TYPE_MASK_CLASS
        val v = t and InputType.TYPE_MASK_VARIATION
        passwordField = (cls == InputType.TYPE_CLASS_TEXT &&
            (v == InputType.TYPE_TEXT_VARIATION_PASSWORD || v == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                v == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)) ||
            (cls == InputType.TYPE_CLASS_NUMBER && v == InputType.TYPE_NUMBER_VARIATION_PASSWORD)
        captureClip(true)
        // Suggestions: only in plain text fields. Learning: also not when the editor asks for no personalised learning.
        sugOn = prefs.suggest && !passwordField && !deviceLocked() && suggestionsAllowed(info)
        learnOn = sugOn && info != null && (info.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) == 0
        selActive = info != null && info.initialSelStart != info.initialSelEnd
        sugWords = emptyList()
        typingMode = false
        if (!::rows.isInitialized) return
        hideClips()
        hidePreview(null)
        shift = Shift.OFF
        if (page != 0) { page = 0; render() } else applyShift()
        refreshMode()
        refreshPill()
        clearSug()
        applySugVisibility()
        if (sugOn) { Suggest.warm(bangla); scheduleSuggest() }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        stopRepeat()
        cancelRec()
        if (::pvBubble.isInitialized) hidePreview(null)
        if (::clipPanel.isInitialized) hideClips()
        main.removeCallbacks(sugTask)
        sugSeq++
        Suggest.flush()
        super.onFinishInputView(finishingInput)
    }

    /** The editor moved the cursor or changed the text (also fires after our own commits). */
    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        selActive = newSelStart != newSelEnd
        if (sugOn) scheduleSuggest()
    }

    override fun onDestroy() {
        stopRepeat()
        cancelRec()
        main.removeCallbacks(sugTask)
        Suggest.flush()
        main.removeCallbacks(resetPill)
        try { cm?.removePrimaryClipChangedListener(clipListener) } catch (_: Exception) {}
        super.onDestroy()
    }

    // ---------- Key widget ----------
    /** Label text: a no-break space before a lone combining sign hides the dotted circle. */
    private fun disp(t: String): String {
        if (t.isEmpty()) return t
        val c = t[0]
        val mark = c == '\u0981' || c == '\u0982' || c == '\u0983' || c == '\u09BC' || c in '\u09BE'..'\u09CD' || c == '\u09D7'
        return if (mark) "\u00A0" + t else t
    }

    private fun bg(kind: Kind, pressed: Boolean, active: Boolean): Drawable {
        val r = dp(10).toFloat()
        if (active) return grad(if (kind == Kind.MIC) 1000f else r)
        return when (kind) {
            Kind.LETTER -> shape(if (pressed) B.EDGE else B.PANEL, r, if (pressed) B.CYAN else B.EDGE, dp(1))
            Kind.SPECIAL -> shape(if (pressed) B.PANEL else B.ABYSS, r, if (pressed) B.CYAN else B.EDGE, dp(1))
            Kind.MIC -> shape(if (pressed) B.EDGE else 0x66001E36, 1000f, B.CYAN, dp(1))
        }
    }

    private inner class Key(val kind: Kind, label: String?, icon: Int, sp: Float, val weight: Float, hintText: String? = null, iconDp: Int = 22) {
        val box = FrameLayout(this@KeyboardService)
        val tv: TextView? = if (label != null) this@KeyboardService.text(disp(label), sp, B.TEXT, false).apply {
            gravity = Gravity.CENTER; includeFontPadding = false
            if (hintText != null) setPadding(0, dp(8), 0, 0)
        } else null
        val hintTv: TextView? = if (hintText != null) this@KeyboardService.text(disp(hintText), 11f, B.SLATE, false).apply {
            includeFontPadding = false
        } else null
        val img: ImageView? = if (icon != 0) ImageView(this@KeyboardService) else null
        var active = false
        var fixedColor = 0

        init {
            if (tv != null) box.addView(tv, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            if (hintTv != null) {
                val hp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END)
                hp.setMargins(0, dp(5), dp(7), 0)
                box.addView(hintTv, hp)
            }
            if (img != null) {
                img.setImageResource(icon)
                box.addView(img, FrameLayout.LayoutParams(dp(iconDp), dp(iconDp), Gravity.CENTER))
            }
            paint(false)
        }

        fun setIcon(res: Int) { img?.setImageResource(res) }

        fun paint(pressed: Boolean) {
            box.background = bg(kind, pressed, active)
            val c = if (active) B.ABYSS else if (fixedColor != 0) fixedColor else if (kind == Kind.MIC) B.CYAN else B.TEXT
            tv?.setTextColor(c)
            img?.setColorFilter(c)
        }
    }

    /**
     * Without [onLong]: fire [act] on touch-down (fast typing; [repeat] keeps firing while held).
     * With [onLong]: fire [act] on release, or [onLong] if held for 350 ms.
     */
    private fun bind(k: Key, repeat: Boolean = false, onLong: (() -> Unit)? = null, pv: Boolean = false, act: () -> Unit) {
        var fired = false
        var task: Runnable? = null
        k.box.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    k.paint(true)
                    if (pv) showPreview(k)
                    if (onLong == null) {
                        act()
                        if (repeat) startRepeat(act)
                    } else {
                        fired = false
                        val t = Runnable {
                            fired = true; onLong()
                            if (pv) k.hintTv?.text?.takeIf { it.isNotEmpty() }?.let { updatePreview(k, it.toString()) }
                        }
                        task = t
                        main.postDelayed(t, 350)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    stopRepeat(); k.paint(false); hidePreview(k)
                    task?.let { main.removeCallbacks(it) }
                    if (onLong != null && !fired) act()
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    stopRepeat(); k.paint(false); hidePreview(k)
                    task?.let { main.removeCallbacks(it) }
                    true
                }
                else -> true
            }
        }
    }

    private fun startRepeat(act: () -> Unit) {
        stopRepeat()
        val r = object : Runnable {
            override fun run() { act(); main.postDelayed(this, 50) }
        }
        repeatTask = r
        main.postDelayed(r, 400)
    }

    private fun stopRepeat() {
        repeatTask?.let { main.removeCallbacks(it) }
        repeatTask = null
    }

    // ---------- Rendering ----------
    private fun cellLp(w: Float): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, w).apply { setMargins(dp(2), dp(3), dp(2), dp(3)) }

    private fun newRow(): LinearLayout = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

    private fun addRow(line: LinearLayout) {
        rows.addView(line, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)))
    }

    private fun render() {
        stopRepeat()
        rows.removeAllViews()
        letters.clear()
        shiftKey = null
        val lay = when (page) {
            1 -> if (bangla) Layouts.SYM1_BN else Layouts.SYM1_EN
            2 -> Layouts.SYM2
            else -> if (bangla) Layouts.JATIO else Layouts.ENGLISH
        }
        val sp = if (symbols) 19f else 20f

        for (ri in 0 until 3) {
            val row = lay.rows[ri]
            val line = newRow()
            val pad = if (ri == 1 && row.size < 10) (10 - row.size) / 2f else 0f
            if (pad > 0f) line.addView(View(this), LinearLayout.LayoutParams(0, 1, pad))

            if (ri == 2) {
                if (!symbols) {
                    val sk = Key(Kind.SPECIAL, null, R.drawable.ic_shift, 14f, 1.5f)
                    bind(sk) { onShift() }
                    line.addView(sk.box, cellLp(1.5f))
                    shiftKey = sk
                } else {
                    val tl = if (page == 1) "=\\<" else (if (bangla) "?১২৩" else "?123")
                    val tk = Key(Kind.SPECIAL, tl, 0, 16f, 1.5f)
                    bind(tk) { page = if (page == 1) 2 else 1; main.post { render() } }
                    line.addView(tk.box, cellLp(1.5f))
                }
            }
            for (c in row) {
                if (c.n == Layouts.CUR) { line.addView(cursorKey(c).box, cellLp(1f)); continue }
                val k = Key(Kind.LETTER, c.label(false), 0, sp, 1f, c.h ?: c.sh)
                val shortcut = !bangla && !symbols && (c.n == "x" || c.n == "c" || c.n == "v")
                if (shortcut) bind(k, onLong = { shortcutKey(c.n) }, pv = true) { typeCell(c) }
                else if (c.h != null || c.sh != null) bind(k, onLong = { longPress(c) }, pv = true) { typeCell(c) }
                else bind(k, pv = true) { typeCell(c) }
                line.addView(k.box, cellLp(1f))
                k.tv?.let { letters.add(LK(it, k.hintTv, c)) }
            }
            if (ri == 2) {
                val bk = Key(Kind.SPECIAL, null, R.drawable.ic_backspace, 14f, 1.5f)
                bind(bk, true) { backspace() }
                line.addView(bk.box, cellLp(1.5f))
            }
            if (pad > 0f) line.addView(View(this), LinearLayout.LayoutParams(0, 1, pad))
            addRow(line)
        }

        // Bottom row. Letters: ?123 | lang | , | space | । or . | enter. Symbols: back | , | space | . | enter.
        val bottom = newRow()
        val symLabel = if (symbols) (if (bangla) "অকখ" else "ABC") else (if (bangla) "?১২৩" else "?123")
        val symK = Key(Kind.SPECIAL, symLabel, 0, 16f, 1.5f)
        bind(symK) { page = if (symbols) 0 else 1; main.post { render() } }
        bottom.addView(symK.box, cellLp(1.5f))

        if (!symbols) {
            val langK = Key(Kind.SPECIAL, if (bangla) "EN" else "বাং", 0, 16f, 1f)
            bind(langK) { switchLayout() }
            bottom.addView(langK.box, cellLp(1f))
        }

        val comma = Key(Kind.SPECIAL, ",", 0, 19f, 1f)
        bind(comma, pv = true) { commitKey(",") }
        bottom.addView(comma.box, cellLp(1f))

        // Space: tap = space, swipe left/right = switch layout
        val sw = if (symbols) 5f else 4f
        val space = Key(Kind.LETTER, "\u25C0\uFE0E    " + (if (bangla) "জাতীয়" else "English") + "    \u25B6\uFE0E", 0, 13f, sw)
        space.fixedColor = B.SLATE
        space.paint(false)
        var downX = 0f
        space.box.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downX = e.rawX; space.paint(true); true }
                MotionEvent.ACTION_UP -> {
                    space.paint(false)
                    if (Math.abs(e.rawX - downX) > dp(48)) switchLayout() else commitRaw(" ")
                    true
                }
                MotionEvent.ACTION_CANCEL -> { space.paint(false); true }
                else -> true
            }
        }
        bottom.addView(space.box, cellLp(sw))

        val dot = if (bangla && !symbols) "।" else "."
        val dotK = Key(Kind.SPECIAL, dot, 0, 19f, 1f)
        bind(dotK, pv = true) { commitKey(dot) }
        bottom.addView(dotK.box, cellLp(1f))

        val enterK = Key(Kind.SPECIAL, null, R.drawable.ic_enter, 14f, 1.5f)
        bind(enterK) { enter() }
        bottom.addView(enterK.box, cellLp(1.5f))
        addRow(bottom)

        applyShift()
    }

    /** Cursor key: tap left half = cursor left, right half = cursor right, hold = hint. */
    private fun cursorKey(c: Layouts.Cell): Key {
        val k = Key(Kind.LETTER, "\u25C0\uFE0E   \u25B6\uFE0E", 0, 13f, 1f, c.h)
        k.fixedColor = B.SLATE
        k.paint(false)
        val hh = c.h ?: ""
        var fired = false
        var task: Runnable? = null
        var x0 = 0f
        k.box.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    k.paint(true); fired = false; x0 = e.x
                    val t = Runnable { fired = true; commitRaw(hh) }
                    task = t
                    main.postDelayed(t, 350)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    k.paint(false)
                    task?.let { main.removeCallbacks(it) }
                    if (!fired) sendDownUpKeyEvents(if (x0 < v.width / 2f) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT)
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    k.paint(false)
                    task?.let { main.removeCallbacks(it) }
                    true
                }
                else -> true
            }
        }
        return k
    }

    private fun applyShift() {
        val on = shift != Shift.OFF && !symbols
        for (l in letters) {
            l.tv.text = disp(l.c.label(on))
            l.hint?.text = disp(l.c.hintFor(on) ?: "")
        }
        shiftKey?.let {
            it.active = shift != Shift.OFF
            it.setIcon(if (shift == Shift.LOCK) R.drawable.ic_shift_lock else R.drawable.ic_shift)
            it.paint(false)
        }
    }

    // ---------- Typing ----------
    private fun onShift() {
        val now = System.currentTimeMillis()
        shift = when {
            shift == Shift.LOCK -> Shift.OFF
            shift == Shift.ONCE && now - lastShiftTap < 400 -> Shift.LOCK
            shift == Shift.ONCE -> Shift.OFF
            else -> Shift.ONCE
        }
        lastShiftTap = now
        applyShift()
    }

    private fun switchLayout() {
        if (vstate != V.IDLE) return
        bangla = !bangla
        prefs.layout = if (bangla) "jatio" else "en"
        shift = Shift.OFF
        page = 0
        main.post { render(); refreshPill() }
        // Suggestions follow the layout: Bangla and English keep separate words.
        clearSug()
        if (sugOn) { Suggest.warm(bangla); scheduleSuggest() }
    }

    private fun commitRaw(s: String) {
        currentInputConnection?.commitText(s, 1)
        afterCommit(s)
    }

    private fun longPress(c: Layouts.Cell) {
        val hnt = c.hintFor(shift != Shift.OFF && !symbols)
        if (hnt == null) { typeCell(c); return }
        commitRaw(hnt)
        if (shift == Shift.ONCE) { shift = Shift.OFF; applyShift() }
    }

    private fun typeCell(c: Layouts.Cell) {
        val s0 = if (shift != Shift.OFF && !symbols) c.s else c.n
        val s = if (s0 == Layouts.TAB) "\t" else s0
        commitKey(s)
        if (shift == Shift.ONCE) { shift = Shift.OFF; applyShift() }
    }

    private fun commitKey(s: String) {
        if (bangla && !symbols) commitBangla(s) else currentInputConnection?.commitText(s, 1)
        afterCommit(s)
    }

    private fun isConsonant(ch: Char): Boolean =
        ch in 'ক'..'হ' || ch in 'ড়'..'য়' || ch == 'ৎ'

    private fun commitBangla(s: String) {
        val ic = currentInputConnection ?: return
        if (s.length == 1) {
            val ch = s[0]
            val prev = ic.getTextBeforeCursor(1, 0)?.toString() ?: ""
            val p = if (prev.isEmpty()) ' ' else prev[0]
            // e-kar + aa-kar = o-kar
            if (p == 'ে' && ch == 'া') {
                ic.deleteSurroundingText(1, 0); ic.commitText("ো", 1); return
            }
            // A vowel sign with no consonant before it becomes the full vowel letter
            val full = INDEP[ch]
            if (full != null && !isConsonant(p)) { ic.commitText(full, 1); return }
        }
        ic.commitText(s, 1)
    }

    private fun backspace() {
        val ic = currentInputConnection ?: return
        val sel = ic.getSelectedText(0)
        if (sel != null && sel.isNotEmpty()) ic.commitText("", 1) else sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
        if (sugOn) scheduleSuggest()
    }

    private fun enter() {
        val ic = currentInputConnection ?: return
        learnNow(false)   // the word before Enter counts as finished
        val ei = currentInputEditorInfo
        val act = if (ei != null) (ei.imeOptions and EditorInfo.IME_MASK_ACTION) else EditorInfo.IME_ACTION_NONE
        val plain = ei == null || (ei.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0 ||
            act == EditorInfo.IME_ACTION_NONE || act == EditorInfo.IME_ACTION_UNSPECIFIED
        if (plain) sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER) else ic.performEditorAction(act)
        if (typingMode) { typingMode = false; applySugVisibility() }
    }

    // ---------- Key preview bubble ----------
    private fun showPreview(k: Key) {
        if (passwordField || !::pvBubble.isInitialized) return
        val t = k.tv?.text?.toString() ?: return
        if (t.isBlank()) return
        pvOwner = k
        updatePreview(k, t)
    }

    private fun updatePreview(k: Key, t: String) {
        if (pvOwner !== k || !::pvBubble.isInitialized || !::frame.isInitialized) return
        val kl = IntArray(2); k.box.getLocationInWindow(kl)
        val fl = IntArray(2); frame.getLocationInWindow(fl)
        val w = k.box.width + dp(16)
        val h = dp(54)
        var x = kl[0] - fl[0] + k.box.width / 2 - w / 2
        x = x.coerceIn(0, maxOf(0, frame.width - w))
        val y = kl[1] - fl[1] - h + dp(4)
        pvBubble.layoutParams = (pvBubble.layoutParams as FrameLayout.LayoutParams).also { it.width = w; it.height = h }
        pvBubble.translationX = x.toFloat()
        pvBubble.translationY = y.toFloat()
        pvBubble.text = t
        pvBubble.visibility = View.VISIBLE
        pvBubble.bringToFront()
    }

    /** Hides the bubble if [k] owns it. Pass null to force-hide. */
    private fun hidePreview(k: Key?) {
        if (!::pvBubble.isInitialized) return
        if (k == null || pvOwner === k) { pvOwner = null; pvBubble.visibility = View.GONE }
    }

    // ---------- Shortcuts (English layout): long-press X = cut all, C = copy all, V = paste ----------
    private fun allText(ic: InputConnection): String {
        val before = ic.getTextBeforeCursor(100000, 0) ?: ""
        val sel = ic.getSelectedText(0) ?: ""
        val after = ic.getTextAfterCursor(100000, 0) ?: ""
        return before.toString() + sel.toString() + after.toString()
    }

    private fun shortcutKey(n: String) {
        val ic = currentInputConnection ?: return
        root.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        when (n) {
            "c", "x" -> {
                val all = allText(ic)
                if (all.isEmpty()) { flash("Nothing to " + (if (n == "c") "copy" else "cut"), B.AMBER); return }
                try { cm?.setPrimaryClip(ClipData.newPlainText("text", all)) } catch (_: Exception) {}
                if (n == "x") {
                    ic.beginBatchEdit()
                    ic.setSelection(0, all.length)
                    ic.commitText("", 1)
                    ic.endBatchEdit()
                }
                flash(if (n == "c") "Copied" else "Cut", B.GREEN)
            }
            "v" -> {
                val t = try { cm?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString() } catch (_: Exception) { null }
                if (t.isNullOrEmpty()) { flash("Clipboard empty", B.AMBER); return }
                ic.commitText(t, 1)
                flash("Pasted", B.GREEN)
            }
        }
    }

    // ---------- Clipboard history ----------
    private fun captureClip(onlyIfNew: Boolean) {
        try {
            val c = cm?.primaryClip ?: return
            if (c.itemCount == 0) return
            val d = c.description
            if (!d.hasMimeType("text/*")) return
            if (d.extras?.getBoolean("android.content.extra.IS_SENSITIVE", false) == true) return
            val t = c.getItemAt(0).coerceToText(this)?.toString() ?: return
            clips.add(t, onlyIfNew)
        } catch (_: Exception) {}
    }

    private fun buildClipPanel(): LinearLayout {
        val p = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        val head = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(8), 0, dp(4), dp(4)) }
        head.addView(text("Clipboard", 13f, B.CYAN, true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val clear = chip("Clear all").apply {
            setPadding(dp(14), dp(7), dp(14), dp(7))
            setOnClickListener { clips.clear(); rebuildClipList() }
        }
        val close = chip("Close").apply {
            setPadding(dp(14), dp(7), dp(14), dp(7))
            setOnClickListener { hideClips() }
        }
        head.addView(clear, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { rightMargin = dp(8) })
        head.addView(close, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { rightMargin = dp(4) })
        p.addView(head, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40)))
        clipList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val sv = ScrollView(this).apply { isVerticalScrollBarEnabled = false; addView(clipList) }
        p.addView(sv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        return p
    }

    private fun rebuildClipList() {
        clipList.removeAllViews()
        fun msg(t: String) {
            clipList.addView(text(t, 13f, B.SLATE).apply { gravity = Gravity.CENTER; setPadding(dp(16), dp(32), dp(16), 0) },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        if (!clips.available) { msg("Unlock your phone to see your clipboard"); return }
        val items = clips.list()
        if (items.isEmpty()) { msg("Nothing copied yet. Text you copy will show up here."); return }
        for (t in items) {
            val row = LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                background = shape(B.PANEL, dp(10).toFloat(), B.EDGE, dp(1))
                setPadding(dp(12), dp(6), 0, dp(6))
                minimumHeight = dp(44)
                setOnClickListener { insertClip(t) }
            }
            val tv = text(t.replace('\n', ' ').take(300), 14f, B.TEXT).apply {
                maxLines = 2; ellipsize = TextUtils.TruncateAt.END
            }
            row.addView(tv, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            val del = text("\u2715", 15f, B.SLATE, true).apply {
                gravity = Gravity.CENTER; setPadding(dp(16), dp(8), dp(16), dp(8))
                setOnClickListener { clips.remove(t); rebuildClipList() }
            }
            row.addView(del, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            clipList.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { setMargins(dp(4), dp(3), dp(4), dp(3)) })
        }
    }

    private fun toggleClips() { if (clipOpen) hideClips() else showClips() }

    private fun showClips() {
        captureClip(true)
        rebuildClipList()
        clipOpen = true
        rows.visibility = View.GONE
        clipPanel.visibility = View.VISIBLE
        clipKey.active = true; clipKey.paint(false)
        applySugVisibility()
    }

    private fun hideClips() {
        clipOpen = false
        if (::rows.isInitialized) rows.visibility = View.VISIBLE
        if (::clipPanel.isInitialized) clipPanel.visibility = View.GONE
        if (::clipKey.isInitialized) { clipKey.active = false; clipKey.paint(false) }
        applySugVisibility()
        if (sugOn) scheduleSuggest()
    }

    private fun insertClip(t: String) {
        currentInputConnection?.commitText(t, 1)
        hideClips()
        if (sugOn) scheduleSuggest()
    }

    // ---------- Next-word suggestions ----------
    /** Plain text fields only. Honors "no suggestions" and skips e-mail, web address, address and name fields. */
    private fun suggestionsAllowed(info: EditorInfo?): Boolean {
        if (info == null) return false
        val t = info.inputType
        if ((t and InputType.TYPE_MASK_CLASS) != InputType.TYPE_CLASS_TEXT) return false
        if ((t and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0) return false
        val v = t and InputType.TYPE_MASK_VARIATION
        return v != InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS &&
            v != InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS &&
            v != InputType.TYPE_TEXT_VARIATION_URI &&
            v != InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS &&
            v != InputType.TYPE_TEXT_VARIATION_PERSON_NAME
    }

    /** True before the first unlock after boot, and while the lock screen is showing. No suggestions or learning then. */
    private fun deviceLocked(): Boolean = try {
        !getSystemService(android.os.UserManager::class.java).isUserUnlocked ||
            getSystemService(android.app.KeyguardManager::class.java)?.isKeyguardLocked == true
    } catch (_: Exception) { true }

    private fun buildSugRow(): LinearLayout {
        val r = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = shape(B.PANEL, dp(10).toFloat(), B.EDGE, dp(1))
            visibility = View.GONE
        }
        sugCells.clear()
        for (i in 0 until 3) {
            if (i > 0) r.addView(View(this).apply { setBackgroundColor(B.EDGE) }, LinearLayout.LayoutParams(dp(1), dp(18)))
            val tv = text("", 14f, B.TEXT).apply {
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                includeFontPadding = false
                setPadding(dp(4), 0, dp(4), 0)
                setOnTouchListener { v, e ->
                    if (e.actionMasked == MotionEvent.ACTION_DOWN) {
                        v.setBackgroundColor(B.EDGE)
                    } else if (e.actionMasked == MotionEvent.ACTION_UP || e.actionMasked == MotionEvent.ACTION_CANCEL) {
                        v.background = null
                    }
                    false
                }
                setOnClickListener {
                    val w = sugWords.getOrNull(i)
                    if (!w.isNullOrEmpty()) pickSuggestion(w)
                }
            }
            sugCells.add(tv)
            r.addView(tv, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        }
        return r
    }

    /** Decides what the top row shows. Typing: back + suggestions. Otherwise: all icons. Mic is always there. */
    private fun applySugVisibility() {
        if (!::sugRow.isInitialized || !::backKey.isInitialized) return
        val status = ::pill.isInitialized && pill.visibility == View.VISIBLE   // status text needs the room
        val typing = typingMode && sugOn && !clipOpen
        backKey.box.visibility = if (typing && !status) View.VISIBLE else View.GONE
        for (k in topKeys) k.box.visibility = if (!typing && !status) View.VISIBLE else View.GONE
        sugRow.visibility = if (typing && !status) View.VISIBLE else View.GONE
    }

    private fun showSug(words: List<String>) {
        sugWords = words
        for (i in sugCells.indices) {
            sugCells[i].text = words.getOrNull(i) ?: ""
            sugCells[i].typeface = if (i == 0) B.FONT_B else B.FONT
        }
    }

    private fun clearSug() {
        sugSeq++
        showSug(emptyList())
    }

    /** Refresh soon. Many calls in a row (fast typing, held backspace) become one refresh. */
    private fun scheduleSuggest() {
        if (!sugOn) return
        main.removeCallbacks(sugTask)
        main.postDelayed(sugTask, 60)
    }

    private fun runSuggest() {
        if (!sugOn || clipOpen || !::sugRow.isInitialized) return
        val ic = currentInputConnection
        if (ic == null || selActive) { clearSug(); return }
        val before = ic.getTextBeforeCursor(Tok.CTX_LEN, 0)?.toString()
        val after = ic.getTextAfterCursor(1, 0)?.toString() ?: ""
        val c = if (before == null) null else Tok.context(before, after, bangla)
        if (c == null) { clearSug(); return }
        val seq = ++sugSeq
        Suggest.request(seq, bangla, c) { s, words ->
            main.post { if (s == sugSeq && sugOn) showSug(words) }
        }
    }

    /** Tap on a suggestion: replace the unfinished word and add a space. */
    private fun pickSuggestion(w: String) {
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(Tok.CTX_LEN, 0)?.toString() ?: ""
        val partialLen = before.length - Tok.partialStart(before)
        ic.beginBatchEdit()
        if (partialLen > 0) ic.deleteSurroundingText(partialLen, 0)
        ic.commitText("$w ", 1)
        ic.endBatchEdit()
        learnNow(true)
        clearSug()
        scheduleSuggest()
    }

    /** Called after a key typed text. A word-ending key (space, comma, ...) means the word before it is finished. */
    private fun afterCommit(s: String) {
        if (!sugOn) return
        if (!typingMode) { typingMode = true; applySugVisibility() }
        if (s.length == 1 && Tok.isBoundary(s[0])) learnNow(true)
        scheduleSuggest()
    }

    /**
     * Learn the finished word from what is really in the field (getTextBeforeCursor), so it matches the
     * Bangla vowel-sign logic and corrections. Never runs when [learnOn] is false (password, incognito, locked).
     */
    private fun learnNow(hasBoundary: Boolean) {
        if (!learnOn) return
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(Tok.CTX_LEN, 0)?.toString() ?: return
        val p = Tok.learnTail(before, hasBoundary, bangla) ?: return
        Suggest.learn(bangla, p.prev, p.word)
    }

    // ---------- Translate ----------
    /** Translate ALL text in the field into the language chosen in settings, using Gemini. */
    private fun onTranslate() {
        if (vstate != V.IDLE) return
        if (!getSystemService(android.os.UserManager::class.java).isUserUnlocked) {
            flash("Unlock your phone to translate", B.AMBER); return
        }
        if (passwordField) { flash("Translate is off in password fields", B.AMBER); return }
        if (prefs.key.isBlank()) {
            flash("Add your Gemini key in Ai Voice Typer", B.AMBER); openApp(); return
        }
        val ic = currentInputConnection ?: return
        val original = allText(ic)
        if (original.isBlank()) { flash("Nothing to translate", B.AMBER); return }
        val key = prefs.key
        val model = prefs.model
        val lang = prefs.transLang
        busyLabel = "Translating…"
        setV(V.BUSY)
        Thread {
            var out = ""
            var err: String? = null
            try { out = Gemini.translate(key, model, lang, original) }
            catch (e: Exception) { err = (e.message ?: "Something went wrong").take(80) }
            val o = out
            val er = err
            main.post { busyLabel = "Processing…"; setV(V.IDLE); finishTranslate(original, o, er) }
        }.start()
    }

    private fun finishTranslate(original: String, out: String, err: String?) {
        if (err != null) { flash(err, B.AMBER); return }
        if (out.isBlank()) { flash("No translation", B.AMBER); return }
        val ic = currentInputConnection ?: return
        // The person kept typing, or moved to another field, while Gemini worked: do not overwrite.
        if (allText(ic) != original) { flash("Text changed. Try again", B.AMBER); return }
        ic.beginBatchEdit()
        ic.setSelection(0, original.length)
        ic.commitText(out, 1)
        ic.endBatchEdit()
        flash("Translated to " + prefs.transLang, B.GREEN)
        if (sugOn) scheduleSuggest()
    }

    // ---------- Voice ----------
    private fun onMic() {
        when (vstate) {
            V.IDLE -> startRec()
            V.REC -> stopAndSend()
            V.BUSY -> {}
        }
    }

    private fun openApp() {
        try { startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}
    }

    private fun startRec() {
        if (!getSystemService(android.os.UserManager::class.java).isUserUnlocked) {
            flash("Unlock your phone to use voice", B.AMBER); return
        }
        if (passwordField) { flash("Mic is off in password fields", B.AMBER); return }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            flash("Allow the mic in Ai Voice Typer", B.AMBER); openApp(); return
        }
        if (prefs.key.isBlank()) {
            flash("Add your Gemini key in Ai Voice Typer", B.AMBER); openApp(); return
        }
        val ok = recorder.start(180, { main.post { if (vstate == V.REC) stopAndSend() } }, SILENCE_SEC) { heard ->
            main.post {
                if (vstate == V.REC) {
                    if (heard) stopAndSend()                       // spoke, then went quiet: send it
                    else { cancelRec(); flash("No speech heard", B.AMBER) }   // never spoke: drop it
                }
            }
        }
        if (!ok) { flash("Mic is busy", B.AMBER); return }
        setV(V.REC)
    }

    private fun stopAndSend() {
        val lang = if (bangla) "bn" else "en"
        val key = prefs.key
        val model = prefs.model
        val mode = prefs.mode
        setV(V.BUSY)
        Thread {
            var out = ""
            var err: String? = null
            try {
                val wav = recorder.stop()
                if (wav.size < 44 + 16000) err = "Too short. Try again."
                else out = Gemini.run(key, model, mode, lang, wav)
            } catch (e: Exception) {
                err = (e.message ?: "Something went wrong").take(80)
            }
            val o = out
            val er = err
            main.post { setV(V.IDLE); finishVoice(o, er) }
        }.start()
    }

    private fun finishVoice(out: String, err: String?) {
        if (err != null) { flash(err, B.AMBER); return }
        if (out.isBlank()) { flash("No speech heard", B.AMBER); return }
        val ic = currentInputConnection
        if (ic == null) {
            try {
                (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                    .setPrimaryClip(ClipData.newPlainText("voice", out))
            } catch (_: Exception) {}
            flash("Copied. Paste it.", B.CYAN)
            return
        }
        val before = ic.getTextBeforeCursor(1, 0)?.toString() ?: ""
        val needSpace = before.isNotEmpty() && !before[0].isWhitespace() && out[0] !in ",.?!;:।)"
        ic.commitText(if (needSpace) " $out" else out, 1)
        if (sugOn) scheduleSuggest()
    }

    private fun cancelRec() {
        if (vstate == V.REC) {
            try { recorder.stop() } catch (_: Exception) {}
            setV(V.IDLE)
        }
    }

    private fun setV(s: V) {
        vstate = s
        main.removeCallbacks(resetPill)
        flashing = false
        paintMic()
        refreshPill()
    }

    private fun paintMic() {
        pulse?.cancel(); pulse = null
        val k = micKey ?: return
        k.box.scaleX = 1f; k.box.scaleY = 1f; k.box.alpha = 1f
        k.active = vstate != V.IDLE
        k.setIcon(if (vstate == V.REC) R.drawable.ic_stop else R.drawable.ic_mic)
        k.paint(false)
        if (vstate == V.BUSY) k.box.alpha = 0.6f
        if (vstate == V.REC) {
            val a = ObjectAnimator.ofFloat(k.box, "scaleX", 1f, 1.08f)
            a.duration = 700
            a.repeatMode = ValueAnimator.REVERSE
            a.repeatCount = ValueAnimator.INFINITE
            a.addUpdateListener { k.box.scaleY = k.box.scaleX }
            a.start()
            pulse = a
        }
    }

    // ---------- Status pill ----------
    private fun setPill(t: String, color: Int) {
        pill.text = t
        pill.visibility = View.VISIBLE
        pill.setTextColor(color)
        pill.background = shape(B.PANEL, 1000f, color, dp(1))
        applySugVisibility()
    }

    private fun refreshPill() {
        if (!::pill.isInitialized || flashing) return
        when (vstate) {
            V.REC -> setPill("Listening… tap to stop", B.CYAN)
            V.BUSY -> setPill(busyLabel, B.CYAN)
            V.IDLE -> {
                pill.visibility = View.GONE
                applySugVisibility()
            }
        }
    }

    private fun flash(t: String, color: Int) {
        if (!::pill.isInitialized) return
        main.removeCallbacks(resetPill)
        flashing = true
        setPill(t, color)
        main.postDelayed(resetPill, 2500)
    }

    private fun refreshMode() {
        if (!::modeKey.isInitialized) return
        modeKey.setIcon(if (prefs.mode == "cleanup") R.drawable.ic_cleanup else R.drawable.ic_transcribe)
    }

    companion object {
        /** Quiet this long (seconds) = stop mic by itself. Tap still stops it any time. */
        private const val SILENCE_SEC = 6.5f
        private val INDEP: Map<Char, String> = mapOf(
            'া' to "আ", 'ি' to "ই", 'ী' to "ঈ", 'ু' to "উ",
            'ূ' to "ঊ", 'ৃ' to "ঋ", 'ে' to "এ", 'ৈ' to "ঐ",
            'ো' to "ও", 'ৌ' to "ঔ"
        )
    }
}
