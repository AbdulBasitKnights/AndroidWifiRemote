package com.tvremote.app.util

import android.content.Context
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.TypefaceSpan
import android.util.TypedValue
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.FontRes
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.tvremote.app.R

data class TextLineStyle(
    @ColorInt val color: Int,
    val textSizeSp: Float,
    @FontRes val fontRes: Int? = null,
    val bold: Boolean = false,
)

object SpannableTextHelper {

    fun TextView.setTwoLineStyledText(
        topLine: String,
        bottomLine: String,
        @ColorInt topColor: Int = ContextCompat.getColor(context, R.color.text_primary),
        @ColorInt bottomColor: Int = ContextCompat.getColor(context, R.color.text_secondary),
        topTextSizeSp: Float = 13f,
        bottomTextSizeSp: Float = 11f,
        @FontRes topFontRes: Int = R.font.inter_semibold,
        @FontRes bottomFontRes: Int = R.font.inter_regular,
    ) {
        text = buildMultiLineText(
            context = context,
            lines = listOf(topLine, bottomLine),
            styles = listOf(
                TextLineStyle(topColor, topTextSizeSp, topFontRes, topFontRes == null),
                TextLineStyle(bottomColor, bottomTextSizeSp, bottomFontRes),
            ),
        )
    }

    private fun buildMultiLineText(
        context: Context,
        lines: List<String>,
        styles: List<TextLineStyle>,
    ): SpannableString {
        val fullText = lines.joinToString("\n")
        val spannable = SpannableString(fullText)
        var cursor = 0
        lines.forEachIndexed { index, line ->
            val style = styles[index]
            val start = cursor
            val end = start + line.length
            spannable.setSpan(ForegroundColorSpan(style.color), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannable.setSpan(
                AbsoluteSizeSpan(spToPx(context, style.textSizeSp)),
                start,
                end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
            style.fontRes?.let { fontRes ->
                ResourcesCompat.getFont(context, fontRes)?.let { typeface ->
                    spannable.setSpan(CustomTypefaceSpan(typeface), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
            cursor = end + 1
        }
        return spannable
    }

    private fun spToPx(context: Context, sp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            sp,
            context.resources.displayMetrics,
        ).toInt()
    }
}

private class CustomTypefaceSpan(private val typeface: Typeface) : TypefaceSpan("") {
    override fun updateDrawState(textPaint: TextPaint) = applyTypeface(textPaint, typeface)
    override fun updateMeasureState(textPaint: TextPaint) = applyTypeface(textPaint, typeface)
    private fun applyTypeface(paint: TextPaint, typeface: Typeface) {
        paint.typeface = typeface
    }
}
