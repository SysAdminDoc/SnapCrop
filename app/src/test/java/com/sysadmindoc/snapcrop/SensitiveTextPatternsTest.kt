package com.sysadmindoc.snapcrop

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SensitiveTextPatternsTest {
    @Test
    fun containsSensitivePattern_detectsCommonPrivateText() {
        assertTrue(SensitiveTextPatterns.containsSensitivePattern("Email me at person@example.com"))
        assertTrue(SensitiveTextPatterns.containsSensitivePattern("Call +1 (555) 123-4567 today"))
        assertTrue(SensitiveTextPatterns.containsSensitivePattern("Router is 192.168.1.42"))
        assertTrue(SensitiveTextPatterns.containsSensitivePattern("MAC AA:BB:CC:DD:EE:FF"))
    }

    @Test
    fun containsSensitivePattern_requiresValidLuhnForCardCandidates() {
        assertTrue(SensitiveTextPatterns.containsSensitivePattern("Card 4111 1111 1111 1111"))
        assertTrue(SensitiveTextPatterns.passesLuhn("4111111111111111"))
        assertFalse(SensitiveTextPatterns.passesLuhn("4111111111111112"))
        assertFalse(SensitiveTextPatterns.containsSensitivePattern("Release build succeeded"))
    }

    @Test
    fun passesLuhn_rejectsEmptyOrZeroStrings() {
        assertFalse(SensitiveTextPatterns.passesLuhn(""))
        assertFalse(SensitiveTextPatterns.passesLuhn("0000000000000"))
    }

    @Test
    fun typedMatchesRejectDateAndInvalidNetworkLookalikes() {
        val categories = SensitiveTextPatterns.sensitiveMatches(
            "qa.fixture@example.test +1 202-555-0100 192.0.2.42 " +
                "2001:db8::42 02:00:00:00:00:01 GB82 WEST 1234 5698 7654 32"
        ).map { it.category }.toSet()

        assertEquals(
            setOf(
                SensitiveTextCategory.EMAIL,
                SensitiveTextCategory.PHONE,
                SensitiveTextCategory.IPV4,
                SensitiveTextCategory.IPV6,
                SensitiveTextCategory.MAC_ADDRESS,
                SensitiveTextCategory.IBAN
            ),
            categories
        )
        assertFalse(SensitiveTextPatterns.containsSensitivePattern("Build 2026-07-12 at 10:30"))
        assertFalse(SensitiveTextPatterns.containsSensitivePattern("Invalid host 999.999.999.999"))
    }

    @Test
    fun developerSecretsRequireStrictShapesOrHighSignalAssignments() {
        val positives = listOf(
            "Authorization: Bearer abcdefghijklmnop1234567890",
            "token = Abcd1234Efgh5678",
            "password=P@ssw0rd!",
            "AKIA" + "IOSFODNN7EXAMPLE",
            "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.signature12345678",
            "-----BEGIN PRIVATE KEY-----",
            "postgresql://user:S3cure!Pass@db.example.test/app",
        )
        positives.forEach { assertTrue(it, SensitiveTextPatterns.containsSensitivePattern(it)) }

        val negatives = listOf(
            "token = enabled",
            "password=password",
            "Bearer short",
            "AKIAIOSFODNN7EXAMPL",
            "eyJ.not-a-complete-token",
            "Build secret feature flag",
            "postgresql://db.example.test/app",
            "-----BEGIN PUBLIC KEY-----",
            "550e8400-e29b-41d4-a716-446655440000",
            "0123456789abcdef0123456789abcdef01234567",
        )
        negatives.forEach { assertFalse(it, SensitiveTextPatterns.containsSensitivePattern(it)) }

        val privateKey = "-----BEGIN PRIVATE KEY-----\nsynthetic-payload\n-----END PRIVATE KEY-----"
        val keyMatch = SensitiveTextPatterns.sensitiveMatches(privateKey)
            .single { it.category == SensitiveTextCategory.DEVELOPER_SECRET }
        assertEquals(privateKey.indices, keyMatch.range)
    }
}
