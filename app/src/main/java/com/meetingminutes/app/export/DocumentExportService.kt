package com.meetingminutes.app.export

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.meetingminutes.app.data.MeetingDetail
import com.meetingminutes.app.data.MeetingRepository
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DocumentExportService(
    private val context: Context,
    private val repository: MeetingRepository
) {
    suspend fun exportMarkdown(detail: MeetingDetail): File {
        val file = exportFile(detail.meeting.title, "md")
        file.writeText(markdownText(detail), Charsets.UTF_8)
        repository.saveExport(detail.meeting.id, "markdown", file.absolutePath)
        return file
    }

    suspend fun exportPdf(detail: MeetingDetail): File {
        val file = exportFile(detail.meeting.title, "pdf")
        val document = PdfDocument()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 11f
            color = android.graphics.Color.rgb(28, 35, 32)
        }
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 18f
            isFakeBoldText = true
            color = android.graphics.Color.rgb(31, 107, 91)
        }
        val pageWidth = 595
        val pageHeight = 842
        var pageNumber = 1
        var page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        var canvas = page.canvas
        var y = 54f
        canvas.drawText(detail.meeting.title, 36f, y, titlePaint)
        y += 32f
        wrap(markdownText(detail), 70).forEach { line ->
            if (y > pageHeight - 48) {
                document.finishPage(page)
                pageNumber += 1
                page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
                canvas = page.canvas
                y = 48f
            }
            canvas.drawText(line, 36f, y, paint)
            y += 18f
        }
        document.finishPage(page)
        file.outputStream().use { document.writeTo(it) }
        document.close()
        repository.saveExport(detail.meeting.id, "pdf", file.absolutePath)
        return file
    }

    fun shareIntent(file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        return Intent(Intent.ACTION_SEND)
            .setType(if (file.extension == "pdf") "application/pdf" else "text/markdown")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    private fun exportFile(title: String, extension: String): File {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val safe = title.replace(Regex("[^\\p{L}\\p{N}_-]+"), "_").trim('_').ifBlank { "meeting" }
        return File(dir, "${safe}_${System.currentTimeMillis()}.$extension")
    }

    fun markdownText(detail: MeetingDetail): String {
        val summary = detail.summary?.markdown
        if (!summary.isNullOrBlank()) return summary
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(detail.meeting.startedAt))
        return buildString {
            appendLine("# ${detail.meeting.title}")
            appendLine()
            appendLine("时间：$time")
            appendLine()
            appendLine("## 原始转写")
            detail.transcript.forEach { appendLine("${it.speaker}：${it.text}") }
        }
    }

    private fun wrap(text: String, maxChars: Int): List<String> {
        val lines = mutableListOf<String>()
        text.lines().forEach { source ->
            if (source.isBlank()) {
                lines += ""
            } else {
                var remaining = source
                while (remaining.length > maxChars) {
                    lines += remaining.take(maxChars)
                    remaining = remaining.drop(maxChars)
                }
                lines += remaining
            }
        }
        return lines
    }
}
