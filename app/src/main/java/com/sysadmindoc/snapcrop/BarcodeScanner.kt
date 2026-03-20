package com.sysadmindoc.snapcrop

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

data class ScannedCode(val rawValue: String, val displayValue: String, val bounds: Rect, val type: Int)

object BarcodeScanner {

    private val scanner by lazy { BarcodeScanning.getClient() }

    suspend fun scan(bitmap: Bitmap): List<ScannedCode> {
        return suspendCancellableCoroutine { cont ->
            val image = InputImage.fromBitmap(bitmap, 0)
            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    val codes = barcodes.mapNotNull { barcode ->
                        val bounds = barcode.boundingBox ?: return@mapNotNull null
                        val raw = barcode.rawValue ?: return@mapNotNull null
                        val display = when (barcode.valueType) {
                            Barcode.TYPE_URL -> barcode.url?.url ?: raw
                            Barcode.TYPE_WIFI -> "WiFi: ${barcode.wifi?.ssid ?: raw}"
                            Barcode.TYPE_EMAIL -> barcode.email?.address ?: raw
                            Barcode.TYPE_PHONE -> barcode.phone?.number ?: raw
                            Barcode.TYPE_SMS -> "SMS: ${barcode.sms?.phoneNumber ?: raw}"
                            Barcode.TYPE_GEO -> "Location: ${barcode.geoPoint?.lat},${barcode.geoPoint?.lng}"
                            else -> raw
                        }
                        ScannedCode(raw, display, bounds, barcode.valueType)
                    }
                    cont.resume(codes)
                }
                .addOnFailureListener { cont.resume(emptyList()) }
        }
    }
}
