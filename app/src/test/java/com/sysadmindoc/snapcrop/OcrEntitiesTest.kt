package com.sysadmindoc.snapcrop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrEntitiesTest {
    @Test
    fun detectsActionableAndCopyableEntities() {
        val text = """
            Contact me at Jane.Doe@example.com or +1 (555) 123-4567.
            Docs: https://example.com/page and www.example.org
            IBAN GB82 WEST 1234 5698 7654 32
        """.trimIndent()
        val types = extractEntities(text).map { it.type }.toSet()
        assertTrue(OcrEntityType.EMAIL in types)
        assertTrue(OcrEntityType.PHONE in types)
        assertTrue(OcrEntityType.URL in types)
        assertTrue(OcrEntityType.IBAN in types)
    }

    @Test
    fun ipv4IsNotMisreadAsPhone() {
        val entities = extractEntities("Server 192.168.1.100 gateway")
        assertEquals(1, entities.size)
        assertEquals(OcrEntityType.IPV4, entities.single().type)
        assertEquals("192.168.1.100", entities.single().value)
    }

    @Test
    fun macAddressIsCopyable() {
        val e = extractEntities("MAC 3D:F2:C9:A6:B3:4F here").single()
        assertEquals(OcrEntityType.MAC, e.type)
        assertTrue(!e.type.actionable)
    }

    @Test
    fun urlIsNormalizedAndPhoneKeepsPlusDigits() {
        val entities = extractEntities("visit www.site.com call +1-555-987-6543")
        val url = entities.first { it.type == OcrEntityType.URL }
        assertEquals("https://www.site.com", url.value)
        val phone = entities.first { it.type == OcrEntityType.PHONE }
        assertEquals("+15559876543", phone.value)
    }

    @Test
    fun deduplicatesRepeatedValuesAndRespectsLimit() {
        val text = (1..20).joinToString(" ") { "user$it@mail.com" } + " user1@mail.com"
        val emails = extractEntities(text, limit = 5)
        assertEquals(5, emails.size)
        assertEquals(emails.size, emails.map { it.value.lowercase() }.toSet().size)
    }
}
