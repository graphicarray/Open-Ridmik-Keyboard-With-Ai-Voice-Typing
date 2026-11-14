package com.graphicarray.voicetyper

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.UserManager
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

/**
 * Next-word suggestions, 100% on this device. Nothing here touches the network.
 *
 * - All model work (load, learn, suggest, save, clear) runs on ONE background thread, so typing never waits for it.
 * - Learned data is credential-protected app storage (filesDir). It is only read or written after the user
 *   unlocked the phone. While locked, only the built-in starter list is used and nothing is learned.
 * - Same process for the keyboard and the settings screen, so "Clear learned words" also clears memory.
 */
object Suggest {
    private val ex: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "ga-suggest").apply { isDaemon = true; priority = Thread.NORM_PRIORITY - 1 }
    }
    private val main = Handler(Looper.getMainLooper())
    private var app: Context? = null
    private val en = WordModel(false)
    private val bn = WordModel(true)
    @Volatile private var latest = 0
    @Volatile private var saveQueued = false
    private val saveTask = Runnable { saveQueued = false; flush() }

    fun init(c: Context) { if (app == null) app = c.applicationContext }

    private fun unlocked(): Boolean = try {
        app?.getSystemService(UserManager::class.java)?.isUserUnlocked == true
    } catch (_: Exception) { false }

    private fun file(isBn: Boolean): File = File(app!!.filesDir, if (isBn) "ga_words_bn.txt" else "ga_words_en.txt")

    /** The model for a language, loaded from disk on first use (only once the phone is unlocked). */
    private fun model(isBn: Boolean): WordModel {
        val m = if (isBn) bn else en
        if (!m.loaded && unlocked()) m.load(file(isBn))
        return m
    }

    private fun bg(block: () -> Unit) {
        try {
            ex.execute { try { block() } catch (_: Exception) { /* never crash the keyboard */ } }
        } catch (_: RejectedExecutionException) { }
    }

    /** Load a language in the background so the first suggestion is instant. */
    fun warm(isBn: Boolean) = bg { model(isBn) }

    /** Ask for suggestions. [done] runs on the background thread; post to the main thread yourself. */
    fun request(seq: Int, isBn: Boolean, c: Tok.Ctx, done: (Int, List<String>) -> Unit) {
        latest = seq
        bg {
            if (seq == latest) done(seq, model(isBn).suggest(c).map { Tok.show(it, c, isBn) })
        }
    }

    /** Learn one finished word (and the word before it). Ignored while the phone is locked. */
    fun learn(isBn: Boolean, prev: String?, w: String) = bg {
        val m = model(isBn)
        if (!m.loaded) return@bg
        m.learn(prev, w)
        if (!saveQueued) { saveQueued = true; main.postDelayed(saveTask, 20_000) }
    }

    /** Write unsaved changes to disk. */
    fun flush() = bg {
        if (unlocked()) {
            if (en.loaded) en.save(file(false))
            if (bn.loaded) bn.save(file(true))
        }
    }

    /** Forget everything learned (both languages). [done] runs on the main thread. */
    fun clearAll(done: () -> Unit) = bg {
        en.clear(); bn.clear()
        if (unlocked()) {
            for (isBn in listOf(false, true)) {
                try {
                    val f = file(isBn)
                    f.delete()
                    File(f.parentFile, f.name + ".tmp").delete()
                } catch (_: Exception) { }
            }
        }
        main.post { done() }
    }
}
