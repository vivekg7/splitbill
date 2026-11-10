package com.crylo.splitbill.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import com.crylo.splitbill.R
import com.crylo.splitbill.core.GroupSnapshot
import com.crylo.splitbill.core.Summary
import com.crylo.splitbill.core.SummaryStrings
import java.io.File

/** Everything that leaves the app does so through the system share sheet or a UPI app. */
object Sharing {
    fun summaryStrings(context: Context) = SummaryStrings(
        title = context.getString(R.string.summary_title),
        total = context.getString(R.string.summary_total),
        balancesHeader = context.getString(R.string.summary_balances),
        getsBack = context.getString(R.string.summary_gets_back),
        owes = context.getString(R.string.summary_owes),
        settled = context.getString(R.string.summary_settled),
        planHeader = context.getString(R.string.summary_plan),
        transfer = context.getString(R.string.summary_transfer),
        allSettled = context.getString(R.string.summary_all_settled),
        upiLine = context.getString(R.string.summary_upi),
        footer = context.getString(R.string.summary_footer),
    )

    fun shareText(context: Context, snapshot: GroupSnapshot) {
        val text = Summary.text(snapshot, summaryStrings(context))
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        context.startActivity(Intent.createChooser(send, context.getString(R.string.share_summary)))
    }

    fun shareImage(context: Context, snapshot: GroupSnapshot) {
        val bitmap = renderSummary(snapshot, summaryStrings(context))
        val file = sharedFile(context, "summary.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        shareFile(context, file, "image/png", context.getString(R.string.share_summary))
    }

    /** Sends the group's export file through the share sheet (WhatsApp, Bluetooth, ...). */
    fun shareGroupFile(context: Context, fileName: String, json: String) {
        val file = sharedFile(context, fileName)
        file.writeText(json)
        shareFile(context, file, "application/json", context.getString(R.string.send_group_file))
    }

    /** Hands the payment to whichever UPI app the user picks; returns false if none. */
    fun openUpi(context: Context, link: String): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, link.toUri())
        // A chooser never throws, it just shows "no apps", so check for a UPI app first
        // (visible thanks to the <queries> entry in the manifest).
        val hasApp = context.packageManager.queryIntentActivities(intent, 0).isNotEmpty()
        if (hasApp) {
            try {
                context.startActivity(Intent.createChooser(intent, context.getString(R.string.pay_with_upi)))
                return true
            } catch (_: ActivityNotFoundException) {
                // Fall through to the toast.
            }
        }
        Toast.makeText(context, R.string.no_upi_app, Toast.LENGTH_LONG).show()
        return false
    }

    private fun sharedFile(context: Context, name: String): File {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        return File(dir, name)
    }

    private fun shareFile(context: Context, file: File, mime: String, title: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType(mime)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri(file.name, uri)
        context.startActivity(Intent.createChooser(send, title))
    }

    /**
     * Draws the summary as a light card. It is always light, whatever the app theme,
     * because the image is viewed in someone else's chat app.
     */
    private fun renderSummary(snapshot: GroupSnapshot, strings: SummaryStrings): Bitmap {
        val lines = Summary.lines(snapshot, strings)
        val width = 1080
        val pad = 72f
        val maxText = width - 2 * pad
        val accent = Color.rgb(0, 105, 92)
        val positive = Color.rgb(0, 121, 107)
        val negative = Color.rgb(198, 40, 40)
        val body = Color.rgb(33, 33, 33)
        val muted = Color.rgb(117, 117, 117)

        fun paint(size: Float, color: Int, bold: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
        val titlePaint = paint(64f, Color.WHITE, bold = true)
        val subtitlePaint = paint(38f, Color.WHITE)
        val headerPaint = paint(44f, accent, bold = true)
        val bodyPaint = paint(40f, body)
        val footerPaint = paint(32f, muted)

        // Lay out first (wrapping long lines) so the bitmap is exactly as tall as needed.
        data class Line(val text: String, val paint: Paint, val gapBefore: Float)
        val items = mutableListOf<Line>()
        fun add(text: String, p: Paint, gap: Float = 0f) {
            wrap(text, p, maxText).forEachIndexed { i, part -> items += Line(part, p, if (i == 0) gap else 0f) }
        }
        add(lines.balancesHeader, headerPaint)
        lines.balances.forEach { (text, value) ->
            add(text, paint(40f, if (value > 0) positive else if (value < 0) negative else body), 8f)
        }
        add(lines.planHeader, headerPaint, 48f)
        lines.transfers.forEach { add(it, bodyPaint, 8f) }
        add(lines.footer, footerPaint, 56f)

        val bannerHeight = pad + 64f + 24f + 38f + pad * 0.75f
        val lineHeight = { p: Paint -> p.fontSpacing }
        val contentHeight = items.sumOf { (lineHeight(it.paint) + it.gapBefore).toDouble() }.toFloat()
        val height = (bannerHeight + pad * 0.75f + contentHeight + pad).toInt()

        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        canvas.drawRect(0f, 0f, width.toFloat(), bannerHeight, Paint().apply { color = accent })
        var y = pad + 64f
        canvas.drawText(ellipsize(lines.title, titlePaint, maxText), pad, y, titlePaint)
        y += 24f + 38f
        canvas.drawText(ellipsize(lines.total, subtitlePaint, maxText), pad, y, subtitlePaint)
        y = bannerHeight + pad * 0.75f
        for (item in items) {
            y += item.gapBefore + lineHeight(item.paint)
            canvas.drawText(item.text, pad, y - item.paint.descent(), item.paint)
        }
        return bitmap
    }

    private fun wrap(text: String, paint: Paint, maxWidth: Float): List<String> {
        val out = mutableListOf<String>()
        var line = ""
        for (word in text.split(' ')) {
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (paint.measureText(candidate) <= maxWidth || line.isEmpty()) {
                line = candidate
            } else {
                out += line
                line = "    $word"
            }
        }
        if (line.isNotEmpty()) out += line
        return out.map { ellipsize(it, paint, maxWidth) }
    }

    private fun ellipsize(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 1 && paint.measureText(text.substring(0, end) + "…") > maxWidth) end--
        return text.substring(0, end) + "…"
    }
}
