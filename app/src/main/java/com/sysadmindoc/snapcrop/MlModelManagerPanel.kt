package com.sysadmindoc.snapcrop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.mlkit.nl.translate.TranslateLanguage
import com.sysadmindoc.snapcrop.ui.theme.OnSurface
import com.sysadmindoc.snapcrop.ui.theme.OnSurfaceVariant
import com.sysadmindoc.snapcrop.ui.theme.Outline
import com.sysadmindoc.snapcrop.ui.theme.Primary
import com.sysadmindoc.snapcrop.ui.theme.SurfaceContainer
import com.sysadmindoc.snapcrop.ui.theme.SurfaceVariant
import kotlinx.coroutines.launch

internal enum class ManagedModelState {
    BUNDLED,
    CHECKING,
    NOT_INSTALLED,
    DOWNLOADING,
    INSTALLED,
    REMOVING,
    FAILED,
}

internal data class ManagedModelStatus(
    val state: ManagedModelState,
    val progress: ModelDownloadProgress? = null,
    val error: String? = null,
)

internal object ManagedModelTransitions {
    fun initial(bundled: Boolean): ManagedModelStatus = ManagedModelStatus(
        if (bundled) ManagedModelState.BUNDLED else ManagedModelState.CHECKING
    )

    fun availability(installed: Boolean): ManagedModelStatus = ManagedModelStatus(
        if (installed) ManagedModelState.INSTALLED else ManagedModelState.NOT_INSTALLED
    )

    fun downloading(progress: ModelDownloadProgress? = null): ManagedModelStatus =
        ManagedModelStatus(ManagedModelState.DOWNLOADING, progress)

    fun failed(error: Throwable): ManagedModelStatus =
        ManagedModelStatus(ManagedModelState.FAILED, error = error.message)

    fun afterRelease(stillInstalled: Boolean): ManagedModelStatus = availability(stillInstalled)
}

@Composable
internal fun MlModelManagerPanel(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val optionalScripts = remember { OcrScript.entries.filterNot { it == OcrScript.LATIN } }
    val translationLanguages = remember { TextTranslator.targetLanguages.distinctBy(TranslationTarget::language) }
    var ocrStatus by remember {
        mutableStateOf(
            OcrScript.entries.associateWith {
                ManagedModelTransitions.initial(it == OcrScript.LATIN)
            }
        )
    }
    var translationStatus by remember {
        mutableStateOf(
            translationLanguages.associate { target ->
                target.language to ManagedModelTransitions.initial(
                    target.language == TranslateLanguage.ENGLISH
                )
            }
        )
    }
    var refreshKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(refreshKey) {
        optionalScripts.forEach { script ->
            val status = runCatching { OcrModelManager.isInstalled(context, script) }
                .fold(
                    onSuccess = {
                        ManagedModelTransitions.availability(it)
                    },
                    onFailure = ManagedModelTransitions::failed,
                )
            ocrStatus = ocrStatus + (script to status)
        }
        val installedTranslations = runCatching { TranslationModelManager.installedLanguages() }
        translationLanguages.forEach { target ->
            if (target.language == TranslateLanguage.ENGLISH) return@forEach
            translationStatus = translationStatus + (
                target.language to installedTranslations.fold(
                    onSuccess = {
                        ManagedModelTransitions.availability(target.language in it)
                    },
                    onFailure = ManagedModelTransitions::failed,
                )
            )
        }
    }

    Card(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceVariant),
        border = BorderStroke(1.dp, Outline.copy(alpha = 0.72f)),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.ml_models_title), color = OnSurface, style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.ml_models_summary), color = OnSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                OutlinedButton(
                    onClick = {
                        ocrStatus = ocrStatus.mapValues { (script, status) ->
                            if (script == OcrScript.LATIN || status.state == ManagedModelState.DOWNLOADING) status
                            else ManagedModelStatus(ManagedModelState.CHECKING)
                        }
                        translationStatus = translationStatus.mapValues { (language, status) ->
                            if (language == TranslateLanguage.ENGLISH || status.state == ManagedModelState.DOWNLOADING) status
                            else ManagedModelStatus(ManagedModelState.CHECKING)
                        }
                        refreshKey++
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.ml_models_refresh))
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.ml_models_ocr_title), color = OnSurface, style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.ml_models_ocr_summary), color = OnSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(6.dp))
            ModelRow(
                label = OcrScript.LATIN.label,
                detail = stringResource(R.string.ml_model_ocr_size),
                status = ocrStatus.getValue(OcrScript.LATIN),
            )
            optionalScripts.forEach { script ->
                HorizontalDivider(color = Outline.copy(alpha = 0.4f))
                ModelRow(
                    label = script.label,
                    detail = stringResource(R.string.ml_model_ocr_size),
                    status = ocrStatus.getValue(script),
                    onDownload = {
                        ocrStatus = ocrStatus + (script to ManagedModelTransitions.downloading())
                        scope.launch {
                            runCatching {
                                OcrModelManager.install(context, script) { progress ->
                                    ocrStatus = ocrStatus + (
                                        script to ManagedModelTransitions.downloading(progress)
                                    )
                                }
                            }.onSuccess {
                                ocrStatus = ocrStatus + (script to ManagedModelStatus(ManagedModelState.INSTALLED))
                            }.onFailure { error ->
                                ocrStatus = ocrStatus + (
                                    script to ManagedModelTransitions.failed(error)
                                )
                            }
                        }
                    },
                    onRemove = {
                        ocrStatus = ocrStatus + (script to ManagedModelStatus(ManagedModelState.REMOVING))
                        scope.launch {
                            runCatching { OcrModelManager.release(context, script) }
                                .onSuccess { refreshKey++ }
                                .onFailure { error ->
                                    ocrStatus = ocrStatus + (
                                        script to ManagedModelTransitions.failed(error)
                                    )
                                }
                        }
                    },
                )
            }
            Text(
                stringResource(R.string.ml_model_shared_storage),
                color = OnSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 6.dp),
            )

            Spacer(Modifier.height(14.dp))
            Text(stringResource(R.string.ml_models_translation_title), color = OnSurface, style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.ml_models_translation_summary), color = OnSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(6.dp))
            translationLanguages.forEachIndexed { index, target ->
                if (index > 0) HorizontalDivider(color = Outline.copy(alpha = 0.4f))
                ModelRow(
                    label = target.label,
                    detail = if (target.language == TranslateLanguage.ENGLISH) ""
                    else stringResource(R.string.ml_model_translation_size),
                    status = translationStatus.getValue(target.language),
                    onDownload = {
                        translationStatus = translationStatus + (
                            target.language to ManagedModelTransitions.downloading()
                        )
                        scope.launch {
                            runCatching { TranslationModelManager.download(target.language) }
                                .onSuccess {
                                    translationStatus = translationStatus + (
                                        target.language to ManagedModelStatus(ManagedModelState.INSTALLED)
                                    )
                                }
                                .onFailure { error ->
                                    translationStatus = translationStatus + (
                                        target.language to ManagedModelTransitions.failed(error)
                                    )
                                }
                        }
                    },
                    onRemove = {
                        translationStatus = translationStatus + (
                            target.language to ManagedModelStatus(ManagedModelState.REMOVING)
                        )
                        scope.launch {
                            runCatching { TranslationModelManager.delete(target.language) }
                                .onSuccess {
                                    translationStatus = translationStatus + (
                                        target.language to ManagedModelStatus(ManagedModelState.NOT_INSTALLED)
                                    )
                                }
                                .onFailure { error ->
                                    translationStatus = translationStatus + (
                                        target.language to ManagedModelTransitions.failed(error)
                                    )
                                }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun ModelRow(
    label: String,
    detail: String,
    status: ManagedModelStatus,
    onDownload: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(label, color = OnSurface, style = MaterialTheme.typography.titleSmall)
                Text(modelStatusLabel(status), color = OnSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                if (detail.isNotBlank()) {
                    Text(detail, color = OnSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                }
                status.error?.takeIf(String::isNotBlank)?.let { error ->
                    Text(
                        stringResource(R.string.ml_model_action_failed, error),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            when (status.state) {
                ManagedModelState.NOT_INSTALLED -> onDownload?.let {
                    OutlinedButton(onClick = it, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.ml_model_download))
                    }
                }
                ManagedModelState.FAILED -> onDownload?.let {
                    OutlinedButton(onClick = it, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.ml_model_retry))
                    }
                }
                ManagedModelState.INSTALLED -> onRemove?.let {
                    OutlinedButton(onClick = it, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.ml_model_remove))
                    }
                }
                else -> Unit
            }
        }
        if (status.state == ManagedModelState.DOWNLOADING) {
            val progress = status.progress
            if (progress != null && progress.totalBytes > 0) {
                LinearProgressIndicator(
                    progress = { (progress.downloadedBytes.toFloat() / progress.totalBytes).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    color = Primary,
                    trackColor = SurfaceContainer,
                )
            } else {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    color = Primary,
                    trackColor = SurfaceContainer,
                )
            }
        }
    }
}

@Composable
private fun modelStatusLabel(status: ManagedModelStatus): String = when (status.state) {
    ManagedModelState.BUNDLED -> stringResource(R.string.ml_model_bundled)
    ManagedModelState.CHECKING -> stringResource(R.string.ml_model_checking)
    ManagedModelState.NOT_INSTALLED -> stringResource(R.string.ml_model_not_installed)
    ManagedModelState.DOWNLOADING -> status.progress?.takeIf { it.totalBytes > 0 }?.let {
        stringResource(
            R.string.ml_model_downloading_progress,
            ((it.downloadedBytes * 100L) / it.totalBytes).coerceIn(0L, 100L).toInt(),
        )
    } ?: stringResource(R.string.ml_model_downloading)
    ManagedModelState.INSTALLED -> stringResource(R.string.ml_model_installed)
    ManagedModelState.REMOVING -> stringResource(R.string.ml_model_removing)
    ManagedModelState.FAILED -> stringResource(R.string.ml_model_failed)
}
