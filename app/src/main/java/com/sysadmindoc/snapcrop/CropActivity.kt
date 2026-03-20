package com.sysadmindoc.snapcrop

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import com.sysadmindoc.snapcrop.ui.theme.SnapCropTheme
import java.io.IOException

class CropActivity : ComponentActivity() {

    private var originalBitmap: Bitmap? = null
    private val cropRect = mutableStateOf(Rect(0, 0, 0, 0))
    private var sourceUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        sourceUri = intent.data
        if (sourceUri == null) {
            finish()
            return
        }

        loadBitmap(sourceUri!!)

        setContent {
            SnapCropTheme {
                originalBitmap?.let { bmp ->
                    CropEditorScreen(
                        bitmap = bmp,
                        initialCropRect = cropRect.value,
                        onSave = { rect -> saveCropped(bmp, rect) },
                        onSaveOriginal = { saveOriginal() },
                        onDiscard = { finish() },
                        onAutoCrop = { AutoCrop.detect(bmp) }
                    )
                }
            }
        }
    }

    private fun loadBitmap(uri: Uri) {
        try {
            contentResolver.openInputStream(uri)?.use { stream ->
                originalBitmap = BitmapFactory.decodeStream(stream)
                originalBitmap?.let { bmp ->
                    cropRect.value = AutoCrop.detect(bmp)
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun saveCropped(bitmap: Bitmap, rect: Rect) {
        val cropped = Bitmap.createBitmap(
            bitmap,
            rect.left.coerceAtLeast(0),
            rect.top.coerceAtLeast(0),
            (rect.width()).coerceAtMost(bitmap.width - rect.left.coerceAtLeast(0)),
            (rect.height()).coerceAtMost(bitmap.height - rect.top.coerceAtLeast(0))
        )
        saveToGallery(cropped, "SnapCrop_${System.currentTimeMillis()}")
        cropped.recycle()
    }

    private fun saveOriginal() {
        // Just close — original is already saved
        finish()
    }

    private fun saveToGallery(bitmap: Bitmap, name: String) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$name.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SnapCrop")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        if (uri == null) {
            Toast.makeText(this, "Failed to save", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            contentResolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            contentResolver.update(uri, values, null, null)
            Toast.makeText(this, "Saved to Pictures/SnapCrop", Toast.LENGTH_SHORT).show()

            // Optionally delete original screenshot
            deleteOriginal()
        } catch (e: IOException) {
            Toast.makeText(this, "Save failed", Toast.LENGTH_SHORT).show()
            contentResolver.delete(uri, null, null)
        }

        finish()
    }

    private fun deleteOriginal() {
        sourceUri?.let { uri ->
            try {
                contentResolver.delete(uri, null, null)
            } catch (_: Exception) {
                // Can't delete — that's fine, user still has the cropped version
            }
        }
    }
}
