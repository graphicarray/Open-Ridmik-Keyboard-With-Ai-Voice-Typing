package com.graphicarray.voicetyper

import android.content.Context

class Prefs(c: Context) {
    // Stored in device-protected storage so the services (marked directBootAware)
    // can read settings after a reboot, before the user unlocks the phone.
    private val sp = run {
        val dp = c.createDeviceProtectedStorageContext()
        try { dp.moveSharedPreferencesFrom(c, NAME) } catch (_: Exception) { /* locked or nothing to migrate */ }
        dp.getSharedPreferences(NAME, Context.MODE_PRIVATE)
    }
    private companion object { const val NAME = "ga_prefs" }
    var key: String get() = sp.getString("key", "") ?: ""; set(v) = sp.edit().putString("key", v.trim()).apply()
    var model: String get() = sp.getString("model", "gemini-2.5-flash") ?: "gemini-2.5-flash"; set(v) = sp.edit().putString("model", v.trim()).apply()
    /** "cleanup" or "transcribe" */
    var mode: String get() = sp.getString("mode", "cleanup") ?: "cleanup"; set(v) = sp.edit().putString("mode", v).apply()
    /** Keyboard layout: "jatio" or "en" */
    var layout: String get() = sp.getString("layout", "jatio") ?: "jatio"; set(v) = sp.edit().putString("layout", v).apply()
    /** Language the Translate key converts the field text into (English name, see Gemini.TRANSLATE_LANGS). */
    var transLang: String get() = sp.getString("transLang", "English") ?: "English"; set(v) = sp.edit().putString("transLang", v).apply()
    /** Next-word suggestions on/off (default on). */
    var suggest: Boolean get() = sp.getBoolean("suggest", true); set(v) = sp.edit().putBoolean("suggest", v).apply()
    /** Preferences popup: capitalize the first word of each sentence (English layout). */
    var autoCap: Boolean get() = sp.getBoolean("autoCap", true); set(v) = sp.edit().putBoolean("autoCap", v).apply()
    /** Preferences popup: extra row of digits above the letters. */
    var numberRow: Boolean get() = sp.getBoolean("numberRow", false); set(v) = sp.edit().putBoolean("numberRow", v).apply()
    /** Preferences popup: allow the keyboard height setting. */
    var resize: Boolean get() = sp.getBoolean("resize", true); set(v) = sp.edit().putBoolean("resize", v).apply()
    /** Keyboard height in portrait, percent (70..130, step 5). */
    var kbHeight: Int get() = sp.getInt("kbHeight", 100).coerceIn(70, 130); set(v) = sp.edit().putInt("kbHeight", v.coerceIn(70, 130)).apply()
    /** Recently used emojis, newest first (emoji panel). */
    var recentEmoji: List<String>
        get() = (sp.getString("recentEmoji", "") ?: "").split("\n").filter { it.isNotEmpty() }
        set(v) = sp.edit().putString("recentEmoji", v.joinToString("\n")).apply()
    /** Cached model list loaded from the Gemini API */
    var models: List<String>
        get() = (sp.getString("models", "") ?: "").split("\n").filter { it.isNotBlank() }
        set(v) = sp.edit().putString("models", v.joinToString("\n")).apply()
}
