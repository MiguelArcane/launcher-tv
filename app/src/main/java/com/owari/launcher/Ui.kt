package com.owari.launcher

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.widget.TextView

/** Piezas visuales que comparten la pantalla de inicio y la de ADMIN. */

fun Context.dp(value: Int) = (value * resources.displayMetrics.density).toInt()

fun Context.label(sizeSp: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
    setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
    setTextColor(color)
    if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
}

fun rounded(color: Int, radius: Float) = GradientDrawable().apply {
    setColor(color)
    cornerRadius = radius
}

fun oval(color: Int) = GradientDrawable().apply {
    shape = GradientDrawable.OVAL
    setColor(color)
}
