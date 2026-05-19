package com.inkpad.editor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.text.Layout
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatEditText

/**
 * Custom EditText for immersive reading mode.
 *
 * In immersive mode:
 * - Current paragraph text stays at full opacity (black)
 * - All other paragraphs are overlaid with a semi-transparent white mask
 * - No blur (e-ink friendly — blur would cause full-screen refresh)
 *
 * Uses onDraw() overlay technique: system draws text normally first,
 * then we paint dim rectangles above and below the current paragraph.
 */
class ImmersiveEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = android.R.attr.editTextStyle
) : AppCompatEditText(context, attrs, defStyle) {

    var immersiveMode = false
        set(value) {
            field = value
            invalidate()
        }

    private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(180, 255, 255, 255)
        style = Paint.Style.FILL
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(60, 0, 0, 0)
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!immersiveMode || layout == null) return

        val cursorOffset = selectionStart.coerceAtLeast(0)
        val paragraphBounds = getParagraphBounds(cursorOffset)
        val paraTop = paragraphBounds.first.toFloat()
        val paraBottom = paragraphBounds.second.toFloat()

        val scrollOffset = scrollY.toFloat()
        val viewWidth = width.toFloat()

        // Dim everything above current paragraph
        if (paraTop > scrollOffset) {
            canvas.drawRect(
                0f,
                scrollOffset,
                viewWidth,
                paraTop - 4f,
                dimPaint
            )
        }

        // Dim everything below current paragraph
        val viewBottom = scrollOffset + height
        if (paraBottom < viewBottom) {
            canvas.drawRect(
                0f,
                paraBottom + 4f,
                viewWidth,
                viewBottom,
                dimPaint
            )
        }

        // Subtle border around current paragraph (helps e-ink readers focus)
        val paddingL = paddingLeft.toFloat()
        val paddingR = paddingRight.toFloat()
        canvas.drawRect(
            paddingL - 2f,
            paraTop - 2f,
            viewWidth - paddingR + 2f,
            paraBottom + 2f,
            borderPaint
        )
    }

    /**
     * Find the top/bottom pixel bounds of the paragraph containing [cursorOffset].
     * A "paragraph" here means a block of text between newlines.
     */
    private fun getParagraphBounds(cursorOffset: Int): Pair<Int, Int> {
        val text = text ?: return Pair(0, height)
        val len = text.length

        // Find paragraph start (scan back to previous \n)
        var paraStart = cursorOffset
        while (paraStart > 0 && text[paraStart - 1] != '\n') paraStart--

        // Find paragraph end (scan forward to next \n)
        var paraEnd = cursorOffset
        while (paraEnd < len && text[paraEnd] != '\n') paraEnd++

        // Skip blank paragraphs — expand to include adjacent non-blank lines
        if (paraStart == paraEnd || text.substring(paraStart, paraEnd).isBlank()) {
            // On a blank line: just highlight that single line
            val line = layout.getLineForOffset(cursorOffset.coerceIn(0, len))
            return Pair(
                layout.getLineTop(line) + paddingTop,
                layout.getLineBottom(line) + paddingTop
            )
        }

        val startLine = layout.getLineForOffset(paraStart.coerceIn(0, len))
        val endLine = layout.getLineForOffset(paraEnd.coerceIn(0, len))

        val top = layout.getLineTop(startLine) + paddingTop
        val bottom = layout.getLineBottom(endLine) + paddingTop

        return Pair(top, bottom)
    }

    /**
     * Refresh highlight whenever cursor moves (called by selection change).
     */
    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        if (immersiveMode) invalidate()
    }
}
