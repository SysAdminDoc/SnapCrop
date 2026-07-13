package com.sysadmindoc.snapcrop

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.ceil

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenshotSimilarityBenchmarkTest {
    @Test
    fun deterministicCorpusReportsQualityCostAndProductionDecision() {
        val rows = mutableListOf<BenchmarkRow>()
        val timings = SimilarityMetric.entries.associateWith { mutableListOf<Long>() }
        ScreenshotSimilarityCorpus.forEachPair { pair ->
            val firstDHash = ScreenshotSimilarityMetrics.dHash(pair.before)
            val secondDHash = ScreenshotSimilarityMetrics.dHash(pair.after)
            val firstPHash = ScreenshotSimilarityMetrics.pHash(pair.before)
            val secondPHash = ScreenshotSimilarityMetrics.pHash(pair.after)
            val ssim = ScreenshotSimilarityMetrics.ssim(pair.before, pair.after)
            rows += BenchmarkRow(
                pair = pair,
                production = DuplicateSensitivity.entries.associateWith {
                    ScreenshotSimilarityMetrics.productionMatch(firstDHash, secondDHash, it)
                },
                dHashDistance = ScreenshotSimilarityMetrics.dHashDistance(firstDHash, secondDHash),
                pHashDistance = ScreenshotSimilarityMetrics.pHashDistance(firstPHash, secondPHash),
                ssim = ssim,
            )
            if (pair.seed < TIMING_SEEDS) measurePair(pair, timings)
            if (pair.seed == 0 && pair.category == SimilarityCorpusCategory.JPEG_RECOMPRESS) {
                assertEquals(firstPHash, ScreenshotSimilarityMetrics.pHash(pair.before))
                assertEquals(ssim, ScreenshotSimilarityMetrics.ssim(pair.before, pair.after), 0.0)
            }
        }

        assertEquals(ScreenshotSimilarityCorpus.PAIR_COUNT, rows.size)
        assertEquals(SimilarityCorpusCategory.entries.toSet(), rows.map { it.pair.category }.toSet())
        SimilarityCorpusCategory.entries.forEach { category ->
            assertEquals(ScreenshotSimilarityCorpus.SEED_COUNT, rows.count { it.pair.category == category })
        }
        assertEquals(48, rows.count { it.pair.label == SimilarityCorpusLabel.POSITIVE })
        assertEquals(36, rows.count { it.pair.label == SimilarityCorpusLabel.NEGATIVE })
        assertEquals(12, rows.count { it.pair.label == SimilarityCorpusLabel.DIAGNOSTIC })
        assertTrue(rows.all { it.dHashDistance in 0..64 && it.pHashDistance in 0..64 && it.ssim.isFinite() })
        assertTrue(SimilarityMetric.entries.all { it.estimatedPairScratchBytes <= MAX_PAIR_SCRATCH_BYTES })

        val production = DuplicateSensitivity.entries.associateWith { sensitivity ->
            quality(rows, calibration = true) { it.production.getValue(sensitivity) }
        }
        val selectedProduction = production.entries
            .filter { it.value.precision >= PRECISION_FLOOR && it.value.falsePositiveCategories.isEmpty() }
            .maxWithOrNull(compareBy<Map.Entry<DuplicateSensitivity, Quality>> { it.value.recall }.thenBy { it.value.f1 })
            ?: production.maxBy { it.value.f1 }
        val productionValidation = quality(rows, calibration = false) {
            it.production.getValue(selectedProduction.key)
        }

        val rawDHash = selectDistance("raw_dhash", rows, 0..16, BenchmarkRow::dHashDistance)
        val pHash = selectDistance("phash", rows, 0..32, BenchmarkRow::pHashDistance)
        val boundedSsim = selectSsim(rows)
        val candidates = listOf(
            gate(rawDHash, productionValidation, timings.getValue(SimilarityMetric.RAW_DHASH)),
            gate(pHash, productionValidation, timings.getValue(SimilarityMetric.PHASH)),
            gate(boundedSsim, productionValidation, timings.getValue(SimilarityMetric.SSIM)),
        )
        val recommendation = when (candidates.filter(ProductionGate::eligible).maxByOrNull { it.selection.validation.recall }?.selection?.name) {
            "raw_dhash" -> "adjust_production_dimension_or_luma_gates"
            "phash" -> "evaluate_phash_production_migration"
            "ssim" -> "evaluate_ssim_after_indexed_candidate_generation"
            else -> "retain_current_production"
        }

        val report = report(rows, timings, production, selectedProduction.key, productionValidation, candidates, recommendation)
        val reportFile = reportFile()
        checkNotNull(reportFile.parentFile).mkdirs()
        reportFile.writeText(report.toString(2) + "\n")
        assertTrue(reportFile.isFile && reportFile.length() > 0)
        assertEquals(ScreenshotSimilarityCorpus.PAIR_COUNT, JSONObject(reportFile.readText()).getInt("pairCount"))
        println("Screenshot similarity benchmark: ${reportFile.absolutePath}\nRecommendation: $recommendation")
    }

    private fun measurePair(pair: SimilarityCorpusPair, timings: Map<SimilarityMetric, MutableList<Long>>) {
        repeat(MEASURED_REPEATS) {
            timings.getValue(SimilarityMetric.PRODUCTION_DHASH) += elapsed {
                val first = ScreenshotSimilarityMetrics.dHash(pair.before)
                val second = ScreenshotSimilarityMetrics.dHash(pair.after)
                consume(ScreenshotSimilarityMetrics.productionMatch(first, second, DuplicateSensitivity.BALANCED))
            }
            timings.getValue(SimilarityMetric.RAW_DHASH) += elapsed {
                consume(ScreenshotSimilarityMetrics.dHashDistance(
                    ScreenshotSimilarityMetrics.dHash(pair.before),
                    ScreenshotSimilarityMetrics.dHash(pair.after),
                ))
            }
            timings.getValue(SimilarityMetric.PHASH) += elapsed {
                consume(ScreenshotSimilarityMetrics.pHashDistance(
                    ScreenshotSimilarityMetrics.pHash(pair.before),
                    ScreenshotSimilarityMetrics.pHash(pair.after),
                ))
            }
            timings.getValue(SimilarityMetric.SSIM) += elapsed {
                consume(ScreenshotSimilarityMetrics.ssim(pair.before, pair.after))
            }
        }
    }

    private fun elapsed(block: () -> Unit): Long {
        val start = System.nanoTime()
        block()
        return System.nanoTime() - start
    }

    private fun selectDistance(
        name: String,
        rows: List<BenchmarkRow>,
        thresholds: IntRange,
        score: (BenchmarkRow) -> Int,
    ): ThresholdSelection = select(name, rows, thresholds.map(Int::toDouble), lowerMatches = true) { score(it).toDouble() }

    private fun selectSsim(rows: List<BenchmarkRow>): ThresholdSelection = select(
        "ssim",
        rows,
        (750..1000 step 5).map { it / 1000.0 },
        lowerMatches = false,
        BenchmarkRow::ssim,
    )

    private fun select(
        name: String,
        rows: List<BenchmarkRow>,
        thresholds: List<Double>,
        lowerMatches: Boolean,
        score: (BenchmarkRow) -> Double,
    ): ThresholdSelection {
        val results = thresholds.map { threshold ->
            threshold to quality(rows, calibration = true) {
                if (lowerMatches) score(it) <= threshold else score(it) >= threshold
            }
        }
        val selected = results
            .filter { it.second.precision >= PRECISION_FLOOR && it.second.falsePositiveCategories.isEmpty() }
            .maxWithOrNull(compareBy<Pair<Double, Quality>> { it.second.recall }.thenBy { it.second.f1 })
            ?: results.maxBy { it.second.f1 }
        return ThresholdSelection(
            name,
            selected.first,
            selected.second,
            quality(rows, calibration = false) {
                if (lowerMatches) score(it) <= selected.first else score(it) >= selected.first
            },
        )
    }

    private fun quality(
        rows: List<BenchmarkRow>,
        calibration: Boolean,
        predictsMatch: (BenchmarkRow) -> Boolean,
    ): Quality {
        var tp = 0
        var fp = 0
        var tn = 0
        var fn = 0
        val falsePositiveCategories = linkedSetOf<String>()
        rows.filter { it.pair.calibration == calibration && it.pair.label != SimilarityCorpusLabel.DIAGNOSTIC }
            .forEach { row ->
                val expected = row.pair.label == SimilarityCorpusLabel.POSITIVE
                val predicted = predictsMatch(row)
                when {
                    expected && predicted -> tp++
                    expected -> fn++
                    predicted -> {
                        fp++
                        falsePositiveCategories += row.pair.category.name.lowercase()
                    }
                    else -> tn++
                }
            }
        return Quality(tp, fp, tn, fn, falsePositiveCategories)
    }

    private fun gate(selection: ThresholdSelection, baseline: Quality, nanos: List<Long>): ProductionGate {
        val timing = Timing(nanos.percentile(0.5), nanos.percentile(0.95), nanos.size)
        val metric = when (selection.name) {
            "raw_dhash" -> SimilarityMetric.RAW_DHASH
            "phash" -> SimilarityMetric.PHASH
            else -> SimilarityMetric.SSIM
        }
        val validation = selection.validation
        return ProductionGate(
            selection = selection,
            timing = timing,
            scratchBytes = metric.estimatedPairScratchBytes,
            eligible = validation.precision >= maxOf(PRECISION_FLOOR, baseline.precision) &&
                validation.recall >= baseline.recall + MIN_RECALL_GAIN &&
                validation.falsePositiveCategories.isEmpty() &&
                metric.estimatedPairScratchBytes <= MAX_PAIR_SCRATCH_BYTES &&
                timing.p95Nanos <= MAX_P95_NANOS,
        )
    }

    private fun report(
        rows: List<BenchmarkRow>,
        timings: Map<SimilarityMetric, MutableList<Long>>,
        production: Map<DuplicateSensitivity, Quality>,
        selectedProduction: DuplicateSensitivity,
        productionValidation: Quality,
        candidates: List<ProductionGate>,
        recommendation: String,
    ) = JSONObject().apply {
        put("schemaVersion", 1)
        put("pairCount", rows.size)
        put("calibrationSeeds", ScreenshotSimilarityCorpus.CALIBRATION_SEEDS)
        put("validationSeeds", ScreenshotSimilarityCorpus.SEED_COUNT - ScreenshotSimilarityCorpus.CALIBRATION_SEEDS)
        put("categories", JSONArray(SimilarityCorpusCategory.entries.map { it.name.lowercase() }))
        put("productionChangeGate", JSONObject().apply {
            put("precisionFloor", PRECISION_FLOOR)
            put("minimumAbsoluteRecallGain", MIN_RECALL_GAIN)
            put("maximumPairScratchBytes", MAX_PAIR_SCRATCH_BYTES)
            put("maximumP95Nanos", MAX_P95_NANOS)
            put("requiresZeroHardNegativeCategories", true)
        })
        put("production", JSONObject().apply {
            DuplicateSensitivity.entries.forEach { put(it.name.lowercase(), production.getValue(it).json()) }
            put("selectedOnCalibration", selectedProduction.name.lowercase())
            put("validation", productionValidation.json())
        })
        put("timing", JSONObject().apply {
            SimilarityMetric.entries.forEach { metric ->
                val values = timings.getValue(metric)
                put(metric.name.lowercase(), JSONObject().apply {
                    put("medianNanos", values.percentile(0.5))
                    put("p95Nanos", values.percentile(0.95))
                    put("samples", values.size)
                    put("estimatedPairScratchBytes", metric.estimatedPairScratchBytes)
                })
            }
        })
        put("candidates", JSONArray(candidates.map { it.json() }))
        put("categoryScores", JSONObject().apply {
            SimilarityCorpusCategory.entries.forEach { category ->
                val categoryRows = rows.filter { it.pair.category == category }
                put(category.name.lowercase(), JSONObject().apply {
                    put("label", category.label.name.lowercase())
                    put("pairs", categoryRows.size)
                    put("meanDHashDistance", categoryRows.map(BenchmarkRow::dHashDistance).average())
                    put("meanPHashDistance", categoryRows.map(BenchmarkRow::pHashDistance).average())
                    put("meanSsim", categoryRows.map(BenchmarkRow::ssim).average())
                    put("productionMatchRate", JSONObject().apply {
                        DuplicateSensitivity.entries.forEach { sensitivity ->
                            put(
                                sensitivity.name.lowercase(),
                                categoryRows.count { it.production.getValue(sensitivity) }.toDouble() / categoryRows.size,
                            )
                        }
                    })
                })
            }
        })
        put("themeDiagnostics", JSONArray(rows.filter { it.pair.label == SimilarityCorpusLabel.DIAGNOSTIC }.map {
            JSONObject().put("id", it.pair.id).put("dHashDistance", it.dHashDistance)
                .put("pHashDistance", it.pHashDistance).put("ssim", it.ssim)
        }))
        put("recommendation", recommendation)
    }

    private fun List<Long>.percentile(fraction: Double): Long {
        if (isEmpty()) return 0
        val sorted = sorted()
        return sorted[(ceil(sorted.size * fraction).toInt() - 1).coerceIn(sorted.indices)]
    }

    private fun Quality.json() = JSONObject()
        .put("truePositive", truePositive).put("falsePositive", falsePositive)
        .put("trueNegative", trueNegative).put("falseNegative", falseNegative)
        .put("precision", precision).put("recall", recall).put("f1", f1)
        .put("falsePositiveCategories", JSONArray(falsePositiveCategories.toList()))

    private fun ProductionGate.json() = JSONObject()
        .put("metric", selection.name).put("threshold", selection.threshold)
        .put("calibration", selection.calibration.json()).put("validation", selection.validation.json())
        .put("medianNanos", timing.medianNanos).put("p95Nanos", timing.p95Nanos)
        .put("timingSamples", timing.samples).put("estimatedPairScratchBytes", scratchBytes)
        .put("eligibleForProductionChange", eligible)

    private fun reportFile(): File {
        val working = File(checkNotNull(System.getProperty("user.dir")))
        val app = if (working.name == "app") working else working.resolve("app")
        return app.resolve("build/reports/screenshot-similarity/benchmark.json")
    }

    private fun consume(value: Any) {
        sink = sink xor value.hashCode().toLong()
    }

    private data class BenchmarkRow(
        val pair: SimilarityCorpusPair,
        val production: Map<DuplicateSensitivity, Boolean>,
        val dHashDistance: Int,
        val pHashDistance: Int,
        val ssim: Double,
    )

    private data class Quality(
        val truePositive: Int,
        val falsePositive: Int,
        val trueNegative: Int,
        val falseNegative: Int,
        val falsePositiveCategories: Set<String>,
    ) {
        val precision: Double get() = if (truePositive + falsePositive == 0) 1.0 else truePositive.toDouble() / (truePositive + falsePositive)
        val recall: Double get() = if (truePositive + falseNegative == 0) 0.0 else truePositive.toDouble() / (truePositive + falseNegative)
        val f1: Double get() = if (precision + recall == 0.0) 0.0 else 2 * precision * recall / (precision + recall)
    }

    private data class ThresholdSelection(
        val name: String,
        val threshold: Double,
        val calibration: Quality,
        val validation: Quality,
    )

    private data class Timing(val medianNanos: Long, val p95Nanos: Long, val samples: Int)
    private data class ProductionGate(
        val selection: ThresholdSelection,
        val timing: Timing,
        val scratchBytes: Int,
        val eligible: Boolean,
    )

    companion object {
        private const val TIMING_SEEDS = 2
        private const val MEASURED_REPEATS = 3
        private const val PRECISION_FLOOR = 0.98
        private const val MIN_RECALL_GAIN = 0.10
        private const val MAX_PAIR_SCRATCH_BYTES = 512 * 1024
        private const val MAX_P95_NANOS = 25_000_000L
        private var sink = 0L
    }
}
