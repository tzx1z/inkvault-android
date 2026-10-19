package dev.inkov.inkvault.android

import android.content.res.Resources
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.zxing.BinaryBitmap
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import uniffi.inkvault_uniffi.totp

/** Распознает QR-код в кадрах камеры. onFound вызывается в потоке анализа на каждый кадр с кодом. */
class QrAnalyzer(private val onFound: (String) -> Unit) : ImageAnalysis.Analyzer {
    private val reader = QRCodeReader()
    // буфер кадра переиспользуется: новый массив на каждый кадр нагружал бы сборщик мусора
    private var luminance = ByteArray(0)

    override fun analyze(image: ImageProxy) {
        val text = image.use {
            // для распознавания достаточно яркости - плоскости Y кадра YUV_420_888
            val plane = it.planes[0]
            val buffer = plane.buffer
            if (luminance.size != buffer.remaining()) luminance = ByteArray(buffer.remaining())
            buffer.get(luminance)
            val source = PlanarYUVLuminanceSource(luminance, plane.rowStride, it.height, 0, 0, it.width, it.height, false)
            try {
                reader.decode(BinaryBitmap(HybridBinarizer(source))).text
            } catch (e: ReaderException) {
                null
            } finally {
                reader.reset()
            }
        }
        text?.let(onFound)
    }
}

/** Почему текст из QR-кода не подходит как секрет TOTP, null - подходит. */
fun scannedSecretProblem(resources: Resources, text: String): String? {
    val error = runCatching { totp(text, 0u) }.exceptionOrNull() ?: return null
    return when {
        // экспорт Google Authenticator - protobuf с несколькими учетными записями, а не ссылка otpauth
        text.startsWith("otpauth-migration:", ignoreCase = true) -> resources.getString(R.string.qr_migration)
        text.startsWith("otpauth:", ignoreCase = true) && error.message != null -> error.message
        else -> resources.getString(R.string.qr_not_totp)
    }
}
