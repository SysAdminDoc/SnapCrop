package com.sysadmindoc.snapcrop

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
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
    /** Last target language picked in the OCR dialog, as an ML Kit language code. */
    const val PREF_TARGET = "ocr_translate_target"

    val defaultTarget: TranslationTarget by lazy { target(TranslateLanguage.ENGLISH) }

    /** Every language ML Kit translates on device (59 in translate 17.0.3), sorted by name. */
    val targetLanguages: List<TranslationTarget> by lazy {
        TranslateLanguage.getAllLanguages().distinct().map(::target).sortedBy { it.label }
    }

    /** One-tap chips in the OCR dialog; the rest of [targetLanguages] sit behind "More languages". */
    val quickTargets: List<TranslationTarget> by lazy {
        listOf(
            TranslateLanguage.ENGLISH,
            TranslateLanguage.SPANISH,
            TranslateLanguage.FRENCH,
            TranslateLanguage.GERMAN,
            TranslateLanguage.PORTUGUESE,
            TranslateLanguage.JAPANESE,
            TranslateLanguage.KOREAN,
            TranslateLanguage.CHINESE,
        ).map(::target)
    }

    private val languageIdentifier by lazy {
        LanguageIdentification.getClient()
    }

    fun target(language: String): TranslationTarget = TranslationTarget(language, labelFor(language))

    /** Target for a stored language code, or [defaultTarget] when it's missing or no longer offered. */
    fun targetFor(language: String?): TranslationTarget =
        targetLanguages.firstOrNull { it.language == language } ?: defaultTarget

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
            val missingLanguages = listOf(sourceLanguage, target.language)
                .distinct()
                .filter { it != TranslateLanguage.ENGLISH }
                .filterNot { TranslationModelManager.isInstalled(it) }
            if (missingLanguages.isNotEmpty()) {
                onProgress(context?.let { MlKitStatus.translationDownloadMessage(it, target.label) } ?: "Downloading the ${target.label} translation model…")
            }
            translator.downloadModelIfNeeded(conditions).awaitResult()
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

    /** English language name, matching the rest of the UI ("el" reads "Greek"); unknown codes fall back to the code. */
    fun labelFor(language: String): String {
        val name = Locale.forLanguageTag(language).getDisplayLanguage(Locale.ENGLISH)
        return if (name.isBlank() || name.equals(language, ignoreCase = true)) {
            language.uppercase(Locale.ROOT)
        } else {
            name.replaceFirstChar { it.titlecase(Locale.ENGLISH) }
        }
    }

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
