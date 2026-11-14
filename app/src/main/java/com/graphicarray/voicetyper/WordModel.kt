package com.graphicarray.voicetyper

import java.io.File

/**
 * Count-based word model for ONE language (not AI): word counts + word-pair counts.
 * Not thread safe. [Suggest] only touches it from its single background thread.
 * Pure Kotlin (no Android classes).
 *
 * Score = learned count * 8 + starter strength (1..7), so words you really use beat the starter list.
 * Learned words/pairs show only after they were seen [MIN_SHOW] times (filters one-off typos).
 */
class WordModel(private val bn: Boolean) {
    var loaded = false
        private set
    private var dirty = false
    private var events = 0
    private val uni = HashMap<String, Int>()
    private val bi = HashMap<String, Int>()     // "prev\tnext" -> count

    // ---------- Learning ----------
    fun learn(prev: String?, w: String) {
        if (!loaded) return
        uni[w] = minOf(MAX_COUNT, (uni[w] ?: 0) + 1)
        if (prev != null) {
            val k = prev + "\t" + w
            bi[k] = minOf(MAX_COUNT, (bi[k] ?: 0) + 1)
        }
        dirty = true
        if (uni.size > MAX_UNI) shrink(uni, MAX_UNI)
        if (bi.size > MAX_BI) shrink(bi, MAX_BI)
        if (++events >= DECAY_EVERY) {
            events = 0
            shrink(uni, 0); shrink(bi, 0)
        }
    }

    /** Halve all counts and drop what falls to 0 (forgets rare entries). If still above [cap], keep the best 3/4. */
    private fun shrink(m: HashMap<String, Int>, cap: Int) {
        val it = m.entries.iterator()
        while (it.hasNext()) {
            val e = it.next()
            val v = e.value / 2
            if (v <= 0) it.remove() else e.setValue(v)
        }
        if (cap > 0 && m.size > cap) {
            val keep = m.entries.sortedByDescending { it.value }.take(cap * 3 / 4).map { it.key to it.value }
            m.clear()
            for ((k, v) in keep) m[k] = v
        }
    }

    fun clear() {
        uni.clear(); bi.clear(); events = 0; dirty = false; loaded = true
    }

    // ---------- Suggestions ----------
    private fun uniScore(w: String): Int = (uni[w] ?: 0) * 8 + (Starter.uni(bn)[w] ?: 0)

    private fun biScore(prev: String?, w: String): Int {
        if (prev == null) return 0
        val k = prev + "\t" + w
        return (bi[k] ?: 0) * 8 + (Starter.bi(bn)[k] ?: 0)
    }

    private fun score(prev: String?, w: String): Int = uniScore(w) + 3 * biScore(prev, w)

    /** Keeps the best [n] (score high, then short, then alphabetical). */
    private class Top(private val n: Int) {
        val words = ArrayList<String>()
        private val scores = ArrayList<Int>()
        val size: Int get() = words.size
        fun has(w: String) = words.contains(w)
        private fun better(s: Int, w: String, i: Int): Boolean {
            val o = scores[i]
            if (s != o) return s > o
            val ow = words[i]
            if (w.length != ow.length) return w.length < ow.length
            return w < ow
        }
        fun add(w: String, s: Int) {
            var pos = words.size
            for (i in words.indices) if (better(s, w, i)) { pos = i; break }
            if (pos >= n) return
            words.add(pos, w); scores.add(pos, s)
            if (words.size > n) { words.removeAt(n); scores.removeAt(n) }
        }
    }

    /** Best [n] words (stored form) for this context. Call [Tok.show] to format them. */
    fun suggest(c: Tok.Ctx, n: Int = 3): List<String> {
        val su = Starter.uni(bn)
        val sb = Starter.bi(bn)
        val prev = c.prev
        val top = Top(n)
        val p = c.key
        if (p.isNotEmpty()) {
            for (w in su.keys) if (w.length > p.length && w.startsWith(p)) top.add(w, score(prev, w))
            for ((w, cnt) in uni) {
                if (cnt >= MIN_SHOW && w.length > p.length && w.startsWith(p) && !su.containsKey(w)) top.add(w, score(prev, w))
            }
            return top.words
        }
        if (prev != null) {
            val pre = prev + "\t"
            val seen = HashSet<String>()
            for (k in sb.keys) if (k.startsWith(pre)) {
                val w = k.substring(pre.length)
                if (seen.add(w)) top.add(w, score(prev, w))
            }
            for ((k, cnt) in bi) if (cnt >= MIN_SHOW && k.startsWith(pre)) {
                val w = k.substring(pre.length)
                if (seen.add(w)) top.add(w, score(prev, w))
            }
        }
        if (top.size < n) {
            // Not enough pairs: fill with the words used most.
            val fill = Top(n)
            for (w in su.keys) if (!top.has(w)) fill.add(w, uniScore(w))
            for ((w, cnt) in uni) if (cnt >= MIN_SHOW && !su.containsKey(w) && !top.has(w)) fill.add(w, uniScore(w))
            for (w in fill.words) if (top.size < n && !top.has(w)) top.words.add(w)
        }
        return top.words
    }

    // ---------- Storage (one small text file per language) ----------
    fun load(f: File) {
        uni.clear(); bi.clear(); events = 0
        try {
            if (f.exists()) {
                f.bufferedReader(Charsets.UTF_8).useLines { lines ->
                    var first = true
                    for (line in lines) {
                        if (first) {
                            first = false
                            if (!line.startsWith(MAGIC)) break
                            events = line.substringAfter('\t', "0").toIntOrNull() ?: 0
                            continue
                        }
                        val p = line.split('\t')
                        if (p[0] == "U" && p.size == 3) {
                            val c = p[2].toIntOrNull() ?: continue
                            if (p[1].isNotEmpty()) uni[p[1]] = c.coerceIn(1, MAX_COUNT)
                        } else if (p[0] == "B" && p.size == 4) {
                            val c = p[3].toIntOrNull() ?: continue
                            if (p[1].isNotEmpty() && p[2].isNotEmpty()) bi[p[1] + "\t" + p[2]] = c.coerceIn(1, MAX_COUNT)
                        }
                    }
                }
            }
        } catch (_: Exception) {
            uni.clear(); bi.clear()
        }
        while (uni.size > MAX_UNI) shrink(uni, MAX_UNI)
        while (bi.size > MAX_BI) shrink(bi, MAX_BI)
        loaded = true
        dirty = false
    }

    fun save(f: File) {
        if (!loaded || !dirty) return
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.bufferedWriter(Charsets.UTF_8).use { w ->
            w.write("$MAGIC\t$events\n")
            for ((k, v) in uni) { w.write("U\t$k\t$v\n") }
            for ((k, v) in bi) { w.write("B\t$k\t$v\n") }
        }
        if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
        dirty = false
    }

    val learnedWords: Int get() = uni.size
    val learnedPairs: Int get() = bi.size

    companion object {
        const val MAX_UNI = 5000          // learned words kept per language
        const val MAX_BI = 9000           // learned word pairs kept per language
        const val MAX_COUNT = 60000
        const val MIN_SHOW = 2            // a learned word/pair must be seen this often to be suggested
        const val DECAY_EVERY = 5000      // every N learned words: halve all counts, drop rare ones
        private const val MAGIC = "GAW1"
    }
}
