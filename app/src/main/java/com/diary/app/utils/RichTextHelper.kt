package com.diary.app.utils

import android.graphics.Canvas
import android.graphics.Paint
import android.text.Editable
import android.text.Html
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BulletSpan
import android.text.style.LeadingMarginSpan
import android.text.style.ReplacementSpan
import android.text.style.StrikethroughSpan
import org.xml.sax.XMLReader

object RichTextHelper {

    fun isHtml(text: String): Boolean {
        val t = text
        return t.contains("<strong") || t.contains("<em") || t.contains("<del") ||
                t.contains("<b>") || t.contains("<i>") || t.contains("<u>") ||
                t.contains("<strike") || t.contains("<font") ||
                t.contains("<br") || t.contains("<p>") || t.contains("<p ") ||
                t.contains("<h1") || t.contains("<h2") || t.contains("<h3") ||
                t.contains("<h4") || t.contains("<h5") || t.contains("<h6") ||
                t.contains("<li") || t.contains("<ol") || t.contains("<ul") ||
                t.contains("<blockquote") || t.contains("<a ") || t.contains("<hr") ||
                t.contains("</strong") || t.contains("</em") || t.contains("</del")
    }

    fun loadForDisplay(content: String): CharSequence {
        if (content.isEmpty()) return ""
        return try {
            val cleaned = cleanContent(content)
            if (isHtml(cleaned)) trimTrailingNewlines(fromHtml(cleaned)) else cleaned
        } catch (_: Exception) { content }
    }

    fun loadForPreview(content: String, maxChars: Int = 100): CharSequence {
        if (content.isEmpty()) return ""
        val cleaned = cleanContent(content)

        // Convert list items to plain text with numbers/bullets before stripping
        var processed = cleaned

        // Handle <ol>: number each <li> inside
        processed = Regex("<ol[^>]*>(.*?)</ol>", RegexOption.DOT_MATCHES_ALL)
            .replace(processed) { match ->
                var counter = 0
                Regex("<li[^>]*>(.*?)</li>", RegexOption.DOT_MATCHES_ALL)
                    .replace(match.groupValues[1]) { li ->
                        counter++
                        " $counter. ${li.groupValues[1]} "
                    }
            }

        // Handle <ul>: add bullet to each <li> inside
        processed = Regex("<ul[^>]*>(.*?)</ul>", RegexOption.DOT_MATCHES_ALL)
            .replace(processed) { match ->
                Regex("<li[^>]*>(.*?)</li>", RegexOption.DOT_MATCHES_ALL)
                    .replace(match.groupValues[1]) { li ->
                        " \u2022 ${li.groupValues[1]} "
                    }
            }

        // Strip remaining HTML
        val plain = processed
            .replace(Regex("<br\\s*/?>"), " ")
            .replace(Regex("</(p|li|h[1-6]|div|blockquote)>"), " ")
            .replace(Regex("<[^>]*>"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
        return if (plain.length > maxChars) plain.substring(0, maxChars) + "\u2026" else plain
    }

    fun cleanContent(content: String): String {
        var c = content
        c = c.replace(Regex("</?bg[0-9A-Fa-f]{6}>"), "")
        c = c.replace(Regex("</?sz\\d+>"), "")
        return c
    }

    private fun fromHtml(html: String): Spanned {
        if (html.isEmpty()) return SpannableStringBuilder("")
        return Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY)
    }

    private fun trimTrailingNewlines(s: Spanned): SpannableStringBuilder {
        val sb = SpannableStringBuilder(s)
        while (sb.isNotEmpty() && sb[sb.length - 1] == '\n') sb.delete(sb.length - 1, sb.length)
        return sb
    }

    // ── Horizontal rule span ──────────────────────────────────
    class HrSpan : ReplacementSpan() {
        override fun getSize(paint: Paint, text: CharSequence?, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
            fm?.let { it.ascent = -20; it.descent = 20; it.top = it.ascent; it.bottom = it.descent }
            return 0
        }
        override fun draw(canvas: Canvas, text: CharSequence?, start: Int, end: Int,
                          x: Float, top: Int, y: Int, bottom: Int, paint: Paint) {
            val oldColor = paint.color
            paint.color = 0xFFCCCCCC.toInt()
            paint.strokeWidth = 2f
            val mid = (top + bottom) / 2f
            canvas.drawLine(x, mid, canvas.width.toFloat(), mid, paint)
            paint.color = oldColor
        }
    }

    // ── Numbered list span ────────────────────────────────────
    class NumberedSpan(private val num: Int) : LeadingMarginSpan {
        override fun getLeadingMargin(first: Boolean): Int = 60
        override fun drawLeadingMargin(c: Canvas, p: Paint, x: Int, dir: Int,
                                       top: Int, baseline: Int, bottom: Int,
                                       text: CharSequence, start: Int, end: Int,
                                       first: Boolean, layout: Layout?) {
            if (first) {
                val label = "$num."
                val w = p.measureText(label)
                c.drawText(label, x + 24f * dir - if (dir < 0) w else 0f, baseline.toFloat(), p)
            }
        }
    }

    // ── Tag handler for <del>, <hr>, <ol>/<ul>/<li> ──────────
    private class FullTagHandler : Html.TagHandler {
        private var listItemStart = -1
        private var delStart = -1
        private var orderedCounter = 0
        private var isOrdered = false

        override fun handleTag(opening: Boolean, tag: String, output: Editable, xmlReader: XMLReader) {
            when (tag.lowercase()) {
                "del", "s" -> {
                    if (opening) { delStart = output.length }
                    else if (delStart >= 0 && delStart < output.length) {
                        output.setSpan(StrikethroughSpan(), delStart, output.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        delStart = -1
                    }
                }
                "hr" -> {
                    if (opening) {
                        ensureNewline(output)
                        val start = output.length
                        output.append(" \n")
                        output.setSpan(HrSpan(), start, start + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                }
                "x-ol" -> {
                    if (opening) { isOrdered = true; orderedCounter = 0; ensureNewline(output) }
                    else { isOrdered = false; orderedCounter = 0; ensureNewline(output) }
                }
                "x-ul" -> {
                    if (opening) { isOrdered = false; ensureNewline(output) }
                    else { ensureNewline(output) }
                }
                "x-li" -> {
                    if (opening) {
                        if (isOrdered) orderedCounter++
                        listItemStart = output.length
                    } else if (listItemStart >= 0) {
                        ensureNewline(output)
                        if (isOrdered) {
                            output.setSpan(NumberedSpan(orderedCounter), listItemStart, output.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        } else {
                            output.setSpan(BulletSpan(24), listItemStart, output.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        }
                        listItemStart = -1
                    }
                }
            }
        }

        private fun ensureNewline(output: Editable) {
            if (output.isNotEmpty() && output[output.length - 1] != '\n') output.append("\n")
        }
    }
}
