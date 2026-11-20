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
    // Banglish = Bangla words typed with English letters ("ami", "bhalo"). Own store, no starter list.
    private val bl = WordModel(false, starter = false)
    @Volatile private var latest = 0
    @Volatile private var saveQueued = false
    private val saveTask = Runnable { saveQueued = false; flush() }

    fun init(c: Context) { if (app == null) app = c.applicationContext }

    private fun unlocked(): Boolean = try {
        app?.getSystemService(UserManager::class.java)?.isUserUnlocked == true
    } catch (_: Exception) { false }

    private fun file(name: String): File = File(app!!.filesDir, "ga_words_$name.txt")
    private fun file(isBn: Boolean): File = file(if (isBn) "bn" else "en")

    /** The model for a language, loaded from disk on first use (only once the phone is unlocked). */
    private fun model(isBn: Boolean): WordModel {
        val m = if (isBn) bn else en
        if (!m.loaded && unlocked()) m.load(file(isBn))
        return m
    }

    private fun blModel(): WordModel {
        if (!bl.loaded && unlocked()) bl.load(file("bl"))
        return bl
    }

    private fun bg(block: () -> Unit) {
        try {
            ex.execute { try { block() } catch (_: Exception) { /* never crash the keyboard */ } }
        } catch (_: RejectedExecutionException) { }
    }

    /** Load a language in the background so the first suggestion is instant. */
    fun warm(isBn: Boolean) = bg { model(isBn); if (!isBn) blModel() }

    /** Ask for suggestions. [done] runs on the background thread; post to the main thread yourself. */
    fun request(seq: Int, isBn: Boolean, c: Tok.Ctx, done: (Int, List<String>) -> Unit) {
        latest = seq
        bg {
            if (seq == latest) {
                val words = if (isBn) model(true).suggest(c) else mergeEnglish(c)
                done(seq, words.map { Tok.show(it, c, isBn) })
            }
        }
    }

    /** English layout: best words from the English list and from your Banglish words, mixed by score. */
    private fun mergeEnglish(c: Tok.Ctx): List<String> {
        val e = model(false)
        val b = blModel()
        val all = LinkedHashMap<String, Int>()
        for (w in e.suggest(c)) all[w] = e.scoreOf(c.prev, w)
        for (w in b.suggest(c)) {
            val s = b.scoreOf(c.prev, w)
            if ((all[w] ?: -1) < s) all[w] = s
        }
        return all.entries.sortedByDescending { it.value }.take(3).map { it.key }
    }

    /** Learn one finished word (and the word before it). Ignored while the phone is locked. */
    fun learn(isBn: Boolean, prev: String?, w: String) = bg {
        // English layout: a word not in the English starter list is Banglish (or a name): keep it in its own store.
        val m = if (isBn || Starter.uni(false).containsKey(w)) model(isBn) else blModel()
        if (!m.loaded) return@bg
        m.learn(prev, w)
        if (!saveQueued) { saveQueued = true; main.postDelayed(saveTask, 20_000) }
    }

    /** Write unsaved changes to disk. */
    fun flush() = bg {
        if (unlocked()) {
            if (en.loaded) en.save(file(false))
            if (bn.loaded) bn.save(file(true))
            if (bl.loaded) bl.save(file("bl"))
        }
    }

    /** Forget everything learned (Bangla, English, Banglish). [done] runs on the main thread. */
    fun clearAll(done: () -> Unit) = bg {
        en.clear(); bn.clear(); bl.clear()
        if (unlocked()) {
            for (name in listOf("en", "bn", "bl")) {
                try {
                    val f = file(name)
                    f.delete()
                    File(f.parentFile, f.name + ".tmp").delete()
                } catch (_: Exception) { }
            }
        }
        main.post { done() }
    }
}
