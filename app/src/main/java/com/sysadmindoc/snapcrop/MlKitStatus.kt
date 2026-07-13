package com.sysadmindoc.snapcrop

import android.content.Context
import androidx.annotation.StringRes
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
    @StringRes val TRANSLATION_IDENTIFYING: Int = R.string.mlkit_identifying
    @StringRes val TRANSLATION_TRANSLATING: Int = R.string.mlkit_translating
    @StringRes val SUBJECT_SEGMENTATION_STARTING: Int = R.string.mlkit_segmentation_prep

    fun translationDownloadMessage(context: Context, targetLabel: String): String =
        context.getString(R.string.mlkit_translation_download, targetLabel)

    fun playServicesIssue(context: Context): String? {
        val availability = GoogleApiAvailability.getInstance()
        val code = availability.isGooglePlayServicesAvailable(context)
        if (code == ConnectionResult.SUCCESS) return null
        val label = availability.getErrorString(code)
        return context.getString(R.string.mlkit_play_attention, label)
    }

    fun userMessage(context: Context, feature: MlKitFeature, error: Throwable): String {
        val raw = error.message?.trim().orEmpty()
        val lower = raw.lowercase()
        return when {
            raw.isBlank() ->
                "${feature.label.replaceFirstChar { it.uppercase() }} unavailable. Retry after updating Google Play services."
            "wifi" in lower || "network" in lower ->
                context.getString(R.string.mlkit_retry_wifi, feature.label)
            "storage" in lower || "space" in lower ->
                context.getString(R.string.mlkit_retry_storage, feature.label)
            "play services" in lower || "google play" in lower || "service" in lower ->
                context.getString(R.string.mlkit_retry_update, feature.label)
            "model" in lower || "download" in lower ->
                context.getString(R.string.mlkit_retry_generic, feature.label)
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
}
