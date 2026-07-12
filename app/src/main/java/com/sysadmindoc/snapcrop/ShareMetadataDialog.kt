package com.sysadmindoc.snapcrop

import android.app.AlertDialog
import android.content.Context
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.activity.ComponentActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

internal const val PREF_SHARE_METADATA_DEFAULT = "share_metadata_policy_default"
internal const val EXTRA_PRIVACY_SHARE_URIS = "com.sysadmindoc.snapcrop.PRIVACY_SHARE_URIS"
internal const val EXTRA_DISMISS_DETECTED_NOTIFICATION = "com.sysadmindoc.snapcrop.DISMISS_DETECTED_NOTIFICATION"

internal suspend fun ComponentActivity.chooseShareMetadataPolicy(
    summary: MetadataSummary,
    allowSanitize: Boolean = true,
): ShareMetadataPolicy? = withContext(Dispatchers.Main) {
    suspendCancellableCoroutine { continuation ->
        val prefs = getSharedPreferences("snapcrop", Context.MODE_PRIVATE)
        val remembered = prefs.getString(PREF_SHARE_METADATA_DEFAULT, null)
            ?.let { runCatching { ShareMetadataPolicy.valueOf(it) }.getOrNull() }
            ?: if (prefs.getBoolean("strip_exif", false)) ShareMetadataPolicy.STRIP_ALL else null
        val initial = remembered?.takeIf { allowSanitize || it == ShareMetadataPolicy.PRESERVE }
            ?: if (allowSanitize) ShareMetadataPolicy.STRIP_ALL else ShareMetadataPolicy.PRESERVE

        val group = RadioGroup(this@chooseShareMetadataPolicy).apply { orientation = RadioGroup.VERTICAL }
        val buttons = ShareMetadataPolicy.entries.associateWith { policy ->
            RadioButton(this@chooseShareMetadataPolicy).apply {
                id = android.view.View.generateViewId()
                text = getString(
                    when (policy) {
                        ShareMetadataPolicy.STRIP_ALL -> R.string.share_metadata_strip_all
                        ShareMetadataPolicy.KEEP_SAFE -> R.string.share_metadata_keep_safe
                        ShareMetadataPolicy.PRESERVE -> R.string.share_metadata_preserve
                    }
                )
                isEnabled = allowSanitize || policy == ShareMetadataPolicy.PRESERVE
                isChecked = policy == initial
                group.addView(this)
            }
        }
        val remember = CheckBox(this@chooseShareMetadataPolicy).apply {
            text = getString(R.string.share_metadata_remember)
        }
        val categories = summary.categories.sortedBy { it.ordinal }.joinToString(", ") { category ->
            getString(
                when (category) {
                    MetadataCategory.LOCATION -> R.string.share_metadata_category_location
                    MetadataCategory.DEVICE -> R.string.share_metadata_category_device
                    MetadataCategory.TIME -> R.string.share_metadata_category_time
                    MetadataCategory.AUTHORSHIP -> R.string.share_metadata_category_authorship
                    MetadataCategory.SOFTWARE -> R.string.share_metadata_category_software
                    MetadataCategory.EMBEDDED_TEXT -> R.string.share_metadata_category_embedded_text
                    MetadataCategory.TECHNICAL -> R.string.share_metadata_category_technical
                }
            )
        }
        val pad = (20 * resources.displayMetrics.density).toInt()
        val content = LinearLayout(this@chooseShareMetadataPolicy).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, 0, pad, 0)
            addView(TextView(this@chooseShareMetadataPolicy).apply {
                text = getString(R.string.share_metadata_summary, categories)
            })
            if (!allowSanitize) {
                addView(TextView(this@chooseShareMetadataPolicy).apply {
                    text = getString(R.string.share_metadata_video_limit)
                })
            }
            addView(group)
            addView(remember)
        }
        lateinit var dialog: AlertDialog
        dialog = AlertDialog.Builder(this@chooseShareMetadataPolicy)
            .setTitle(R.string.share_metadata_title)
            .setView(content)
            .setPositiveButton(R.string.share_metadata_continue) { _, _ ->
                val selected = buttons.entries.firstOrNull { it.value.isChecked }?.key
                    ?: ShareMetadataPolicy.STRIP_ALL
                if (remember.isChecked) {
                    prefs.edit().putString(PREF_SHARE_METADATA_DEFAULT, selected.name).apply()
                }
                if (continuation.isActive) continuation.resume(selected)
            }
            .setNegativeButton(R.string.cancel) { _, _ ->
                if (continuation.isActive) continuation.resume(null)
            }
            .setOnCancelListener {
                if (continuation.isActive) continuation.resume(null)
            }
            .create()
        continuation.invokeOnCancellation { runOnUiThread { dialog.dismiss() } }
        dialog.show()
    }
}
