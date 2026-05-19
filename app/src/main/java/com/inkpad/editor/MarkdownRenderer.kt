package com.inkpad.editor

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.*
import android.util.TypedValue
import androidx.core.content.ContextCompat
import com.inkpad.R

/**
 * Lightweight Markdown renderer.
 * Handles only: headings, bold, highlight, blockquote, callout.
 * Line-level processing — no cross-paragraph state needed.
 */
class MarkdownRenderer(private val context: Context) {

    data class RenderConfig(
        val baseFontSizeSp: Float = 16f,
        val fontFamily: Typeface = Typeface.DEFAULT
    )

    var config = RenderConfig()

    fun render(text: String): SpannableStringBuilder {
        val sb = SpannableStringBuilder()
        val lines = text.split("\n")
        lines.forEachIndexed { index, line ->
            sb.append(renderLine(line))
            if (index < lines.size - 1) sb.append("\n")
        }
        return sb
    }

    private fun renderLine(line: String): SpannableStringBuilder {
        val sb = SpannableStringBuilder()

        // Callout: > [!note] / > [!warning] / > [!tip] etc.
        val calloutMatch = CALLOUT_RE.matchEntire(line)
        if (calloutMatch != null) {
            val type = calloutMatch.groupValues[1].lowercase()
            val content = calloutMatch.groupValues[2]
            val prefix = "  ${calloutIcon(type)} "
            sb.append(prefix)
            appendInline(sb, content)
            val color = calloutColor(type)
            sb.setSpan(ForegroundColorSpan(color), 0, sb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.setSpan(StyleSpan(Typeface.ITALIC), 0, sb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.setSpan(LeadingMarginSpan.Standard(dpToPx(16)), 0, sb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            return sb
        }

        // Blockquote: > text
        val quoteMatch = QUOTE_RE.matchEntire(line)
        if (quoteMatch != null) {
            val content = quoteMatch.groupValues[1]
            sb.append("  ")
            appendInline(sb, content)
            val quoteColor = ContextCompat.getColor(context, R.color.quote_bar)
            sb.setSpan(QuoteSpan(quoteColor, dpToPx(3), dpToPx(8)), 0, sb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.setSpan(ForegroundColorSpan(ContextCompat.getColor(context, R.color.text_secondary)),
                0, sb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            return sb
        }

        // Headings: # ## ###
        val headingMatch = HEADING_RE.matchEntire(line)
        if (headingMatch != null) {
            val level = headingMatch.groupValues[1].length
            val content = headingMatch.groupValues[2]
            appendInline(sb, content)
            val sizeMult = when (level) {
                1 -> 1.8f
                2 -> 1.5f
                3 -> 1.25f
                else -> 1.1f
            }
            sb.setSpan(RelativeSizeSpan(sizeMult), 0, sb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.setSpan(StyleSpan(Typeface.BOLD), 0, sb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.setSpan(ForegroundColorSpan(ContextCompat.getColor(context, R.color.heading_color)),
                0, sb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            return sb
        }

        // Normal line — inline processing only
        appendInline(sb, line)
        return sb
    }

    /**
     * Inline: **bold**, ==highlight==
     * Processes left-to-right, handles nested markers gracefully.
     */
    private fun appendInline(sb: SpannableStringBuilder, text: String) {
        var i = 0
        val startLen = sb.length
        while (i < text.length) {
            when {
                // Bold: **text**
                text.startsWith("**", i) -> {
                    val end = text.indexOf("**", i + 2)
                    if (end != -1) {
                        val spanStart = sb.length
                        sb.append(text.substring(i + 2, end))
                        sb.setSpan(StyleSpan(Typeface.BOLD), spanStart, sb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                        i = end + 2
                    } else {
                        sb.append(text[i])
                        i++
                    }
                }
                // Highlight: ==text==
                text.startsWith("==", i) -> {
                    val end = text.indexOf("==", i + 2)
                    if (end != -1) {
                        val spanStart = sb.length
                        sb.append(text.substring(i + 2, end))
                        sb.setSpan(
                            BackgroundColorSpan(ContextCompat.getColor(context, R.color.highlight_bg)),
                            spanStart, sb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                        i = end + 2
                    } else {
                        sb.append(text[i])
                        i++
                    }
                }
                // Strikethrough: ~~text~~
                text.startsWith("~~", i) -> {
                    val end = text.indexOf("~~", i + 2)
                    if (end != -1) {
                        val spanStart = sb.length
                        sb.append(text.substring(i + 2, end))
                        sb.setSpan(StrikethroughSpan(), spanStart, sb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                        i = end + 2
                    } else {
                        sb.append(text[i])
                        i++
                    }
                }
                else -> {
                    sb.append(text[i])
                    i++
                }
            }
        }
    }

    private fun calloutIcon(type: String) = when (type) {
        "note" -> "📝"
        "warning", "warn" -> "⚠️"
        "tip" -> "💡"
        "important" -> "❗"
        "caution" -> "🔥"
        "info" -> "ℹ️"
        else -> "📌"
    }

    private fun calloutColor(type: String): Int = when (type) {
        "warning", "warn", "caution" -> Color.parseColor("#B8860B")
        "tip" -> Color.parseColor("#2E7D32")
        "important" -> Color.parseColor("#C62828")
        else -> ContextCompat.getColor(context, R.color.callout_default)
    }

    private fun dpToPx(dp: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(),
            context.resources.displayMetrics).toInt()

    companion object {
        private val HEADING_RE = Regex("""^(#{1,6})\s+(.+)$""")
        private val QUOTE_RE = Regex("""^>\s(.*)$""")
        private val CALLOUT_RE = Regex("""^>\s\[!(\w+)]\s*(.*)$""")
    }
}
