package com.sysadmindoc.snapcrop

import org.junit.Assert.assertFalse
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
}
