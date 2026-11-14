package com.graphicarray.voicetyper

import android.content.Context
import android.os.UserManager
import org.json.JSONArray

/**
 * Clipboard history, kept privately on this device. Newest first, latest [MAX] items.
 * Unavailable until the phone is unlocked (the keyboard can start before unlock).
 */
class ClipStore(private val ctx: Context) {
    private fun unlocked(): Boolean = ctx.getSystemService(UserManager::class.java).isUserUnlocked
    private fun sp() = ctx.getSharedPreferences("ga_clips", Context.MODE_PRIVATE)

    val available: Boolean get() = unlocked()

    fun list(): List<String> {
        if (!unlocked()) return emptyList()
        return try {
            val a = JSONArray(sp().getString("items", "[]"))
            List(a.length()) { a.getString(it) }
        } catch (_: Exception) { emptyList() }
    }

    /** Adds [text] at the top. If it already exists it is moved to the top, unless [onlyIfNew]. */
    fun add(text: String, onlyIfNew: Boolean = false) {
        if (!unlocked()) return
        val t = text.take(MAX_LEN)
        if (t.isBlank()) return
        val cur = list()
        if (onlyIfNew && cur.contains(t)) return
        save((listOf(t) + cur.filter { it != t }).take(MAX))
    }

    fun remove(text: String) { if (unlocked()) save(list().filter { it != text }) }

    fun clear() { if (unlocked()) save(emptyList()) }

    private fun save(items: List<String>) {
        val a = JSONArray()
        items.forEach { a.put(it) }
        sp().edit().putString("items", a.toString()).apply()
    }

    private companion object { const val MAX = 30; const val MAX_LEN = 20000 }
}
