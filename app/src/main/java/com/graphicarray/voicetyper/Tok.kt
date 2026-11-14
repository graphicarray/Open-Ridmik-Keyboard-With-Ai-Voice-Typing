package com.graphicarray.voicetyper

import java.util.Locale

/**
 * Text analysis for next-word suggestions. Pure Kotlin (no Android classes).
 * It only ever looks at the text the editor really holds (getTextBeforeCursor),
 * so it stays correct with the Bangla vowel-sign logic, backspace, paste and voice.
 */
object Tok {
    /** Pseudo word meaning "start of a sentence". */
    const val START = "<s>"
    /** How many characters before the cursor we read. */
    const val CTX_LEN = 60
    const val MAX_WORD = 24

    /**
     * What the user is typing now.
     * [partial] = unfinished word as typed ("" after a space), [key] = it normalised for matching,
     * [prev] = the word before it (or [START]), [cap] = show suggestions with a capital first letter.
     */
    class Ctx(val partial: String, val key: String, val prev: String?, val cap: Boolean)

    /** A finished word and the word before it, ready to learn. */
    class Learn(val prev: String?, val word: String)

    private const val SAFE_BEFORE = "\"“‘([{-–—,;:!?।…"
    private const val SENTENCE_END = ".!?।"
    private const val BOUNDARY = ",.;:!?।)]}\""

    fun isApos(c: Char) = c == '\'' || c == '’'
    private fun isSpace(c: Char) = c == ' ' || c == '\t' || c == ' '

    /** Letters, combining marks (Bangla vowel signs, hasant, chandrabindu), zero-width joiners, apostrophes. */
    fun isWordChar(c: Char): Boolean {
        if (Character.isLetter(c)) return true
        val t = Character.getType(c)
        if (t == Character.NON_SPACING_MARK.toInt() || t == Character.COMBINING_SPACING_MARK.toInt()) return true
        return isApos(c) || c == '‌' || c == '‍'
    }

    /** A key that ends a word (space, enter, tab, punctuation). */
    fun isBoundary(c: Char): Boolean = c.isWhitespace() || isSpace(c) || BOUNDARY.indexOf(c) >= 0

    private fun safeBefore(c: Char): Boolean = c.isWhitespace() || isSpace(c) || isApos(c) || SAFE_BEFORE.indexOf(c) >= 0

    /** Same spelling the keyboard types: precomposed ড় ঢ় য় (a nukta pair becomes one letter). */
    fun canonBn(s: String): String =
        s.replace("ড়", "ড়").replace("ঢ়", "ঢ়").replace("য়", "য়")

    private fun isBn(ch: Char) = ch in 'ঀ'..'৿' || ch == '‌' || ch == '‍'
    private fun isLatin(ch: Char) = ch in 'a'..'z' || ch in 'ß'..'ɏ' || ch == '\''

    /** Lowercase / canonical form used for matching and storage. */
    private fun fold(s: String, bn: Boolean): String {
        val t = s.replace('’', '\'')
        return if (bn) canonBn(t) else t.lowercase(Locale.ROOT)
    }

    /** True if every character fits the language (English: Latin, Bangla: Bengali block). */
    private fun fits(key: String, bn: Boolean): Boolean {
        for (ch in key) if (if (bn) !isBn(ch) else !isLatin(ch)) return false
        return true
    }

    /** A word we are willing to learn, in stored form; null if it should not be learned. */
    fun norm(raw: String, bn: Boolean): String? {
        val t = fold(raw, bn).trim('\'')
        if (t.isEmpty() || t.length > MAX_WORD) return null
        if (!fits(t, bn)) return null
        if (bn && t.none { it in 'ক'..'হ' || it in 'অ'..'ঔ' || it in 'ড়'..'য়' || it == 'ৎ' }) return null
        if (t.length < 2 && !(t == "a" || t == "i" || t == "ও" || t == "এ")) return null
        // reject "aaaa", "hahahahaha"-style noise
        var run = 1
        for (i in 1 until t.length) {
            run = if (t[i] == t[i - 1]) run + 1 else 1
            if (run >= 4) return null
        }
        return t
    }

    /** Index where the trailing word starts (leading quote marks are not part of the word). */
    fun partialStart(before: String): Int {
        var i = before.length
        while (i > 0 && isWordChar(before[i - 1])) i--
        while (i < before.length && isApos(before[i])) i++
        return i
    }

    private fun tokenStart(s: String, end: Int): Int {
        var k = end
        while (k > 0 && isWordChar(s[k - 1])) k--
        while (k < end && isApos(s[k])) k++
        return k
    }

    /** Word at s[k, end) in stored form, or null if it is not a clean word (digits, e-mail pieces, cut off...). */
    private fun tokenAt(s: String, k: Int, end: Int, bn: Boolean, truncated: Boolean): String? {
        if (k >= end) return null
        if (k == 0) { if (truncated) return null } else if (!safeBefore(s[k - 1])) return null
        return norm(s.substring(k, end), bn)
    }

    /** The word before position [from] (START at the start of a sentence), or null if there is none we trust. */
    private fun prevWord(s: String, from: Int, bn: Boolean, truncated: Boolean): String? {
        var j = from
        while (j > 0 && isSpace(s[j - 1])) j--
        if (j == 0) return if (truncated) null else START
        val ch = s[j - 1]
        if (ch == '\n') return START
        if (j == from) return null
        if (SENTENCE_END.indexOf(ch) >= 0) return START
        if (isWordChar(ch)) return tokenAt(s, tokenStart(s, j), j, bn, truncated)
        return null
    }

    /**
     * Work out what to suggest. [before] = text before the cursor (last [CTX_LEN] chars), [after] = next char.
     * Null means: show nothing here.
     */
    fun context(before: String, after: String, bn: Boolean): Ctx? {
        if (after.isNotEmpty() && isWordChar(after[0])) return null   // cursor is inside a word
        val n = before.length
        val truncated = n >= CTX_LEN
        val i = partialStart(before)
        val partial = before.substring(i)
        if (partial.length > MAX_WORD) return null
        if (partial.isEmpty()) {
            if (n > 0 && !(before[n - 1].isWhitespace() || isSpace(before[n - 1]))) return null
            val prev = prevWord(before, n, bn, truncated)
            return Ctx("", "", prev, !bn && prev == START)
        }
        if (i > 0 && !safeBefore(before[i - 1])) return null
        val key = fold(partial, bn)
        if (!fits(key, bn)) return null
        return Ctx(partial, key, prevWord(before, i, bn, truncated), !bn && partial[0].isUpperCase())
    }

    /**
     * The word that was just finished, plus the word before it.
     * [hasBoundary] = the last char of [before] is the key that ended the word (space, comma, ...).
     * Returns null if nothing should be learned (double space, "go ." , digits, e-mail pieces, wrong script...).
     */
    fun learnTail(before: String, hasBoundary: Boolean, bn: Boolean): Learn? {
        var end = before.length
        if (hasBoundary) {
            if (end == 0 || !isBoundary(before[end - 1])) return null
            end--
        }
        if (end == 0 || !isWordChar(before[end - 1])) return null
        val truncated = before.length >= CTX_LEN
        val k = tokenStart(before, end)
        val w = tokenAt(before, k, end, bn, truncated) ?: return null
        return Learn(prevWord(before, k, bn, truncated), w)
    }

    /** Make a stored word look right: "i'm" -> "I'm", capital at the start of a sentence. */
    fun show(w: String, c: Ctx, bn: Boolean): String {
        if (bn || w.isEmpty()) return w
        var s = w
        if (s == "i") s = "I" else if (s.startsWith("i'")) s = "I" + s.substring(1)
        if (c.cap) s = s.substring(0, 1).uppercase(Locale.ROOT) + s.substring(1)
        return s
    }
}
