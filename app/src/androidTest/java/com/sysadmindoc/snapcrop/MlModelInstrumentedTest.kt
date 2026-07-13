package com.sysadmindoc.snapcrop

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.ConnectivityManager
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNoException
import org.junit.Assume.assumeTrue
import org.junit.Test

class MlModelInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun optionalJapaneseOcrDownloadsRunsAndReleasesWithoutEmptyFailure() {
        runBlocking {
            assumeTrue(MlKitStatus.playServicesIssue(context) == null)
            val connectivity = context.getSystemService(ConnectivityManager::class.java)
            assumeTrue(connectivity.activeNetwork != null && !connectivity.isActiveNetworkMetered)

            try {
                OcrModelManager.install(context, OcrScript.JAPANESE)
            } catch (error: OcrModelUnavailableException) {
                if (error.cause?.message == "INTERNAL_ERROR") {
                    assumeNoException(
                        "The emulator's Google Play services image cannot install optional modules",
                        error,
                    )
                }
                throw error
            }
            assertTrue(OcrModelManager.isInstalled(context, OcrScript.JAPANESE))

            val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply {
                eraseColor(Color.WHITE)
            }
            try {
                TextExtractor.extract(bitmap, OcrScript.JAPANESE)
            } finally {
                bitmap.recycle()
                OcrModelManager.release(context, OcrScript.JAPANESE)
            }
        }
    }
}
