package com.sysadmindoc.snapcrop

import android.content.Context
import android.net.ConnectivityManager
import com.google.android.gms.common.moduleinstall.InstallStatusListener
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.android.gms.common.moduleinstall.ModuleInstallStatusCodes
import com.google.android.gms.common.moduleinstall.ModuleInstallStatusUpdate
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class ModelDownloadProgress(val downloadedBytes: Long, val totalBytes: Long)

class OcrModelUnavailableException(
    val script: OcrScript,
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)

/** Google Play services-backed lifecycle for the four optional OCR script modules. */
object OcrModelManager {
    const val ESTIMATED_INSTALLED_BYTES = 4_000_000L

    suspend fun isInstalled(context: Context, script: OcrScript): Boolean {
        if (script == OcrScript.LATIN) return true
        MlKitStatus.playServicesIssue(context)?.let {
            throw OcrModelUnavailableException(script, it)
        }
        return withRecognizer(script) { recognizer ->
            ModuleInstall.getClient(context)
                .areModulesAvailable(recognizer)
                .awaitModelTask()
                .areModulesAvailable()
        }
    }

    suspend fun requireInstalled(context: Context, script: OcrScript) {
        if (!isInstalled(context, script)) {
            throw OcrModelUnavailableException(
                script,
                context.getString(R.string.ml_model_ocr_missing, script.label),
            )
        }
    }

    suspend fun install(
        context: Context,
        script: OcrScript,
        onProgress: (ModelDownloadProgress?) -> Unit = {},
    ) {
        require(script != OcrScript.LATIN) { "The Latin OCR model is bundled" }
        if (isInstalled(context, script)) return
        requireUnmeteredNetwork(context)

        withRecognizer(script) { recognizer ->
            val client = ModuleInstall.getClient(context)
            val completion = CompletableDeferred<Unit>()
            val listener = InstallStatusListener { update ->
                update.progressInfo?.let { progress ->
                    onProgress(ModelDownloadProgress(progress.bytesDownloaded, progress.totalBytesToDownload))
                }
                when (update.installState) {
                    ModuleInstallStatusUpdate.InstallState.STATE_COMPLETED -> completion.complete(Unit)
                    ModuleInstallStatusUpdate.InstallState.STATE_CANCELED ->
                        completion.completeExceptionally(IllegalStateException("OCR model download was canceled"))
                    ModuleInstallStatusUpdate.InstallState.STATE_FAILED -> completion.completeExceptionally(
                        IllegalStateException(
                            ModuleInstallStatusCodes.getStatusCodeString(update.errorCode)
                        )
                    )
                }
            }
            val request = ModuleInstallRequest.newBuilder()
                .addApi(recognizer)
                .setListener(listener)
                .build()
            try {
                val response = client.installModules(request).awaitModelTask()
                if (!response.areModulesAlreadyInstalled()) completion.await()
                requireInstalled(context, script)
            } catch (error: Exception) {
                throw OcrModelUnavailableException(
                    script,
                    MlKitStatus.userMessage(context, MlKitFeature.TEXT_RECOGNITION, error),
                    error,
                )
            } finally {
                runCatching { client.unregisterListener(listener).awaitModelTask() }
            }
        }
    }

    /** Best-effort release; Play services may retain a module shared by another app. */
    suspend fun release(context: Context, script: OcrScript) {
        require(script != OcrScript.LATIN) { "The Latin OCR model is bundled" }
        withRecognizer(script) { recognizer ->
            ModuleInstall.getClient(context).releaseModules(recognizer).awaitModelTask()
        }
    }

    private fun requireUnmeteredNetwork(context: Context) {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        require(connectivity.activeNetwork != null && !connectivity.isActiveNetworkMetered) {
            context.getString(R.string.ml_model_wifi_required)
        }
    }

    private suspend fun <T> withRecognizer(script: OcrScript, block: suspend (TextRecognizer) -> T): T {
        val recognizer = TextRecognition.getClient(
            when (script) {
                OcrScript.LATIN -> error("Latin OCR does not use an optional module")
                OcrScript.CHINESE -> ChineseTextRecognizerOptions.Builder().build()
                OcrScript.JAPANESE -> JapaneseTextRecognizerOptions.Builder().build()
                OcrScript.KOREAN -> KoreanTextRecognizerOptions.Builder().build()
                OcrScript.DEVANAGARI -> DevanagariTextRecognizerOptions.Builder().build()
            }
        )
        return try {
            block(recognizer)
        } finally {
            recognizer.close()
        }
    }
}

/** Explicit lifecycle for ML Kit's roughly 30 MB per-language translation models. */
object TranslationModelManager {
    const val ESTIMATED_INSTALLED_BYTES = 30_000_000L
    private val manager: RemoteModelManager
        get() = RemoteModelManager.getInstance()

    suspend fun installedLanguages(): Set<String> =
        manager.getDownloadedModels(TranslateRemoteModel::class.java)
            .awaitModelTask()
            .mapTo(linkedSetOf(TranslateLanguage.ENGLISH)) { it.language }

    suspend fun isInstalled(language: String): Boolean =
        language == TranslateLanguage.ENGLISH || manager.isModelDownloaded(model(language)).awaitModelTask()

    suspend fun download(language: String) {
        require(language != TranslateLanguage.ENGLISH) { "English translation is built in" }
        manager.download(
            model(language),
            DownloadConditions.Builder().requireWifi().build(),
        ).awaitModelTask()
    }

    suspend fun delete(language: String) {
        require(language != TranslateLanguage.ENGLISH) { "English translation is built in" }
        manager.deleteDownloadedModel(model(language)).awaitModelTask()
    }

    private fun model(language: String): TranslateRemoteModel =
        TranslateRemoteModel.Builder(language).build()
}

private suspend fun <T> Task<T>.awaitModelTask(): T =
    suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { result -> if (continuation.isActive) continuation.resume(result) }
        addOnFailureListener { error -> if (continuation.isActive) continuation.resumeWithException(error) }
        addOnCanceledListener { if (continuation.isActive) continuation.cancel() }
    }
