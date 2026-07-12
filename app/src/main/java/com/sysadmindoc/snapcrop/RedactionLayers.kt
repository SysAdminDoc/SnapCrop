package com.sysadmindoc.snapcrop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.sysadmindoc.snapcrop.ui.theme.OnSurface
import com.sysadmindoc.snapcrop.ui.theme.OnSurfaceVariant
import com.sysadmindoc.snapcrop.ui.theme.Primary
import com.sysadmindoc.snapcrop.ui.theme.PrimaryContainer
import com.sysadmindoc.snapcrop.ui.theme.Secondary
import com.sysadmindoc.snapcrop.ui.theme.SurfaceVariant
import com.sysadmindoc.snapcrop.ui.theme.Tertiary

@Composable
internal fun RedactionLayerPanel(
    regions: List<RedactionRegion>,
    selectedIndex: Int = -1,
    onSelectRegion: (index: Int) -> Unit,
    onToggleRegion: (index: Int) -> Unit,
    onDeleteRegion: (index: Int) -> Unit,
    onStyleRegion: (index: Int, style: RedactionStyle) -> Unit,
    onMoveRegion: (index: Int, dx: Int, dy: Int) -> Unit,
    onResizeRegion: (index: Int, deltaWidth: Int, deltaHeight: Int) -> Unit,
    onToggleCategory: (category: RedactionCategory, enabled: Boolean) -> Unit
) {
    val categories = regions.flatMap(RedactionRegion::categories).distinct()

    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        color = SurfaceVariant.copy(alpha = 0.36f),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.redactions_title), color = OnSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text(stringResource(R.string.redactions_subtitle), color = OnSurfaceVariant, fontSize = 10.sp)
                }
                Text(stringResource(R.string.redactions_enabled_count, regions.count(RedactionRegion::enabled), regions.size), color = Secondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }

            if (categories.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categories.forEach { category ->
                        val members = regions.filter { category in it.categories }
                        val enabledCount = members.count(RedactionRegion::enabled)
                        val anyEnabled = enabledCount > 0
                        val label = redactionCategoryLabel(category)
                        FilterChip(
                            selected = anyEnabled,
                            onClick = { onToggleCategory(category, !anyEnabled) },
                            label = { Text(stringResource(R.string.redactions_category_count, label, enabledCount, members.size), fontSize = 10.sp) },
                            modifier = Modifier.semantics {
                                contentDescription = "$label category, $enabledCount of ${members.size} enabled"
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PrimaryContainer,
                                selectedLabelColor = Primary,
                                containerColor = SurfaceVariant,
                                labelColor = OnSurfaceVariant
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }
            }

            if (regions.isEmpty()) {
                Text(stringResource(R.string.redactions_empty), color = OnSurfaceVariant, fontSize = 11.sp, modifier = Modifier.padding(vertical = 4.dp))
            } else {
                regions.asReversed().forEachIndexed { visualIndex, region ->
                    val actualIndex = regions.lastIndex - visualIndex
                    val categoryNames = mutableListOf<String>()
                    for (category in region.categories) categoryNames.add(redactionCategoryLabel(category))
                    val categoryLabel = categoryNames.joinToString(", ")
                    val styleLabel = redactionStyleLabel(region.style)
                    val stateLabel = stringResource(if (region.enabled) R.string.redactions_enabled else R.string.redactions_disabled)
                    val isSelected = actualIndex == selectedIndex

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectRegion(actualIndex) }
                            .semantics {
                                contentDescription = "$categoryLabel redaction, $stateLabel, $styleLabel"
                            }
                            .background(
                                when {
                                    isSelected -> Secondary.copy(alpha = 0.22f)
                                    region.enabled -> OnSurface.copy(alpha = 0.10f)
                                    else -> OnSurface.copy(alpha = 0.05f)
                                },
                                RoundedCornerShape(8.dp)
                            )
                            .padding(start = 8.dp, end = 2.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(18.dp)
                                .background(redactionStyleColor(region.style), RoundedCornerShape(4.dp))
                                .border(1.dp, OnSurfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(4.dp))
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                categoryLabel,
                                color = if (region.enabled) OnSurface else OnSurfaceVariant,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                stringResource(R.string.redactions_region_subtitle, actualIndex + 1, styleLabel, stateLabel),
                                color = OnSurfaceVariant,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(
                            onClick = { onToggleRegion(actualIndex) },
                            modifier = Modifier.size(36.dp).semantics {
                                contentDescription = if (region.enabled) "Disable $categoryLabel redaction" else "Enable $categoryLabel redaction"
                            }
                        ) {
                            Icon(
                                if (region.enabled) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                null,
                                tint = if (region.enabled) Secondary else OnSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        IconButton(
                            onClick = { onDeleteRegion(actualIndex) },
                            modifier = Modifier.size(36.dp).semantics {
                                contentDescription = "Delete $categoryLabel redaction"
                            }
                        ) {
                            Icon(Icons.Default.Delete, null, tint = Tertiary, modifier = Modifier.size(16.dp))
                        }
                    }

                    if (isSelected) {
                        Column(
                            Modifier.fillMaxWidth().padding(start = 8.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Row(
                                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(RedactionStyle.SOLID, RedactionStyle.PIXELATE, RedactionStyle.BLUR).forEach { style ->
                                    val label = redactionStyleLabel(style)
                                    FilterChip(
                                        selected = region.style == style,
                                        onClick = { onStyleRegion(actualIndex, style) },
                                        label = { Text(label, fontSize = 10.sp) },
                                        modifier = Modifier.semantics {
                                            contentDescription = "Use $label for $categoryLabel redaction"
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = PrimaryContainer,
                                            selectedLabelColor = Primary,
                                            containerColor = SurfaceVariant,
                                            labelColor = OnSurfaceVariant
                                        ),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                                RedactionNudgeButton("←", "Move redaction left") { onMoveRegion(actualIndex, -1, 0) }
                                RedactionNudgeButton("→", "Move redaction right") { onMoveRegion(actualIndex, 1, 0) }
                                RedactionNudgeButton("↑", "Move redaction up") { onMoveRegion(actualIndex, 0, -1) }
                                RedactionNudgeButton("↓", "Move redaction down") { onMoveRegion(actualIndex, 0, 1) }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                                RedactionNudgeButton("W−", "Make redaction narrower") { onResizeRegion(actualIndex, -1, 0) }
                                RedactionNudgeButton("W+", "Make redaction wider") { onResizeRegion(actualIndex, 1, 0) }
                                RedactionNudgeButton("H−", "Make redaction shorter") { onResizeRegion(actualIndex, 0, -1) }
                                RedactionNudgeButton("H+", "Make redaction taller") { onResizeRegion(actualIndex, 0, 1) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RedactionNudgeButton(label: String, description: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
        modifier = Modifier.semantics { contentDescription = description }
    ) {
        Text(label, color = Secondary, fontSize = 13.sp)
    }
}

@Composable
private fun redactionCategoryLabel(category: RedactionCategory): String = stringResource(when (category) {
    RedactionCategory.EMAIL -> R.string.redaction_category_email
    RedactionCategory.PHONE -> R.string.redaction_category_phone
    RedactionCategory.PAYMENT_CARD -> R.string.redaction_category_payment_card
    RedactionCategory.IPV4, RedactionCategory.IPV6 -> R.string.redaction_category_ip
    RedactionCategory.MAC_ADDRESS -> R.string.redaction_category_mac
    RedactionCategory.IBAN -> R.string.redaction_category_iban
    RedactionCategory.POSTAL_ADDRESS -> R.string.redaction_category_address
    RedactionCategory.DEVELOPER_SECRET -> R.string.redaction_category_developer_secret
    RedactionCategory.CUSTOM -> R.string.redaction_category_custom
    RedactionCategory.FACE -> R.string.redaction_category_face
    RedactionCategory.MANUAL -> R.string.redaction_category_manual
})

@Composable
private fun redactionStyleLabel(style: RedactionStyle): String = stringResource(when (style) {
    RedactionStyle.SOLID -> R.string.redaction_style_bar_short
    RedactionStyle.PIXELATE -> R.string.redaction_style_pixelate_short
    RedactionStyle.BLUR -> R.string.redaction_style_blur_short
})

private fun redactionStyleColor(style: RedactionStyle): Color = when (style) {
    RedactionStyle.SOLID -> Color.Black
    RedactionStyle.PIXELATE -> Tertiary
    RedactionStyle.BLUR -> Secondary
}
