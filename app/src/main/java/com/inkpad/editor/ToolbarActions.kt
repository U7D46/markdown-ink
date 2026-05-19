package com.inkpad.editor

import android.text.Editable
import android.widget.EditText

/**
 * All toolbar editing operations. Operates directly on EditText's Editable.
 * No dependencies — pure text manipulation.
 */
object ToolbarActions {

    fun toggleBold(et: EditText) {
        wrapSelection(et, "**", "**")
    }

    fun toggleHighlight(et: EditText) {
        wrapSelection(et, "==", "==")
    }

    fun toggleStrikethrough(et: EditText) {
        wrapSelection(et, "~~", "~~")
    }

    /** Delete current line entirely */
    fun deleteLine(et: EditText) {
        val (start, end) = currentLineRange(et)
        val text = et.text
        // Include trailing newline if present
        val delEnd = if (end < text.length && text[end] == '\n') end + 1 else end
        val delStart = if (start > 0 && text[start - 1] == '\n') start - 1 else start
        text.delete(if (delStart == start) start else delStart, delEnd)
    }

    /** Clear all markdown formatting from selection or current line */
    fun clearFormatting(et: EditText) {
        val (lineStart, lineEnd) = selectionOrLine(et)
        val text = et.text
        val raw = text.substring(lineStart, lineEnd)
        val cleaned = raw
            .replace(Regex("""\*\*(.*?)\*\*"""), "$1")
            .replace(Regex("""==(.*?)=="""), "$1")
            .replace(Regex("""~~(.*?)~~"""), "$1")
            .replace(Regex("""^#{1,6}\s"""), "")
            .replace(Regex("""^>\s"""), "")
        text.replace(lineStart, lineEnd, cleaned)
    }

    fun indent(et: EditText) {
        val (start, _) = currentLineRange(et)
        et.text.insert(start, "    ")
    }

    fun unindent(et: EditText) {
        val (start, _) = currentLineRange(et)
        val text = et.text
        val line = text.substring(start, minOf(start + 4, text.length))
        when {
            line.startsWith("    ") -> text.delete(start, start + 4)
            line.startsWith("\t") -> text.delete(start, start + 1)
        }
    }

    /** Select all text in current line */
    fun selectCurrentLine(et: EditText) {
        val (start, end) = currentLineRange(et)
        et.setSelection(start, end)
    }

    /** Remove consecutive blank lines (keep max 1) */
    fun removeExtraBlankLines(et: EditText) {
        val cursor = et.selectionStart
        val original = et.text.toString()
        val cleaned = original.replace(Regex("\n{3,}"), "\n\n")
        if (cleaned != original) {
            et.setText(cleaned)
            et.setSelection(minOf(cursor, cleaned.length))
        }
    }

    /** Move cursor/view to previous line (for ← button, no scroll) */
    fun moveToPrevLine(et: EditText) {
        val text = et.text
        val sel = et.selectionStart
        if (sel <= 0) return
        // Find start of current line
        var lineStart = sel - 1
        while (lineStart > 0 && text[lineStart - 1] != '\n') lineStart--
        if (lineStart == 0) { et.setSelection(0); return }
        // Find start of previous line
        var prevLineStart = lineStart - 1
        while (prevLineStart > 0 && text[prevLineStart - 1] != '\n') prevLineStart--
        // Place cursor at same column or end of prev line
        val currentCol = sel - lineStart
        val prevLineEnd = lineStart - 1
        val prevLineLen = prevLineEnd - prevLineStart
        et.setSelection(prevLineStart + minOf(currentCol, prevLineLen))
    }

    /** Move cursor/view to next line (for → button, no scroll) */
    fun moveToNextLine(et: EditText) {
        val text = et.text
        val sel = et.selectionStart
        // Find end of current line
        var lineStart = sel
        while (lineStart > 0 && text[lineStart - 1] != '\n') lineStart--
        var lineEnd = sel
        while (lineEnd < text.length && text[lineEnd] != '\n') lineEnd++
        if (lineEnd >= text.length) return
        val nextLineStart = lineEnd + 1
        var nextLineEnd = nextLineStart
        while (nextLineEnd < text.length && text[nextLineEnd] != '\n') nextLineEnd++
        val currentCol = sel - lineStart
        val nextLineLen = nextLineEnd - nextLineStart
        et.setSelection(nextLineStart + minOf(currentCol, nextLineLen))
    }

    // ─── Helpers ───────────────────────────────────────────────────

    private fun wrapSelection(et: EditText, prefix: String, suffix: String) {
        val start = et.selectionStart
        val end = et.selectionEnd
        val text = et.text
        if (start == end) {
            // No selection: insert markers and place cursor between
            text.insert(start, prefix + suffix)
            et.setSelection(start + prefix.length)
        } else {
            val selected = text.substring(start, end)
            // Toggle: if already wrapped, unwrap
            if (selected.startsWith(prefix) && selected.endsWith(suffix)) {
                text.replace(start, end, selected.drop(prefix.length).dropLast(suffix.length))
            } else {
                text.replace(start, end, "$prefix$selected$suffix")
                et.setSelection(start, end + prefix.length + suffix.length)
            }
        }
    }

    private fun currentLineRange(et: EditText): Pair<Int, Int> {
        val text = et.text
        val sel = et.selectionStart.coerceIn(0, text.length)
        var start = sel
        while (start > 0 && text[start - 1] != '\n') start--
        var end = sel
        while (end < text.length && text[end] != '\n') end++
        return start to end
    }

    private fun selectionOrLine(et: EditText): Pair<Int, Int> {
        val ss = et.selectionStart
        val se = et.selectionEnd
        return if (ss != se) ss to se else currentLineRange(et)
    }
}
