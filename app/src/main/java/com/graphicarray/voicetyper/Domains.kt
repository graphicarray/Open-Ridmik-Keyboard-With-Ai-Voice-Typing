package com.graphicarray.voicetyper

/**
 * Top-bar suggestions for e-mail fields (@gmail.com ...) and web address fields (.org .net ...).
 * Pure Kotlin (no Android classes). Best match comes FIRST in every list (the keyboard puts it in the centre cell).
 */
object Domains {
    val EMAIL = listOf("@gmail.com", "@yahoo.com", "@outlook.com", "@hotmail.com", "@icloud.com")
    val WEB = listOf(".com", ".org", ".net", ".co", ".edu", ".bd", ".com.bd")
    private val WEB_DEFAULT = listOf(".org", ".net", ".co")   // ".com" already has its own key
    private const val MAX = 3

    private fun lastToken(before: String): String =
        before.substring(before.lastIndexOfAny(charArrayOf(' ', '\n', '\t', ',', ';')) + 1)

    // ---------- E-mail ----------
    /** How many characters (the "@..." tail) a picked domain must replace. */
    fun emailTail(before: String): Int {
        val t = lastToken(before)
        val at = t.indexOf('@')
        return if (at < 0) 0 else t.length - at
    }

    fun email(before: String): List<String> {
        val t = lastToken(before)
        if (t.isEmpty()) return emptyList()
        val at = t.indexOf('@')
        if (at < 0) return EMAIL.take(MAX)
        if (t.lastIndexOf('@') != at) return emptyList()
        val part = t.substring(at).lowercase()
        return EMAIL.filter { it.startsWith(part) && it.length > part.length }.take(MAX)
    }

    // ---------- Web address ----------
    private class Web(val words: List<String>, val tail: Int)

    private fun webState(before: String): Web {
        val none = Web(emptyList(), 0)
        if (before.isEmpty() || before.any { it.isWhitespace() }) return none
        var t = before
        val s = t.indexOf("://")
        if (s >= 0) t = t.substring(s + 3)
        if (t.isEmpty() || t.any { it == '/' || it == '?' || it == '#' || it == ':' }) return none
        val dot = t.lastIndexOf('.')
        if (dot < 0) return Web(WEB_DEFAULT, 0)
        if (dot == 0) return none
        if (dot == 3 && t.startsWith("www.", ignoreCase = true)) {
            return if (t.length > 4) Web(WEB_DEFAULT, 0) else none   // "www." alone: nothing yet
        }
        val seg = t.substring(dot).lowercase()
        if (seg == ".") return Web(WEB.take(MAX), 1)
        val m = WEB.filter { it.startsWith(seg) && it.length > seg.length }
        if (m.isNotEmpty()) return Web(m.take(MAX), seg.length)
        if (WEB.contains(seg)) return none           // domain already complete (".org", ".bd" ...)
        return Web(WEB_DEFAULT, 0)                   // ".google" is a name, not an ending yet
    }

    fun web(before: String): List<String> = webState(before).words

    /** How many characters (".co" ...) a picked ending must replace. */
    fun webTail(before: String): Int = webState(before).tail
}
