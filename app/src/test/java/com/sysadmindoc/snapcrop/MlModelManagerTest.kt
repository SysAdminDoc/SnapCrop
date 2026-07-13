package com.sysadmindoc.snapcrop

import android.content.Context
import com.google.mlkit.nl.translate.TranslateLanguage
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class MlModelManagerTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    @Test
    fun latinAndEnglishStayBuiltInAndCannotBeRemoved() = runBlocking {
        assertTrue(OcrModelManager.isInstalled(context, OcrScript.LATIN))
        assertTrue(TranslationModelManager.isInstalled(TranslateLanguage.ENGLISH))
        assertTrue(runCatching { TranslationModelManager.download(TranslateLanguage.ENGLISH) }.isFailure)
        assertTrue(runCatching { TranslationModelManager.delete(TranslateLanguage.ENGLISH) }.isFailure)
    }

    @Test
    fun modelStateReducerKeepsProgressFailureAndSharedReleaseTruthful() {
        assertEquals(ManagedModelState.BUNDLED, ManagedModelTransitions.initial(true).state)
        assertEquals(ManagedModelState.CHECKING, ManagedModelTransitions.initial(false).state)
        assertEquals(ManagedModelState.NOT_INSTALLED, ManagedModelTransitions.availability(false).state)
        assertEquals(ManagedModelState.INSTALLED, ManagedModelTransitions.availability(true).state)

        val progress = ModelDownloadProgress(25, 100)
        assertEquals(progress, ManagedModelTransitions.downloading(progress).progress)
        assertEquals(ManagedModelState.FAILED, ManagedModelTransitions.failed(IllegalStateException("storage")).state)
        assertEquals(ManagedModelState.INSTALLED, ManagedModelTransitions.afterRelease(true).state)
        assertEquals(ManagedModelState.NOT_INSTALLED, ManagedModelTransitions.afterRelease(false).state)
    }

    @Test
    fun onlyLatinOcrIsBundledAndNoInstallTimePredownloadIsDeclared() {
        val catalog = repositoryFile("gradle/libs.versions.toml").readText()
        val manifest = repositoryFile("app/src/main/AndroidManifest.xml").readText()

        assertTrue(catalog.contains("com.google.mlkit\", name = \"text-recognition\""))
        listOf("chinese", "japanese", "korean", "devanagari").forEach { script ->
            assertTrue(
                catalog.contains(
                    "com.google.android.gms\", name = \"play-services-mlkit-text-recognition-$script\""
                )
            )
            assertFalse(catalog.contains("com.google.mlkit\", name = \"text-recognition-$script\""))
        }
        assertFalse(manifest.contains("com.google.mlkit.vision.DEPENDENCIES"))
    }

    @Test
    fun requestedOcrCannotTurnMissingModelIntoEmptySuccess() {
        val extractor = repositoryFile("app/src/main/java/com/sysadmindoc/snapcrop/TextExtractor.kt").readText()
        val callers = listOf("CropEditorScreen.kt", "MainActivity.kt", "GalleryScreen.kt", "ScreenshotService.kt")
            .map { repositoryFile("app/src/main/java/com/sysadmindoc/snapcrop/$it").readText() }

        assertTrue(extractor.contains("requireModel(script)"))
        assertTrue(extractor.contains("cont.resumeWithException(error)"))
        assertFalse(extractor.contains("addOnFailureListener { if (cont.isActive) cont.resume(emptyList()) }"))
        callers.forEach { assertTrue(it.contains("OcrModelUnavailableException")) }
    }

    @Test
    fun translationReadinessUsesLivePerLanguageInventory() {
        val translator = repositoryFile("app/src/main/java/com/sysadmindoc/snapcrop/TextTranslator.kt").readText()
        val status = repositoryFile("app/src/main/java/com/sysadmindoc/snapcrop/MlKitStatus.kt").readText()

        assertTrue(translator.contains("TranslationModelManager.isInstalled"))
        assertFalse(status.contains("translation_model_"))
        assertFalse(status.contains("isTranslationModelReady"))
        assertFalse(status.contains("markTranslationModelReady"))
    }

    private fun repositoryFile(path: String): File {
        var current = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        repeat(8) {
            File(current, path).takeIf(File::isFile)?.let { return it }
            current = current.parentFile ?: return@repeat
        }
        error("Unable to locate $path")
    }
}
