package com.hack.kidhack.core.designsystem.component

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.hack.kidhack.R
import androidx.compose.material3.Text

/**
 * 결제 토큰을 QR 로 그린다. 네트워크 없이 zxing-core 로 즉석 인코딩한다.
 *
 * 화면 픽셀 크기와 무관하게 [MATRIX_SIZE] 로 뜬 뒤 `FilterQuality.None` 없이
 * ContentScale.Fit 으로 늘리므로, 모듈 경계가 뭉개지지 않도록 배율이 정수에 가깝게 잡힌다.
 */
@Composable
fun QrCode(
    content: String,
    modifier: Modifier = Modifier,
    foreground: Color = Color.Black,
    background: Color = Color.White,
) {
    val bitmap = remember(content, foreground, background) {
        runCatching { encodeQr(content, foreground.toArgb(), background.toArgb()) }.getOrNull()
    }
    Box(modifier = modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        if (bitmap == null) {
            Text(stringResource(R.string.child_pay_qr_error))
        } else {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = stringResource(R.string.child_pay_qr_desc),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

private const val MATRIX_SIZE = 512
private const val QUIET_ZONE_MODULES = 1

private fun encodeQr(content: String, foreground: Int, background: Int): Bitmap {
    val hints = mapOf(
        EncodeHintType.MARGIN to QUIET_ZONE_MODULES,
        EncodeHintType.CHARACTER_SET to "UTF-8",
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
    )
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, MATRIX_SIZE, MATRIX_SIZE, hints)
    val pixels = IntArray(matrix.width * matrix.height)
    for (y in 0 until matrix.height) {
        val offset = y * matrix.width
        for (x in 0 until matrix.width) {
            pixels[offset + x] = if (matrix.get(x, y)) foreground else background
        }
    }
    return Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
}
