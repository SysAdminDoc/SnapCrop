package com.sysadmindoc.snapcrop

import android.graphics.Rect

internal data class ExpectedSensitiveRegion(
    val category: SensitiveTextCategory,
    val bounds: Rect
)

internal data class RedactionCategoryMetrics(
    val category: SensitiveTextCategory,
    val truePositive: Int,
    val falsePositive: Int,
    val falseNegative: Int,
    val precision: Double,
    val recall: Double,
    val minimumBoxCoverage: Double,
    val maximumAreaExpansion: Double
)

internal data class RedactionQualityReport(
    val categories: List<RedactionCategoryMetrics>,
    val macroPrecision: Double,
    val visibleSecretCount: Int
) {
    val passes: Boolean
        get() = visibleSecretCount == 0 &&
            macroPrecision >= RedactionQualityGate.MIN_MACRO_PRECISION &&
            categories.all {
                it.precision >= RedactionQualityGate.MIN_CATEGORY_PRECISION &&
                    it.recall >= RedactionQualityGate.MIN_RECALL &&
                    it.minimumBoxCoverage >= RedactionQualityGate.MIN_BOX_COVERAGE &&
                    it.maximumAreaExpansion <= RedactionQualityGate.MAX_AREA_EXPANSION
            }

    fun summary(): String = buildString {
        append("macroPrecision=").append("%.3f".format(macroPrecision))
        append(" visibleSecrets=").append(visibleSecretCount)
        categories.forEach { metric ->
            append("\n").append(metric.category)
            append(" precision=").append("%.3f".format(metric.precision))
            append(" recall=").append("%.3f".format(metric.recall))
            append(" coverage=").append("%.3f".format(metric.minimumBoxCoverage))
            append(" expansion=").append("%.3f".format(metric.maximumAreaExpansion))
            append(" tp/fp/fn=").append(metric.truePositive).append('/')
                .append(metric.falsePositive).append('/').append(metric.falseNegative)
        }
    }
}

internal object RedactionQualityGate {
    const val MIN_RECALL = 1.0
    const val MIN_CATEGORY_PRECISION = 0.90
    const val MIN_MACRO_PRECISION = 0.95
    const val MIN_BOX_COVERAGE = 0.99
    const val MAX_AREA_EXPANSION = 2.5

    fun evaluate(
        expected: List<ExpectedSensitiveRegion>,
        detected: List<SensitiveTextDetection>,
        visibleSecretCount: Int = 0
    ): RedactionQualityReport {
        val categories = expected.map { it.category }.toSet() + detected.map { it.category }.toSet()
        val metrics = categories.sortedBy(Enum<*>::name).map { category ->
            evaluateCategory(
                category,
                expected.filter { it.category == category }.map { it.bounds },
                detected.filter { it.category == category }.map { it.bounds }
            )
        }
        val macroPrecision = if (metrics.isEmpty()) 1.0 else metrics.map { it.precision }.average()
        return RedactionQualityReport(metrics, macroPrecision, visibleSecretCount)
    }

    private fun evaluateCategory(
        category: SensitiveTextCategory,
        expected: List<Rect>,
        detected: List<Rect>
    ): RedactionCategoryMetrics {
        val remainingExpected = expected.indices.toMutableSet()
        val remainingDetected = detected.indices.toMutableSet()
        val matchedCoverage = mutableListOf<Double>()
        val matchedExpansion = mutableListOf<Double>()

        while (remainingExpected.isNotEmpty() && remainingDetected.isNotEmpty()) {
            val best = remainingExpected.flatMap { expectedIndex ->
                remainingDetected.map { detectedIndex ->
                    Triple(
                        expectedIndex,
                        detectedIndex,
                        coverage(expected[expectedIndex], detected[detectedIndex])
                    )
                }
            }.maxByOrNull { it.third } ?: break
            if (best.third <= 0.0) break

            remainingExpected.remove(best.first)
            remainingDetected.remove(best.second)
            matchedCoverage.add(best.third)
            matchedExpansion.add(
                area(detected[best.second]).toDouble() /
                    area(expected[best.first]).coerceAtLeast(1L)
            )
        }

        val truePositive = matchedCoverage.count {
            it >= MIN_BOX_COVERAGE
        }
        val falseNegative = expected.size - truePositive
        val falsePositive = detected.size - truePositive
        val precision = ratio(truePositive, truePositive + falsePositive)
        val recall = ratio(truePositive, truePositive + falseNegative)
        return RedactionCategoryMetrics(
            category = category,
            truePositive = truePositive,
            falsePositive = falsePositive,
            falseNegative = falseNegative,
            precision = precision,
            recall = recall,
            minimumBoxCoverage = matchedCoverage.minOrNull() ?: if (expected.isEmpty()) 1.0 else 0.0,
            maximumAreaExpansion = matchedExpansion.maxOrNull() ?: if (expected.isEmpty()) 0.0 else Double.POSITIVE_INFINITY
        )
    }

    private fun ratio(numerator: Int, denominator: Int): Double =
        if (denominator == 0) 1.0 else numerator.toDouble() / denominator

    private fun coverage(expected: Rect, detected: Rect): Double {
        val intersection = Rect()
        if (!intersection.setIntersect(expected, detected)) return 0.0
        return area(intersection).toDouble() / area(expected).coerceAtLeast(1L)
    }

    private fun area(rect: Rect): Long =
        rect.width().coerceAtLeast(0).toLong() * rect.height().coerceAtLeast(0).toLong()
}
