package com.graphicarray.voicetyper

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView

class DictationService : AccessibilityService() {
    private enum class S { IDLE, REC, BUSY }

    private lateinit var wm: WindowManager
    private val main = Handler(Looper.getMainLooper())
    private val recorder = WavRecorder()
    private var state = S.IDLE
    private var btn: FrameLayout? = null
    private var icon: ImageView? = null
    private var pill: TextView? = null
    private var pulse: ObjectAnimator? = null
    private val size get() = dp(52)

    override fun onServiceConnected() { wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager }
    override fun onInterrupt() {}
    override fun onDestroy() { hideBtn(); hidePill(); super.onDestroy() }

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        if (state != S.IDLE) return
        refresh()
    }

    private fun focusedField(): AccessibilityNodeInfo? {
        val f = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return null
        return if (f.isEditable && !f.isPassword) f else null
    }

    private fun refresh() {
        val f = focusedField()
        if (f == null) { hideBtn(); return }
        val r = Rect(); f.getBoundsInScreen(r)
        showBtn(r)
    }

    // ---------- Overlay UI (brand: gradient pill/circle, halo ring, Abyss icon) ----------
    private fun showBtn(r: Rect) {
        val dm = resources.displayMetrics
        val x = (r.right - size - dp(6)).coerceIn(dp(4), dm.widthPixels - size - dp(4))
        var y = r.top - size - dp(2)
        if (y < dp(24)) y = (r.centerY() - size / 2).coerceAtLeast(dp(24))
        val existing = btn
        if (existing != null) {
            val lp = existing.layoutParams as WindowManager.LayoutParams
            lp.x = x; lp.y = y
            try { wm.updateViewLayout(existing, lp) } catch (_: Exception) {}
            return
        }
        val b = FrameLayout(this).apply {
            background = oval(B.HALO)                      // halo ring
            val inner = FrameLayout(context).apply { background = gradOval() }
            addView(inner, FrameLayout.LayoutParams(size - dp(10), size - dp(10), Gravity.CENTER))
            val i = ImageView(context).apply { setImageResource(R.drawable.ic_mic) }
            icon = i
            addView(i, FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER))
            contentDescription = "Dictate"
            setOnClickListener { onTap() }
            // Long-press the mic to switch English <-> Bangla
            setOnLongClickListener {
                if (state == S.IDLE) {
                    val p = Prefs(context)
                    p.lang = if (p.lang == "en") "bn" else "en"
                    toast(if (p.lang == "bn") "Language: Bangla" else "Language: English")
                }
                true
            }
        }
        val lp = WindowManager.LayoutParams(size, size, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.START; this.x = x; this.y = y
        }
        btn = b
        try { wm.addView(b, lp) } catch (_: Exception) { btn = null }
    }

    private fun hideBtn() {
        pulse?.cancel(); pulse = null
        btn?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        btn = null; icon = null
    }

    private fun toast(msg: String, autoHideMs: Long = 2500) {
        hidePill()
        val t = text(msg, 13f, B.CYAN, true).apply {
            setPadding(dp(16), dp(8), dp(16), dp(8))
            background = shape(B.PANEL, 1000f, B.CYAN, dp(1))   // status pill: Cyan outline + text
        }
        val lp = WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            val btnY = (btn?.layoutParams as? WindowManager.LayoutParams)?.y ?: dp(120)
            y = (btnY - dp(44)).coerceAtLeast(dp(32))
        }
        pill = t
        try { wm.addView(t, lp) } catch (_: Exception) { pill = null }
        if (autoHideMs > 0) main.postDelayed({ if (pill === t) hidePill() }, autoHideMs)
    }

    private fun hidePill() { pill?.let { try { wm.removeView(it) } catch (_: Exception) {} }; pill = null }

    // ---------- Flow ----------
    private fun onTap() {
        when (state) {
            S.IDLE -> startRec()
            S.REC -> stopAndSend()
            S.BUSY -> {}
        }
    }

    private fun startRec() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            toast("Allow the mic in Ai Voice Typer")
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return
        }
        if (Prefs(this).key.isBlank()) {
            toast("Add your Gemini key in Ai Voice Typer")
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return
        }
        if (!recorder.start(180, onLimit = { main.post { if (state == S.REC) stopAndSend() } })) { toast("Mic is busy"); return }
        state = S.REC
        icon?.setImageResource(R.drawable.ic_stop)
        pulse = ObjectAnimator.ofFloat(btn, "scaleX", 1f, 1.12f).apply {
            duration = 700; repeatMode = ValueAnimator.REVERSE; repeatCount = ValueAnimator.INFINITE
            addUpdateListener { btn?.scaleY = btn?.scaleX ?: 1f }; start()
        }
        toast(if (Prefs(this).lang == "bn") "Listening (Bangla)… tap to stop" else "Listening (English)… tap to stop", 0)
    }

    private fun stopAndSend() {
        pulse?.cancel(); btn?.scaleX = 1f; btn?.scaleY = 1f
        state = S.BUSY
        icon?.setImageResource(R.drawable.ic_mic); icon?.alpha = 0.5f
        val wav = recorder.stop()
        val prefs = Prefs(this)
        toast(if (prefs.mode == "cleanup") "Cleaning up…" else "Transcribing…", 0)
        Thread {
            val result = try { Result.success(Gemini.run(prefs.key, prefs.model, prefs.mode, prefs.lang, wav)) }
            catch (e: Exception) { Result.failure(e) }
            main.post { finish(result) }
        }.start()
    }

    private fun finish(r: Result<String>) {
        state = S.IDLE; icon?.alpha = 1f
        r.onSuccess { t ->
            if (t.isBlank()) toast("Didn't catch that. Try again")
            else { toast(if (insert(t)) "Done" else "Copied. Long-press to paste"); }
        }.onFailure { toast("Error: " + (it.message ?: "try again").take(80), 4000) }
        main.postDelayed({ if (state == S.IDLE) refresh() }, 300)
    }

    /** Inserts text at the cursor of the focused field. Falls back to clipboard paste. */
    private fun insert(t: String): Boolean {
        val f = focusedField()
        if (f != null) {
            val existing = if (f.isShowingHintText) "" else (f.text?.toString() ?: "")
            var s = f.textSelectionStart; var e = f.textSelectionEnd
            if (s < 0 || e < 0 || s > existing.length || e > existing.length) { s = existing.length; e = s }
            val a = minOf(s, e); val b = maxOf(s, e)
            val sep = if (a > 0 && !existing[a - 1].isWhitespace()) " " else ""
            val ins = sep + t
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, existing.substring(0, a) + ins + existing.substring(b))
            }
            if (f.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
                val pos = a + ins.length
                val sel = Bundle().apply {
                    putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, pos)
                    putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, pos)
                }
                f.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, sel)
                return true
            }
            (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("voice", t))
            return f.performAction(AccessibilityNodeInfo.ACTION_PASTE)
        }
        (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("voice", t))
        return false
    }
}
