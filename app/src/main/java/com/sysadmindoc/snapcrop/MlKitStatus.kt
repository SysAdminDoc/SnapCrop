package com.sysadmindoc.snapcrop

import android.content.Context
import androidx.annotation.StringRes
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability

/**
 * [needsPlayServices] marks features SnapCrop can't run without Google Play services: the
 * segmentation model only ships through Play services. Latin OCR, barcodes, faces, entity
 * extraction and object detection are bundled in the APK, and translation downloads its own
 * models. The non-Latin OCR scripts also need Play services; they're checked per script by
 * [OcrModelManager].
 */
enum class MlKitFeature(
    /** The name as it reads mid-sentence ("retry background removal"). */
    @param:StringRes val labelRes: Int,
    /** The name as the subject that starts a sentence ("Background removal unavailable."). */
    @param:StringRes val subjectRes: Int,
    val needsPlayServices: Boolean = false,
) {
    TEXT_RECOGNITION(R.string.mlkit_feature_ocr, R.string.mlkit_feature_ocr_subject),
    BARCODE_SCANNING(R.string.mlkit_feature_barcodes, R.string.mlkit_feature_barcodes_subject),
    FACE_DETECTION(R.string.mlkit_feature_faces, R.string.mlkit_feature_faces_subject),
    ENTITY_EXTRACTION(R.string.mlkit_feature_entities, R.string.mlkit_feature_entities_subject),
    // The translate library and its model downloads run without Play services.
    TRANSLATION(R.string.mlkit_feature_translation, R.string.mlkit_feature_translation_subject),
    SUBJECT_SEGMENTATION(
        R.string.mlkit_feature_background_removal,
        R.string.mlkit_feature_background_removal_subject,
        needsPlayServices = true,
    ),
    OBJECT_DETECTION(R.string.mlkit_feature_ai_crop, R.string.mlkit_feature_ai_crop_subject);
}

object MlKitStatus {
    @StringRes val TRANSLATION_IDENTIFYING: Int = R.string.mlkit_identifying
    @StringRes val TRANSLATION_TRANSLATING: Int = R.string.mlkit_translating
    @StringRes val SUBJECT_SEGMENTATION_STARTING: Int = R.string.mlkit_segmentation_prep

    fun translationDownloadMessage(context: Context, targetLabel: String): String =
        context.getString(R.string.mlkit_translation_download, targetLabel)

    fun playServicesCode(context: Context): Int =
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context)

    /**
     * True when the phone has no usable Google Play services at all (CalyxOS, GrapheneOS without
     * sandboxed Play, plain AOSP builds), so no update can bring the feature back.
     */
    fun playServicesMissing(context: Context): Boolean = isMissing(playServicesCode(context))

    internal fun isMissing(code: Int): Boolean =
        code == ConnectionResult.SERVICE_MISSING || code == ConnectionResult.SERVICE_INVALID

    fun playServicesIssue(context: Context, feature: MlKitFeature): String? =
        playServicesIssue(context, context.getString(feature.subjectRes))

    /** [subject] names what can't run, for example "Background removal" or "Chinese OCR". */
    fun playServicesIssue(context: Context, subject: String): String? =
        playServicesMessage(context, subject, playServicesCode(context))

    internal fun playServicesMessage(context: Context, subject: String, code: Int): String? = when {
        code == ConnectionResult.SUCCESS -> null
        isMissing(code) -> context.getString(R.string.mlkit_play_missing, subject)
        code == ConnectionResult.SERVICE_DISABLED -> context.getString(R.string.mlkit_play_disabled, subject)
        else -> context.getString(
            R.string.mlkit_play_attention,
            GoogleApiAvailability.getInstance().getErrorString(code)
        )
    }

    fun userMessage(context: Context, feature: MlKitFeature, error: Throwable): String =
        userMessage(context, feature, error, playServicesCode(context))

    internal fun userMessage(
        context: Context,
        feature: MlKitFeature,
        error: Throwable,
        playServicesCode: Int,
    ): String {
        val raw = error.message?.trim().orEmpty()
        val lower = raw.lowercase()
        val label = context.getString(feature.labelRes)
        val subject = context.getString(feature.subjectRes)
        val playIssue = playServicesMessage(context, subject, playServicesCode)
        // Without Play services, or with it turned off, an update can't help, so the fallbacks drop that advice.
        val missing = isMissing(playServicesCode)
        val updateCantHelp = missing || playServicesCode == ConnectionResult.SERVICE_DISABLED
        return when {
            feature.needsPlayServices && playIssue != null -> playIssue
            raw.isBlank() ->
                if (updateCantHelp) context.getString(R.string.mlkit_unavailable, subject)
                else context.getString(R.string.mlkit_unavailable_update, subject)
            "wifi" in lower || "network" in lower ->
                context.getString(R.string.mlkit_retry_wifi, label)
            "storage" in lower || "space" in lower ->
                context.getString(R.string.mlkit_retry_storage, label)
            // A bundled feature runs without Play services, so its absence isn't the reason.
            "play services" in lower || "google play" in lower || "service" in lower ->
                if (missing) context.getString(R.string.mlkit_unavailable_detail, subject, raw)
                else playIssue ?: context.getString(R.string.mlkit_retry_update, label)
            "model" in lower || "download" in lower ->
                context.getString(R.string.mlkit_retry_generic, label)
            "source language" in lower || "not supported" in lower || "no text" in lower || "could not identify" in lower ->
                raw
            else ->
                if (updateCantHelp) context.getString(R.string.mlkit_unavailable_detail, subject, raw)
                else context.getString(R.string.mlkit_unavailable_detail_retry, subject, raw)
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
