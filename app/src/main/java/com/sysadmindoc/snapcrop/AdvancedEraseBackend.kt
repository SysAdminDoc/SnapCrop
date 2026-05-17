package com.sysadmindoc.snapcrop

import android.content.Context
import android.content.SharedPreferences

enum class EraseBackendId(val prefValue: String, val label: String) {
    LOCAL_SMART_ERASE("local_smart_erase", "Local Smart Erase"),
    RESEARCH_MODEL_PACK("research_model_pack", "Downloaded model pack")
}

data class AdvancedEraseCandidate(
    val id: EraseBackendId,
    val runtime: String,
    val license: String,
    val estimatedSizeMb: Int,
    val measuredMedianLatencyMs: Int?,
    val bundledByDefault: Boolean,
    val qualityLiftPercent: Int?,
    val notes: String
)

object AdvancedEraseBackendRegistry {
    const val PREF_ALLOW_EXPERIMENTAL = "advanced_erase_allow_experimental"
    const val PREF_SELECTED_BACKEND = "advanced_erase_selected_backend"

    val localSmartErase = AdvancedEraseCandidate(
        id = EraseBackendId.LOCAL_SMART_ERASE,
        runtime = "Kotlin bitmap mask fill",
        license = "Project license",
        estimatedSizeMb = 0,
        measuredMedianLatencyMs = 0,
        bundledByDefault = true,
        qualityLiftPercent = 0,
        notes = "Default local heuristic; no model download or network dependency."
    )

    val researchModelPack = AdvancedEraseCandidate(
        id = EraseBackendId.RESEARCH_MODEL_PACK,
        runtime = "ONNX Runtime Android / LaMa-style inpainting candidate",
        license = "Unverified for bundled mobile redistribution",
        estimatedSizeMb = 0,
        measuredMedianLatencyMs = null,
        bundledByDefault = false,
        qualityLiftPercent = null,
        notes = "Research placeholder only. Requires an external model-pack manifest and private screenshot mask benchmark before activation."
    )

    val candidates: List<AdvancedEraseCandidate> = listOf(localSmartErase, researchModelPack)

    fun selectedBackend(prefs: SharedPreferences): EraseBackendId {
        val requested = prefs.getString(PREF_SELECTED_BACKEND, EraseBackendId.LOCAL_SMART_ERASE.prefValue)
        val parsed = EraseBackendId.entries.firstOrNull { it.prefValue == requested } ?: EraseBackendId.LOCAL_SMART_ERASE
        if (parsed == EraseBackendId.LOCAL_SMART_ERASE) return parsed
        val allowExperimental = prefs.getBoolean(PREF_ALLOW_EXPERIMENTAL, false)
        val candidate = candidates.firstOrNull { it.id == parsed } ?: return EraseBackendId.LOCAL_SMART_ERASE
        return if (allowExperimental && isCandidateActivatable(candidate)) parsed else EraseBackendId.LOCAL_SMART_ERASE
    }

    fun selectedBackend(context: Context): EraseBackendId =
        selectedBackend(context.getSharedPreferences("snapcrop", Context.MODE_PRIVATE))

    fun isCandidateActivatable(candidate: AdvancedEraseCandidate): Boolean {
        if (candidate.id == EraseBackendId.LOCAL_SMART_ERASE) return true
        val latency = candidate.measuredMedianLatencyMs ?: return false
        val qualityLift = candidate.qualityLiftPercent ?: return false
        val licenseLooksResolved = !candidate.license.contains("unverified", ignoreCase = true)
        return !candidate.bundledByDefault &&
                licenseLooksResolved &&
                candidate.estimatedSizeMb in 1..120 &&
                latency <= 2_500 &&
                qualityLift >= 10
    }

    fun statusSummary(prefs: SharedPreferences): String {
        val selected = selectedBackend(prefs)
        return if (selected == EraseBackendId.LOCAL_SMART_ERASE) {
            "Using Local Smart Erase. Advanced model packs are not active."
        } else {
            "Using downloaded erase model pack."
        }
    }
}
