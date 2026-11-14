package com.graphicarray.voicetyper

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

/** Graphicarray brand tokens + tiny view helpers. Only these colors are used. */
object B {
    val ABYSS = 0xFF001E36.toInt(); val PANEL = 0xFF062847.toInt(); val EDGE = 0xFF0F4A7A.toInt()
    val CYAN = 0xFF00CDFF.toInt(); val AZURE = 0xFF0083FF.toInt(); val TEXT = 0xFFFFFFFF.toInt()
    val SLATE = 0xFF8FA7BF.toInt(); val MUTE = 0xFF5F7C99.toInt()
    val AMBER = 0xFFF5A94A.toInt(); val GREEN = 0xFF3DD68C.toInt()
    val HALO = 0x3300A9FF
    const val PROFILE = "https://www.linkedin.com/in/graphicarray"
    val FONT: Typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    val FONT_B: Typeface = Typeface.create("sans-serif", Typeface.BOLD)
}

fun Context.dp(v: Int) = (v * resources.displayMetrics.density).toInt()
fun Context.openProfile() {
    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(B.PROFILE)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

fun shape(fill: Int, radius: Float, stroke: Int = 0, strokePx: Int = 0) = GradientDrawable().apply {
    setColor(fill); cornerRadius = radius; if (strokePx > 0) setStroke(strokePx, stroke)
}
fun grad(radius: Float) = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(B.CYAN, B.AZURE)).apply { cornerRadius = radius }
fun oval(fill: Int) = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(fill) }
fun gradOval() = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(B.CYAN, B.AZURE)).apply { shape = GradientDrawable.OVAL }

fun pageBg(c: Context) = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0xFF06406E.toInt(), B.ABYSS)).apply {
    gradientType = GradientDrawable.RADIAL_GRADIENT
    gradientRadius = c.resources.displayMetrics.widthPixels * 1.1f
    setGradientCenter(0.5f, -0.1f)
}

fun Context.text(t: String, sp: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
    text = t; textSize = sp; setTextColor(color); typeface = if (bold) B.FONT_B else B.FONT
}
fun Context.sectionLabel(t: String) = text(t.uppercase(), 11f, B.SLATE, true).apply {
    letterSpacing = 0.16f; setPadding(0, dp(4), 0, dp(8))
}
fun Context.helper(t: String) = text(t, 12.5f, B.SLATE).apply { setPadding(0, dp(8), 0, 0) }
fun Context.card() = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    setPadding(dp(16), dp(16), dp(16), dp(16))
    background = shape(B.PANEL, dp(20).toFloat(), B.EDGE, 1.coerceAtLeast(dp(1)))
}
fun Context.primaryButton(t: String) = text(t, 15f, B.ABYSS, true).apply {
    gravity = Gravity.CENTER; setPadding(dp(24), dp(14), dp(24), dp(14)); background = grad(1000f)
}
fun Context.secondaryButton(t: String) = text(t, 14f, B.TEXT, true).apply {
    gravity = Gravity.CENTER; setPadding(dp(20), dp(10), dp(20), dp(10))
    background = shape(0x66001E36, 1000f, B.CYAN, dp(1))
}
fun Context.chip(t: String) = text(t, 12f, B.SLATE).apply {
    setPadding(dp(10), dp(4), dp(10), dp(4)); background = shape(B.PANEL, 1000f, B.EDGE, dp(1))
}
