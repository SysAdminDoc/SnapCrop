package com.sysadmindoc.snapcrop

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class TranslationTarget(val language: String, val label: String)

data class TextTranslation(
    val originalText: String,
    val translatedText: String,
    val sourceLanguage: String,
    val sourceLabel: String,
    val targetLanguage: String,
    val targetLabel: String,
    val alreadyTargetLanguage: Boolean
)

object TextTranslator {
    val defaultTarget = TranslationTarget(TranslateLanguage.ENGLISH, "English")

    val targetLanguages = listOf(
        defaultTarget,
        TranslationTarget(TranslateLanguage.SPANISH, "Spanish"),
        TranslationTarget(TranslateLanguage.FRENCH, "French"),
        TranslationTarget(TranslateLanguage.GERMAN, "German"),
        TranslationTarget(TranslateLanguage.PORTUGUESE, "Portuguese"),
        TranslationTarget(TranslateLanguage.JAPANESE, "Japanese"),
        TranslationTarget(TranslateLanguage.KOREAN, "Korean"),
        TranslationTarget(TranslateLanguage.CHINESE, "Chinese")
    )

    private val languageIdentifier by lazy {
        LanguageIdentification.getClient()
    }

    private val languageLabels = targetLanguages.associate { it.language to it.label } + mapOf(
        TranslateLanguage.ARABIC to "Arabic",
        TranslateLanguage.HINDI to "Hindi",
        TranslateLanguage.ITALIAN to "Italian",
        TranslateLanguage.RUSSIAN to "Russian",
        TranslateLanguage.TURKISH to "Turkish",
        TranslateLanguage.VIETNAMESE to "Vietnamese"
    )

    suspend fun translate(
        text: String,
        target: TranslationTarget = defaultTarget,
        context: Context? = null,
        onProgress: (String) -> Unit = {}
    ): TextTranslation {
        val original = text.trim()
        require(original.isNotEmpty()) { "No text selected" }

        context?.let { appContext ->
            MlKitStatus.playServicesIssue(appContext)?.let { throw IllegalStateException(it) }
        }
        onProgress(context?.getString(MlKitStatus.TRANSLATION_IDENTIFYING) ?: "Identifying source language on device…")
        val identifiedTag = languageIdentifier.identifyLanguage(original).awaitResult()
        require(identifiedTag != "und") { "Could not identify the source language" }

        val sourceLanguage = TranslateLanguage.fromLanguageTag(identifiedTag)
            ?: throw IllegalArgumentException("Detected language is not supported for on-device translation")

        if (sourceLanguage == target.language) {
            return TextTranslation(
                originalText = original,
                translatedText = original,
                sourceLanguage = sourceLanguage,
                sourceLabel = labelFor(sourceLanguage),
                targetLanguage = target.language,
                targetLabel = target.label,
                alreadyTargetLanguage = true
            )
        }

        val options = TranslatorOptions.Builder()
            .setSourceLanguage(sourceLanguage)
            .setTargetLanguage(target.language)
            .build()
        val translator = Translation.getClient(options)
        return try {
            val conditions = DownloadConditions.Builder().requireWifi().build()
            if (context?.let { !MlKitStatusStore.isTranslationModelReady(it, sourceLanguage, target.language) } != false) {
                onProgress(context?.let { MlKitStatus.translationDownloadMessage(it, target.label) } ?: "Downloading the ${target.label} translation model…")
            }
            translator.downloadModelIfNeeded(conditions).awaitResult()
            context?.let { MlKitStatusStore.markTranslationModelReady(it, sourceLanguage, target.language) }
            onProgress(context?.getString(MlKitStatus.TRANSLATION_TRANSLATING) ?: "Translating on device…")
            val translated = translator.translate(original).awaitResult()
            TextTranslation(
                originalText = original,
                translatedText = translated,
                sourceLanguage = sourceLanguage,
                sourceLabel = labelFor(sourceLanguage),
                targetLanguage = target.language,
                targetLabel = target.label,
                alreadyTargetLanguage = false
            )
        } finally {
            translator.close()
        }
    }

    fun labelFor(language: String): String =
        languageLabels[language] ?: language.uppercase()

    fun userMessage(context: Context, error: Throwable): String {
        val raw = error.message?.trim().orEmpty()
        return when {
            raw.contains("source language", ignoreCase = true) -> raw
            raw.contains("not supported", ignoreCase = true) -> raw
            raw.contains("No text", ignoreCase = true) -> raw
            raw.contains("wifi", ignoreCase = true) -> MlKitStatus.userMessage(context, MlKitFeature.TRANSLATION, error)
            else -> MlKitStatus.userMessage(context, MlKitFeature.TRANSLATION, error)
        }
    }
}

private suspend fun <T> Task<T>.awaitResult(): T =
    suspendCancellableCoroutine { cont ->
        addOnSuccessListener { result ->
            if (cont.isActive) cont.resume(result)
        }
        addOnFailureListener { error ->
            if (cont.isActive) cont.resumeWithException(error)
        }
        addOnCanceledListener {
            if (cont.isActive) cont.cancel()
        }
    }
