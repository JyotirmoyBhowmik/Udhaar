package com.jyotirmoyb.udhaar.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import androidx.core.content.FileProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.io.File
import java.io.FileOutputStream

object UpiQrGenerator {

    /**
     * Builds a standard NPCI UPI payment URI:
     * upi://pay?pa=YOUR_UPI_ID&pn=NAME&am=AMOUNT&cu=INR
     */
    fun buildUpiUri(
        upiId: String,
        payeeName: String,
        amountInr: Double? = null,
        transactionNote: String? = null
    ): String {
        val cleanVpa = upiId.trim()
        val cleanName = payeeName.trim().ifBlank { "Udhaar Payee" }
        val builder = Uri.Builder()
            .scheme("upi")
            .authority("pay")
            .appendQueryParameter("pa", cleanVpa)
            .appendQueryParameter("pn", cleanName)

        if (amountInr != null && amountInr > 0.0) {
            builder.appendQueryParameter("am", InterestCalculator.formatPlainAmount(amountInr))
        }
        builder.appendQueryParameter("cu", "INR")
        if (!transactionNote.isNullOrBlank()) {
            builder.appendQueryParameter("tn", transactionNote.trim())
        }
        return builder.build().toString()
    }

    /**
     * Generates a crisp monochrome QR code Bitmap offline using ZXing.
     */
    fun generateQrBitmap(content: String, sizePx: Int = 640): Bitmap? {
        if (content.isBlank()) return null
        return runCatching {
            val hints = mapOf(
                EncodeHintType.CHARACTER_SET to "UTF-8",
                EncodeHintType.MARGIN to 1
            )
            val bitMatrix = QRCodeWriter().encode(
                content,
                BarcodeFormat.QR_CODE,
                sizePx,
                sizePx,
                hints
            )
            val width = bitMatrix.width
            val height = bitMatrix.height
            val pixels = IntArray(width * height)
            for (y in 0 until height) {
                val offset = y * width
                for (x in 0 until width) {
                    pixels[offset + x] = if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE
                }
            }
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
                setPixels(pixels, 0, width, 0, 0, width, height)
            }
        }.getOrNull()
    }

    /**
     * Renders a complete high-contrast monochrome UPI Payment Card image for sharing on WhatsApp.
     */
    fun createShareableQrCardBitmap(
        upiId: String,
        payeeName: String,
        amountInr: Double?,
        partyName: String?
    ): Bitmap? {
        val uri = buildUpiUri(upiId = upiId, payeeName = payeeName, amountInr = amountInr)
        val qrBitmap = generateQrBitmap(uri, sizePx = 560) ?: return null

        val cardWidth = 800
        val cardHeight = 1040
        val cardBitmap = Bitmap.createBitmap(cardWidth, cardHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(cardBitmap)

        canvas.drawColor(Color.WHITE)

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 6f
        }
        canvas.drawRect(24f, 24f, cardWidth - 24f, cardHeight - 24f, borderPaint)

        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.FILL
        }
        canvas.drawRect(24f, 24f, cardWidth - 24f, 150f, headerPaint)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 42f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("UDHAAR • UPI SETTLEMENT", cardWidth / 2f, 102f, titlePaint)

        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 34f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val cleanPayee = payeeName.ifBlank { "Udhaar Account" }
        canvas.drawText(cleanPayee.uppercase(), cardWidth / 2f, 220f, labelPaint)

        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            textSize = 26f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("VPA: ${upiId.trim()}", cardWidth / 2f, 265f, subPaint)

        val qrLeft = (cardWidth - qrBitmap.width) / 2f
        val qrTop = 300f
        canvas.drawBitmap(qrBitmap, qrLeft, qrTop, null)

        if (amountInr != null && amountInr > 0.0) {
            val amountPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = 48f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            val formattedAmt = "AMOUNT DUE: ₹${InterestCalculator.formatPlainAmount(amountInr)}"
            canvas.drawText(formattedAmt, cardWidth / 2f, 920f, amountPaint)
        } else {
            canvas.drawText("SCAN WITH GPAY / PHONEPE / PAYTM", cardWidth / 2f, 920f, subPaint)
        }

        if (!partyName.isNullOrBlank()) {
            canvas.drawText("FOR: ${partyName.uppercase()}", cardWidth / 2f, 975f, subPaint)
        } else {
            canvas.drawText("GPay • PhonePe • Paytm • BHIM UPI", cardWidth / 2f, 975f, subPaint)
        }

        return cardBitmap
    }

    fun shareQrCardImage(
        context: Context,
        upiId: String,
        payeeName: String,
        amountInr: Double?,
        partyName: String?
    ) {
        val cardBitmap = createShareableQrCardBitmap(upiId, payeeName, amountInr, partyName) ?: return
        runCatching {
            val cacheDir = File(context.cacheDir, "qr_codes").apply { mkdirs() }
            val imageFile = File(cacheDir, "udhaar_upi_qr_${System.currentTimeMillis()}.png")
            FileOutputStream(imageFile).use { out ->
                cardBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                imageFile
            )
            val shareText = buildString {
                append("UPI Payment QR for ")
                append(payeeName.ifBlank { upiId })
                if (amountInr != null && amountInr > 0.0) {
                    append(" • Amount: ₹${InterestCalculator.formatPlainAmount(amountInr)}")
                }
                append("\nUPI ID: $upiId")
            }

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_TEXT, shareText)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(shareIntent, "Share UPI QR Code").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        }
    }
}
