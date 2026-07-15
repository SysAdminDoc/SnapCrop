package com.sysadmindoc.snapcrop

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.sysadmindoc.snapcrop.ui.theme.Danger
import com.sysadmindoc.snapcrop.ui.theme.OnSurface
import com.sysadmindoc.snapcrop.ui.theme.OnSurfaceVariant
import com.sysadmindoc.snapcrop.ui.theme.Primary
import com.sysadmindoc.snapcrop.ui.theme.SurfaceVariant

internal enum class ExternalLaunchOutcome {
    LAUNCHED,
    UNAVAILABLE,
    FAILED,
}

internal class ExternalIntentLauncher(
    private val canResolve: (Intent) -> Boolean,
    private val start: (Intent) -> Unit,
) {
    constructor(context: Context) : this(
        canResolve = { intent -> intent.resolveActivity(context.packageManager) != null },
        start = context::startActivity,
    )

    fun launchDial(value: String): ExternalLaunchOutcome = launch(
        Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", value, null)),
    )

    fun launchEmail(value: String): ExternalLaunchOutcome = launch(
        Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", value, null)),
    )

    fun launchUrl(value: String): ExternalLaunchOutcome {
        val uri = runCatching { Uri.parse(value.trim()) }.getOrNull()
            ?: return ExternalLaunchOutcome.FAILED
        if (uri.scheme?.lowercase() !in setOf("http", "https") || uri.host.isNullOrBlank()) {
            return ExternalLaunchOutcome.FAILED
        }
        return launch(Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE))
    }

    internal fun launch(intent: Intent): ExternalLaunchOutcome {
        val resolved = try {
            canResolve(intent)
        } catch (_: Exception) {
            return ExternalLaunchOutcome.FAILED
        }
        if (!resolved) return ExternalLaunchOutcome.UNAVAILABLE
        return try {
            start(intent)
            ExternalLaunchOutcome.LAUNCHED
        } catch (_: ActivityNotFoundException) {
            ExternalLaunchOutcome.FAILED
        } catch (_: Exception) {
            ExternalLaunchOutcome.FAILED
        }
    }
}

internal enum class ExternalFallbackCopyKind { VALUE, URL }

internal data class ExternalActionFallback(
    val outcome: ExternalLaunchOutcome,
    val copyValue: String,
    val copyKind: ExternalFallbackCopyKind,
) {
    init {
        require(outcome != ExternalLaunchOutcome.LAUNCHED)
    }
}

@Composable
internal fun ExternalActionFallbackDialog(
    fallback: ExternalActionFallback,
    onDismiss: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val unavailable = fallback.outcome == ExternalLaunchOutcome.UNAVAILABLE
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (unavailable) R.string.external_action_unavailable_title
                    else R.string.external_action_failed_title,
                ),
                color = OnSurface,
            )
        },
        text = {
            Text(
                stringResource(
                    if (unavailable) R.string.external_action_unavailable_body
                    else R.string.external_action_failed_body,
                ),
                color = OnSurfaceVariant,
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val copied = copyFallbackToClipboard(context, fallback)
                    Toast.makeText(
                        context,
                        if (copied) R.string.external_action_copied
                        else R.string.external_action_copy_failed,
                        Toast.LENGTH_SHORT,
                    ).show()
                    onDismiss()
                },
            ) {
                Text(
                    stringResource(
                        if (fallback.copyKind == ExternalFallbackCopyKind.URL) {
                            R.string.external_action_copy_url
                        } else {
                            R.string.external_action_copy_value
                        },
                    ),
                    color = Primary,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close), color = OnSurfaceVariant)
            }
        },
        containerColor = SurfaceVariant,
        iconContentColor = Danger,
    )
}

private fun copyFallbackToClipboard(
    context: Context,
    fallback: ExternalActionFallback,
): Boolean = runCatching {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val label = context.getString(
        if (fallback.copyKind == ExternalFallbackCopyKind.URL) {
            R.string.external_action_copy_url
        } else {
            R.string.external_action_copy_value
        },
    )
    clipboard.setPrimaryClip(ClipData.newPlainText(label, fallback.copyValue))
}.isSuccess
