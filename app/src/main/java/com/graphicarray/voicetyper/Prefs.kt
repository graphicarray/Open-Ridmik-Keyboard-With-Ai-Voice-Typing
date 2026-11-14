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
    /** "en" or "bn" */
    var lang: String get() = sp.getString("lang", "en") ?: "en"; set(v) = sp.edit().putString("lang", v).apply()
    /** Keyboard layout: "jatio" or "en" */
    var layout: String get() = sp.getString("layout", "jatio") ?: "jatio"; set(v) = sp.edit().putString("layout", v).apply()
    /** Next-word suggestions on/off (default on). */
    var suggest: Boolean get() = sp.getBoolean("suggest", true); set(v) = sp.edit().putBoolean("suggest", v).apply()
    /** Cached model list loaded from the Gemini API */
    var models: List<String>
        get() = (sp.getString("models", "") ?: "").split("\n").filter { it.isNotBlank() }
        set(v) = sp.edit().putString("models", v.joinToString("\n")).apply()
}
