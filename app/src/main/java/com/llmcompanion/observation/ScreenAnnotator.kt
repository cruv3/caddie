package com.llmcompanion.observation

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Base64
import com.llmcompanion.model.AnnotatedCapture
import com.llmcompanion.model.IndexedUiNode
import com.llmcompanion.model.UiNode
import java.io.ByteArrayOutputStream

object ScreenAnnotator {

    fun annotate(bitmap: Bitmap, nodes: List<UiNode>): AnnotatedCapture {
        val interactive = nodes
            .filter { it.isClickable || it.isLongClickable || it.isEditable || it.isScrollable }
            .sortedBy { it.bounds.top }

        val indexedNodes = interactive.mapIndexed { i, node -> IndexedUiNode(i + 1, node) }

        val mutable = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutable)

        val rectPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.RED
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.RED
            style = Paint.Style.FILL
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 28f
        }

        for (item in indexedNodes) {
            val b = item.node.bounds
            canvas.drawRect(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat(), rectPaint)
            canvas.drawRect(b.left.toFloat(), b.top.toFloat(), b.left + 40f, b.top + 40f, badgePaint)
            canvas.drawText(item.index.toString(), b.left + 5f, b.top + 30f, textPaint)
        }

        val out = ByteArrayOutputStream()
        mutable.compress(Bitmap.CompressFormat.JPEG, 85, out)
        val base64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        mutable.recycle()

        return AnnotatedCapture(base64, indexedNodes)
    }
}
