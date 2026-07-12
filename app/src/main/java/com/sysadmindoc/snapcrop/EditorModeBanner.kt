package com.sysadmindoc.snapcrop

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sysadmindoc.snapcrop.ui.theme.AdjustAccent
import com.sysadmindoc.snapcrop.ui.theme.OcrAccent
import com.sysadmindoc.snapcrop.ui.theme.Primary
import com.sysadmindoc.snapcrop.ui.theme.Secondary
import com.sysadmindoc.snapcrop.ui.theme.Tertiary

internal data class EditorModeBannerActions(
    val onReviewOcr: () -> Unit,
    val onCopyOcr: () -> Unit,
    val onTranslateOcr: () -> Unit,
)

@Composable
internal fun EditorModeBanner(
    editMode: EditMode,
    drawTool: DrawTool,
    ocrLoading: Boolean,
    ocrBlockCount: Int,
    scannedCodeCount: Int,
    actions: EditorModeBannerActions,
) {
    if (editMode == EditMode.CROP) return
    val pixelateLabel = stringResource(R.string.mode_pixelate).uppercase()
    val drawLabel = stringResource(R.string.mode_draw).uppercase()
    val ocrLabel = stringResource(R.string.mode_ocr).uppercase()
    val adjustLabel = stringResource(R.string.mode_adjust).uppercase()
    val (bannerBg, bannerColor, bannerText) = when (editMode) {
        EditMode.CUTOUT -> Triple(Primary.copy(alpha = 0.15f), Primary, stringResource(R.string.cutout_mode_banner))
        EditMode.PIXELATE -> Triple(Tertiary.copy(alpha = 0.15f), Tertiary, stringResource(R.string.editor_banner_pixelate, pixelateLabel))
        EditMode.DRAW -> Triple(Secondary.copy(alpha = 0.15f), Secondary, stringResource(R.string.editor_banner_draw, drawLabel, drawTool.label.lowercase()))
        EditMode.OCR -> {
            val info = if (ocrLoading) stringResource(R.string.ocr_scan).uppercase() + "..." else buildString {
                append(stringResource(R.string.editor_banner_ocr, ocrLabel))
                if (ocrBlockCount > 0 || scannedCodeCount > 0) {
                    append(" · ")
                    append(stringResource(R.string.editor_banner_ocr_counts, ocrBlockCount, scannedCodeCount))
                }
            }
            Triple(OcrAccent.copy(alpha = 0.15f), OcrAccent, info)
        }
        EditMode.ADJUST -> Triple(AdjustAccent.copy(alpha = 0.15f), AdjustAccent, stringResource(R.string.editor_banner_adjust, adjustLabel))
        EditMode.CROP -> Triple(Color.Transparent, Color.Transparent, "")
    }
    Column(Modifier.fillMaxWidth().background(bannerBg)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (ocrLoading && editMode == EditMode.OCR) {
                CircularProgressIndicator(Modifier.size(12.dp).padding(end = 4.dp), strokeWidth = 1.5.dp, color = bannerColor)
                Spacer(Modifier.width(6.dp))
            }
            Text(
                bannerText,
                color = bannerColor,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
        if (editMode == EditMode.OCR && ocrBlockCount > 0) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = actions.onReviewOcr, modifier = Modifier.heightIn(min = 40.dp), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                    Text(stringResource(R.string.ocr_review), color = bannerColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = actions.onCopyOcr, modifier = Modifier.heightIn(min = 40.dp), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                    Text(stringResource(R.string.ocr_copy_all), color = bannerColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = actions.onTranslateOcr, modifier = Modifier.heightIn(min = 40.dp), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                    Icon(Icons.Default.Translate, null, tint = bannerColor, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(3.dp))
                    Text(stringResource(R.string.ocr_translate), color = bannerColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
