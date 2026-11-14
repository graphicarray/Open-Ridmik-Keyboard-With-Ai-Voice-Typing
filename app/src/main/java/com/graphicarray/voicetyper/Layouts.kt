package com.graphicarray.voicetyper

/**
 * Keyboard layouts. Each row: space-separated keys.
 * n = key, s = shift layer, h = hint top-right (long-press types it), sh = hint while shifted.
 * "·" means "no hint". য় = য় ড় = ড় ঢ় = ঢ় ৎ = ৎ ৗ = ৗ
 */
object Layouts {
    const val TAB = "<tab>"
    const val CUR = "<cur>"

    class Cell(val n: String, val s: String, val h: String?, val sh: String?) {
        fun label(shifted: Boolean): String {
            val t = if (shifted) s else n
            return if (t == TAB) "→|" else t
        }
        fun hintFor(shifted: Boolean): String? = if (shifted) sh else h
    }
    class Layout(val rows: List<List<Cell>>)

    private fun hint(x: String?): String? = if (x == null || x == "·" || x.isEmpty()) null else x

    private fun mk(normal: String, shift: String, hints: String = "", shHints: String? = null): List<Cell> {
        val a = normal.split(" ")
        val b = shift.split(" ")
        val c = if (hints.isEmpty()) emptyList() else hints.split(" ")
        val d = if (shHints == null) c else shHints.split(" ")
        return a.mapIndexed { i, x -> Cell(x, b.getOrElse(i) { x }, hint(c.getOrNull(i)), hint(d.getOrNull(i))) }
    }

    private const val NONE10 = "· · · · · · · · · ·"
    private const val HINT1 = "ং য় ঢ ফ ঠ ছ ঝ ঞ ঘ ঢ়"
    private const val SYM_R2 = "@ # ৳ % & * - + ( )"
    private const val SYM_R3 = "! \" ' : ; / ?"

    val JATIO = Layout(listOf(
        mk("ঙ য ড প ট চ জ হ গ ড়", "ং ্য ঢ ফ ঠ ছ ঝ ঞ ঘ ঢ়", HINT1, NONE10),
        mk("ৃ ু ি ব ্ া ক ত দ", "ঋ ূ ী ভ ৗ অ খ থ ধ",
            "ঐ উ ই ভ · আ খ ৎ ধ", "· ঊ ঈ · · · · · ·"),
        mk("ঁ ো ে র ন স ম", "ঃ ৌ ৈ ্র ণ ষ শ",
            "ঃ ও এ ল ণ ষ শ", "· ঔ ঐ · · · ·")
    ))

    val ENGLISH = Layout(listOf(
        mk("q w e r t y u i o p", "Q W E R T Y U I O P", "1 2 3 4 5 6 7 8 9 0"),
        mk("a s d f g h j k l", "A S D F G H J K L", "@ # & * - ! ? ( )"),
        mk("z x c v b n m", "Z X C V B N M", "\" · · · ' : /")
    ))

    /** Symbols page 1 (Bangla digits on Jatio). */
    val SYM1_BN = Layout(listOf(
        mk("১ ২ ৩ ৪ ৫ ৬ ৭ ৮ ৯ ০", "১ ২ ৩ ৪ ৫ ৬ ৭ ৮ ৯ ০", "1 2 3 4 5 6 7 8 9 0"),
        mk(SYM_R2, SYM_R2, "· · $ · · ★ _ · < >"),
        mk(SYM_R3, SYM_R3, "· · · ° · · ·")
    ))

    val SYM1_EN = Layout(listOf(
        mk("1 2 3 4 5 6 7 8 9 0", "1 2 3 4 5 6 7 8 9 0"),
        mk(SYM_R2, SYM_R2, "· · $ · · ★ _ · < >"),
        mk(SYM_R3, SYM_R3, "· · · ° · · ·")
    ))

    /** Symbols page 2. */
    val SYM2 = Layout(listOf(
        mk("~ ` | ৲ √ π ÷ × { }", "~ ` | ৲ √ π ÷ × { }", "· · · · · Π · · · ·"),
        mk("$TAB £ $ % ^ ° _ = [ ]", "$TAB £ $ % ^ ° _ = [ ]", "· · · · → • · ≠ · ·"),
        mk("$CUR ® © ™ \\ < >", "$CUR ® © ™ \\ < >", "র্য · · · · · ·")
    ))
}
