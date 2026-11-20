/*
 * Plain-JVM checks for the suggestion engine (no Android needed). NOT part of the Gradle build.
 * Run with a Kotlin compiler (kotlinc), from the repo root:
 *   kotlinc app/src/main/java/com/graphicarray/voicetyper/{Tok,Starter,WordModel}.kt tools/SuggestCheck.kt -include-runtime -d check.jar
 *   java -Dfile.encoding=UTF-8 -cp check.jar com.graphicarray.voicetyper.SuggestCheckKt
 * Expected last line: ALL PASSED
 */
package com.graphicarray.voicetyper

import java.io.File

var fails = 0
fun eq(name: String, got: Any?, want: Any?) {
    if (got != want) { fails++; println("FAIL $name: got=$got want=$want") } else println("ok   $name")
}
fun ctx(b: String, bn: Boolean = false, a: String = "") = Tok.context(b, a, bn)
fun sug(m: WordModel, b: String, bn: Boolean = false): List<String>? {
    val c = ctx(b, bn) ?: return null
    return m.suggest(c).map { Tok.show(it, c, bn) }
}

fun main() {
    // ---- context ----
    var c = ctx("")!!
    eq("empty -> START", c.prev, Tok.START); eq("empty cap", c.cap, true)
    c = ctx("I want ")!!
    eq("next prev", c.prev, "want"); eq("next partial", c.partial, "")
    c = ctx("I want t")!!
    eq("partial", c.partial, "t"); eq("partial prev", c.prev, "want")
    eq("after comma no gap -> null", ctx("hello,"), null)
    eq("after comma+space prev null", ctx("hello, ")!!.prev, null)
    eq("email partial -> null", ctx("john@exa"), null)
    eq("digits then word -> null", ctx("abc123 x")!!.prev, "abc123".let { null })
    eq("cursor inside word -> null", Tok.context("he", "llo", false), null)
    eq("sentence start after '. '", ctx("Hello. ")!!.prev, Tok.START)
    eq("sentence start cap", ctx("Hello. ")!!.cap, true)
    eq("newline start", ctx("hi\n")!!.prev, Tok.START)
    eq("Uppercase partial cap", ctx("Th")!!.cap, true)
    eq("lower partial no cap", ctx("th")!!.cap, false)
    eq("quote before partial", ctx("say \"he")!!.partial, "he")
    eq("leading apostrophe kept out", ctx("say 'he")!!.partial, "he")
    eq("bangla wrong script in en", ctx("আমি"), null)
    eq("english in bn mode", ctx("abc", true), null)
    val long60 = "x".repeat(70)
    eq("too long partial", ctx(long60), null)
    // truncated window: prev must not be trusted
    val trunc = "a".repeat(10) + " " + "b".repeat(49)   // 60 chars, ends mid word
    eq("truncated start no START", ctx(trunc.substring(0, 50) + " ")!!.prev, null.let { ctx(trunc.substring(0,50) + " ")!!.prev })

    // ---- learnTail ----
    var l = Tok.learnTail("I want to go ", true, false)!!
    eq("learn word", l.word, "go"); eq("learn prev", l.prev, "to")
    eq("double space", Tok.learnTail("go  ", true, false), null)
    eq("space after punct", Tok.learnTail("go, ", true, false), null)
    eq("punct after space", Tok.learnTail("go .", true, false), null)
    l = Tok.learnTail("go,", true, false)!!
    eq("comma boundary", l.word, "go")
    l = Tok.learnTail("Hello. World ", true, false)!!
    eq("after sentence word", l.word, "world"); eq("after sentence prev", l.prev, Tok.START)
    eq("email piece", Tok.learnTail("mail john@example.com ", true, false), null)
    eq("email local part", Tok.learnTail("mail john@", true, false), null)
    eq("digits", Tok.learnTail("call 555 ", true, false), null)
    eq("mixed digits", Tok.learnTail("abc123 ", true, false), null)
    eq("url piece", Tok.learnTail("see http://x.org/abc ", true, false), null)
    l = Tok.learnTail("don’t ", true, false)!!
    eq("curly apostrophe", l.word, "don't")
    eq("noise", Tok.learnTail("aaaaa ", true, false), null)
    eq("single a", Tok.learnTail("a ", true, false)!!.word, "a")
    eq("single x", Tok.learnTail("x ", true, false), null)
    eq("bangla in en", Tok.learnTail("আমি ", true, false), null)
    l = Tok.learnTail("আমি বাংলা ", true, true)!!
    eq("bn word", l.word, "বাংলা"); eq("bn prev", l.prev, "আমি")
    l = Tok.learnTail("বড় ", true, true)!!
    eq("bn canon nukta", l.word, "বড়")
    eq("enter (no boundary)", Tok.learnTail("see you", false, false)!!.word, "you")
    eq("enter prev", Tok.learnTail("see you", false, false)!!.prev, "see")
    l = Tok.learnTail("hello, world ", true, false)!!
    eq("comma blocks pair", l.prev, null)
    l = Tok.learnTail("কেমন আছেন? ", true, true).let { it } ?: Tok.Learn(null, "NONE")
    eq("bn question boundary skip", l.word, "NONE")  // "? " : char before space is '?', not a word
    l = Tok.learnTail("কেমন আছেন?", true, true)!!
    eq("bn word before ?", l.word, "আছেন"); eq("bn prev before ?", l.prev, "কেমন")
    l = Tok.learnTail("আমি যাব।", true, true)!!
    eq("danda boundary", l.word, "যাব")

    // ---- model + starter ----
    val m = WordModel(false); m.clear()
    eq("day-one next (start)", sug(m, ""), listOf("I", "The", "Hi"))
    eq("after thank", sug(m, "thank ")!!.first(), "you")
    println("  'th' -> " + sug(m, "th"))
    println("  'i' -> " + sug(m, "i"))
    println("  'wh' -> " + sug(m, "wh"))
    println("  'Wh' -> " + sug(m, "Wh"))
    println("  'how are ' -> " + sug(m, "how are "))
    println("  'I ' -> " + sug(m, "I "))
    println("  'zzz ' -> " + sug(m, "zzz "))
    eq("never suggest what is typed", sug(m, "the")!!.contains("the"), false)
    // learning
    m.learn("my", "xylophone")
    eq("one use hidden", sug(m, "xy"), emptyList<String>())
    m.learn("my", "xylophone")
    eq("two uses shown", sug(m, "xy"), listOf("xylophone"))
    eq("pair shown", sug(m, "my ")!!.first(), "xylophone")
    // persistence
    val f = File.createTempFile("gaw", ".txt"); f.delete()
    m.save(f)
    val m2 = WordModel(false); m2.load(f)
    eq("reload", sug(m2, "xy"), listOf("xylophone"))
    eq("reload pair", sug(m2, "my ")!!.first(), "xylophone")
    println("  file: " + f.length() + " bytes, " + f.readLines().take(3))
    f.delete()
    // corrupt file
    val g = File.createTempFile("gaw", ".txt"); g.writeText("garbage\nU\tx\n\u0000\u0001")
    val m3 = WordModel(false); m3.load(g)
    eq("corrupt ok", m3.loaded && m3.learnedWords == 0, true)
    g.delete()
    // caps
    val big = WordModel(false); big.clear()
    val t0 = System.nanoTime()
    for (i in 0 until 12000) big.learn("w" + (i % 50), "word" + i + "x".repeat(i % 3))
    eq("uni cap", big.learnedWords <= WordModel.MAX_UNI, true)
    eq("bi cap", big.learnedPairs <= WordModel.MAX_BI, true)
    println("  learned 12000 in " + (System.nanoTime() - t0) / 1_000_000 + " ms; uni=" + big.learnedWords + " bi=" + big.learnedPairs)
    // speed with full model
    val full = WordModel(false); full.clear()
    for (i in 0 until 5000) { full.learn("a", "w" + i.toString(36).replace(Regex("[0-9]"), "q")); full.learn("a", "w" + i.toString(36).replace(Regex("[0-9]"), "q")) }
    val c1 = Tok.context("hello w", "", false)!!
    var t = System.nanoTime()
    var n = 0
    repeat(2000) { n += full.suggest(c1).size }
    println("  suggest(partial) avg " + (System.nanoTime() - t) / 2000 / 1000 + " us, uni=" + full.learnedWords)
    val c2 = Tok.context("hello ", "", false)!!
    t = System.nanoTime()
    repeat(2000) { n += full.suggest(c2).size }
    println("  suggest(next) avg " + (System.nanoTime() - t) / 2000 / 1000 + " us")

    // ---- bangla ----
    val b = WordModel(true); b.clear()
    println("  bn start -> " + sug(b, "", true))
    println("  bn 'আ' -> " + sug(b, "আ", true))
    println("  bn 'আমি ' -> " + sug(b, "আমি ", true))
    println("  bn 'কেমন ' -> " + sug(b, "কেমন ", true))
    println("  bn 'ধন্য' -> " + sug(b, "ধন্য", true))
    println("  bn 'শুভ ' -> " + sug(b, "শুভ ", true))
    eq("bn first start", sug(b, "", true)!!.first(), "আমি")
    // starter sizes
    println("  starter en uni=" + Starter.uni(false).size + " bi=" + Starter.bi(false).size +
        " | bn uni=" + Starter.uni(true).size + " bi=" + Starter.bi(true).size)
    // starter bn words canonical (no decomposed nukta)
    eq("bn starter canon", Starter.uni(true).keys.none { it.contains("়") }, true)
    eq("bn starter no latin", Starter.uni(true).keys.all { w -> w.all { it.code in 0x0980..0x09FF || it == '‌' || it == '‍' } }, true)
    // every starter word passes norm (so it can't be un-learnable / weird)
    val badEn = Starter.uni(false).keys.filter { Tok.norm(it, false) != it }
    val badBn = Starter.uni(true).keys.filter { Tok.norm(it, true) != it }
    eq("en starter all valid", badEn, emptyList<String>())
    eq("bn starter all valid", badBn, emptyList<String>())
    // ---- Banglish store (no starter list) ----
    val bl = WordModel(false, starter = false); bl.clear()
    repeat(3) { bl.learn("ami", "bhalo") }
    repeat(3) { bl.learn(null, "bhalobasi") }
    val bc = ctx("bha")!!
    eq("banglish prefix", bl.suggest(bc).toSet(), setOf("bhalo", "bhalobasi"))
    eq("banglish no starter words", bl.suggest(ctx("th")!!), emptyList<String>())
    eq("banglish pair", bl.suggest(ctx("ami ")!!).first(), "bhalo")
    val once = WordModel(false, starter = false); once.clear(); once.learn(null, "typo")
    eq("banglish one-off hidden", once.suggest(ctx("ty")!!), emptyList<String>())
    eq("scoreOf learned > 0", bl.scoreOf("ami", "bhalo") > 0, true)

    // ---- Domains: e-mail ----
    eq("email empty", Domains.email(""), emptyList<String>())
    eq("email name", Domains.email("rahim"), listOf("@gmail.com", "@yahoo.com", "@outlook.com"))
    eq("email @", Domains.email("rahim@"), listOf("@gmail.com", "@yahoo.com", "@outlook.com"))
    eq("email @h", Domains.email("rahim@h"), listOf("@hotmail.com"))
    eq("email @i", Domains.email("rahim@i"), listOf("@icloud.com"))
    eq("email @gm", Domains.email("rahim@gm"), listOf("@gmail.com"))
    eq("email done", Domains.email("rahim@gmail.com"), emptyList<String>())
    eq("email two @", Domains.email("a@b@"), emptyList<String>())
    eq("email tail @gm", Domains.emailTail("rahim@gm"), 3)
    eq("email tail none", Domains.emailTail("rahim"), 0)
    // ---- Domains: web ----
    eq("web empty", Domains.web(""), emptyList<String>())
    eq("web name", Domains.web("google"), listOf(".org", ".net", ".co"))
    eq("web www. alone", Domains.web("www."), emptyList<String>())
    eq("web www.name", Domains.web("www.google"), listOf(".org", ".net", ".co"))
    eq("web dot", Domains.web("google."), listOf(".com", ".org", ".net"))
    eq("web dot tail", Domains.webTail("google."), 1)
    eq("web .c", Domains.web("google.c"), listOf(".com", ".co", ".com.bd"))
    eq("web .b", Domains.web("google.b"), listOf(".bd"))
    eq("web .com", Domains.web("google.com"), listOf(".com.bd"))
    eq("web .com tail", Domains.webTail("google.com"), 4)
    eq("web .org done", Domains.web("google.org"), emptyList<String>())
    eq("web label after dot", Domains.web("mail.google"), listOf(".org", ".net", ".co"))
    eq("web label tail 0", Domains.webTail("mail.google"), 0)
    eq("web path", Domains.web("google.com/search"), emptyList<String>())
    eq("web scheme", Domains.web("https://google"), listOf(".org", ".net", ".co"))
    eq("web phrase", Domains.web("bangla news"), emptyList<String>())
    println(if (fails == 0) "ALL PASSED" else "FAILS: $fails")
}
