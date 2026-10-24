package com.voidprivacy.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import android.graphics.Bitmap
import android.graphics.Color
import com.voidprivacy.app.R

/**
 * QR code composable using ZXing (already in deps).
 * Renders a scannable QR code for any string content.
 */
@Composable
fun QrCode(
    content: String,
    modifier: Modifier = Modifier,
    size: Dp = 200.dp,
) {
    val bitmap = remember(content) { generateQrBitmap(content, 512) }
    if (bitmap != null) {
        Image(
            painter = BitmapPainter(bitmap.asImageBitmap()),
            contentDescription = stringResource(R.string.comp_qr_code_desc, content),
            modifier = modifier.size(size),
        )
    }
}

private fun generateQrBitmap(content: String, dimension: Int): Bitmap? {
    return try {
        val hints = mapOf(
            EncodeHintType.MARGIN to 1,
            EncodeHintType.CHARACTER_SET to "UTF-8",
        )
        val result = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, dimension, dimension, hints)
        val bitmap = Bitmap.createBitmap(dimension, dimension, Bitmap.Config.RGB_565)
        for (x in 0 until dimension) {
            for (y in 0 until dimension) {
                bitmap.setPixel(x, y, if (result[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        bitmap
    } catch (_: Exception) {
        null
    }
}
