package com.sysadmindoc.snapcrop

import android.content.Context
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability

enum class MlKitFeature(val label: String) {
    TEXT_RECOGNITION("OCR"),
    BARCODE_SCANNING("barcode scanning"),
    FACE_DETECTION("face detection"),
    ENTITY_EXTRACTION("sensitive text detection"),
    TRANSLATION("translation"),
    SUBJECT_SEGMENTATION("background removal"),
    OBJECT_DETECTION("AI crop")
}

object MlKitStatus {
    const val TRANSLATION_IDENTIFYING = "Identifying source language on device..."
    const val TRANSLATION_TRANSLATING = "Translating on device..."
    const val SUBJECT_SEGMENTATION_STARTING =
        "Preparing subject segmentation on device. First run may download a Play Services model over Wi-Fi."

    fun translationDownloadMessage(targetLabel: String): String =
        "Downloading the $targetLabel translation model over Wi-Fi. Keep the app open and retry if Play Services asks for storage."

    fun playServicesIssue(context: Context): String? {
        val availability = GoogleApiAvailability.getInstance()
        val code = availability.isGooglePlayServicesAvailable(context)
        if (code == ConnectionResult.SUCCESS) return null
        val label = availability.getErrorString(code)
        return "Google Play services needs attention ($label). Update Play services, free storage if prompted, then retry."
    }

    fun userMessage(feature: MlKitFeature, error: Throwable): String {
        val raw = error.message?.trim().orEmpty()
        val lower = raw.lowercase()
        return when {
            raw.isBlank() ->
                "${feature.label.replaceFirstChar { it.uppercase() }} unavailable. Retry after updating Google Play services."
            "wifi" in lower || "network" in lower ->
                "Connect to Wi-Fi so Google Play services can download the ${feature.label} model, then retry."
            "storage" in lower || "space" in lower ->
                "Free device storage so Google Play services can prepare ${feature.label}, then retry."
            "play services" in lower || "google play" in lower || "service" in lower ->
                "Update Google Play services and retry ${feature.label}."
            "model" in lower || "download" in lower ->
                "The ${feature.label} model is not ready yet. Connect to Wi-Fi, keep SnapCrop open, and retry."
            "source language" in lower || "not supported" in lower || "no text" in lower || "could not identify" in lower ->
                raw
            else ->
                "${feature.label.replaceFirstChar { it.uppercase() }} unavailable. $raw Retry after updating Google Play services or reconnecting to Wi-Fi."
        }
    }
}

object MlKitStatusStore {
    private const val PREFS = "snapcrop_mlkit_status"
    private const val KEY_SUBJECT_SEGMENTATION_READY = "subject_segmentation_ready"
    private const val KEY_SUBJECT_SEGMENTATION_LAST_ERROR = "subject_segmentation_last_error"

    fun isTranslationModelReady(context: Context, sourceLanguage: String, targetLanguage: String): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(translationKey(sourceLanguage, targetLanguage), false)
    }

    fun markTranslationModelReady(context: Context, sourceLanguage: String, targetLanguage: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(translationKey(sourceLanguage, targetLanguage), true)
            .apply()
    }

    fun markSubjectSegmentationReady(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SUBJECT_SEGMENTATION_READY, true)
            .remove(KEY_SUBJECT_SEGMENTATION_LAST_ERROR)
            .apply()
    }

    fun markSubjectSegmentationError(context: Context, message: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SUBJECT_SEGMENTATION_LAST_ERROR, message)
            .apply()
    }

    fun subjectSegmentationReady(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_SUBJECT_SEGMENTATION_READY, false)

    fun subjectSegmentationLastError(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_SUBJECT_SEGMENTATION_LAST_ERROR, "").orEmpty()

    private fun translationKey(sourceLanguage: String, targetLanguage: String): String =
        "translation_model_${sourceLanguage}_$targetLanguage"
}
