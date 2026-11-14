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

    private lateinit var frame: FrameLayout
    private lateinit var root: LinearLayout
    private lateinit var pvBubble: TextView
    private var pvOwner: Key? = null
    private lateinit var clips: ClipStore
    private var cm: ClipboardManager? = null
    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener { captureClip(false) }
    private lateinit var clipKey: Key
    private lateinit var clipPanel: LinearLayout
    private lateinit var clipList: LinearLayout
    private var clipOpen = false
    private lateinit var rows: LinearLayout
    private lateinit var pill: TextView
    private lateinit var modeChip: TextView
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
            setPadding(dp(4), dp(4), dp(4), dp(6))
        }
        pill = text("", 12.5f, B.CYAN, true).apply { setPadding(dp(14), dp(6), dp(14), dp(6)) }
        modeChip = chip("").apply {
            setPadding(dp(14), dp(7), dp(14), dp(7))
            setOnClickListener {
                prefs.mode = if (prefs.mode == "cleanup") "transcribe" else "cleanup"
                refreshMode()
            }
        }
        strip.addView(pill, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val ck = Key(Kind.SPECIAL, null, R.drawable.ic_clip, 14f, 1f)
        bind(ck) { toggleClips() }
        clipKey = ck
        strip.addView(ck.box, LinearLayout.LayoutParams(dp(44), dp(36)).apply { leftMargin = dp(8) })
        strip.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        strip.addView(modeChip, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { rightMargin = dp(8) })
        val mk = Key(Kind.MIC, null, R.drawable.ic_mic, 14f, 1f)
        bind(mk) { onMic() }
        micKey = mk
        strip.addView(mk.box, LinearLayout.LayoutParams(dp(60), dp(38)))
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
        if (!::rows.isInitialized) return
        hideClips()
        hidePreview(null)
        shift = Shift.OFF
        if (page != 0) { page = 0; render() } else applyShift()
        refreshMode()
        refreshPill()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        stopRepeat()
        cancelRec()
        if (::pvBubble.isInitialized) hidePreview(null)
        if (::clipPanel.isInitialized) hideClips()
        super.onFinishInputView(finishingInput)
    }

    override fun onDestroy() {
        stopRepeat()
        cancelRec()
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

    private inner class Key(val kind: Kind, label: String?, icon: Int, sp: Float, val weight: Float, hintText: String? = null) {
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
                box.addView(img, FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER))
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
        val sp = if (symbols) 22f else 24f

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

        val comma = Key(Kind.SPECIAL, ",", 0, 22f, 1f)
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
                    if (Math.abs(e.rawX - downX) > dp(48)) switchLayout() else currentInputConnection?.commitText(" ", 1)
                    true
                }
                MotionEvent.ACTION_CANCEL -> { space.paint(false); true }
                else -> true
            }
        }
        bottom.addView(space.box, cellLp(sw))

        val dot = if (bangla && !symbols) "।" else "."
        val dotK = Key(Kind.SPECIAL, dot, 0, 22f, 1f)
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
    }

    private fun commitRaw(s: String) { currentInputConnection?.commitText(s, 1) }

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
    }

    private fun enter() {
        val ic = currentInputConnection ?: return
        val ei = currentInputEditorInfo
        val act = if (ei != null) (ei.imeOptions and EditorInfo.IME_MASK_ACTION) else EditorInfo.IME_ACTION_NONE
        val plain = ei == null || (ei.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0 ||
            act == EditorInfo.IME_ACTION_NONE || act == EditorInfo.IME_ACTION_UNSPECIFIED
        if (plain) sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER) else ic.performEditorAction(act)
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
    }

    private fun hideClips() {
        clipOpen = false
        if (::rows.isInitialized) rows.visibility = View.VISIBLE
        if (::clipPanel.isInitialized) clipPanel.visibility = View.GONE
        if (::clipKey.isInitialized) { clipKey.active = false; clipKey.paint(false) }
    }

    private fun insertClip(t: String) {
        currentInputConnection?.commitText(t, 1)
        hideClips()
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
        val ok = recorder.start(180) { main.post { if (vstate == V.REC) stopAndSend() } }
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
        if (::clipKey.isInitialized) clipKey.box.visibility = if (t.length > 14) View.GONE else View.VISIBLE
        pill.setTextColor(color)
        pill.background = shape(B.PANEL, 1000f, color, dp(1))
    }

    private fun refreshPill() {
        if (!::pill.isInitialized || flashing) return
        when (vstate) {
            V.REC -> setPill("Listening… tap to stop", B.CYAN)
            V.BUSY -> setPill("Processing…", B.CYAN)
            V.IDLE -> setPill(if (bangla) "Bangla voice" else "English voice", B.CYAN)
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
        if (!::modeChip.isInitialized) return
        modeChip.text = if (prefs.mode == "cleanup") "Clean up" else "Transcribe"
    }

    companion object {
        private val INDEP: Map<Char, String> = mapOf(
            'া' to "আ", 'ি' to "ই", 'ী' to "ঈ", 'ু' to "উ",
            'ূ' to "ঊ", 'ৃ' to "ঋ", 'ে' to "এ", 'ৈ' to "ঐ",
            'ো' to "ও", 'ৌ' to "ঔ"
        )
    }
}
