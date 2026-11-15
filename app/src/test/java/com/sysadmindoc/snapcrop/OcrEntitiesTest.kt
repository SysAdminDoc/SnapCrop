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

    // Issue #4: links OCR'd from a screenshot usually have no scheme.
    @Test
    fun bareDomainWithPathIsALink() {
        val url = extractEntities("Source: github.com/SysAdminDoc/SnapCrop.").single()
        assertEquals(OcrEntityType.URL, url.type)
        assertEquals("https://github.com/SysAdminDoc/SnapCrop", url.value)
        assertEquals("github.com/SysAdminDoc/SnapCrop", url.display)
    }

    @Test
    fun bareDomainsAcrossCommonTldsAreLinks() {
        val values = extractEntities("bbc.co.uk/news, docs.example.dev and youtu.be/abc123")
            .filter { it.type == OcrEntityType.URL }
            .map { it.value }
        assertEquals(listOf("https://bbc.co.uk/news", "https://docs.example.dev", "https://youtu.be/abc123"), values)
    }

    @Test
    fun fileNamesVersionsAndRunOnSentencesAreNotLinks() {
        val text = "Open README.md and build.gradle.kts, then photo.jpg. Version 6.92.0 is done.It works"
        assertTrue(extractEntities(text).none { it.type == OcrEntityType.URL })
    }

    @Test
    fun trailingPunctuationAndUnbalancedBracketsAreTrimmed() {
        val values = extractEntities("(see https://example.com/a). Also https://example.org/b!")
            .map { it.value }
        assertEquals(listOf("https://example.com/a", "https://example.org/b"), values)
    }

    @Test
    fun balancedBracketsInsideAUrlAreKept() {
        val url = extractEntities("https://en.wikipedia.org/wiki/Foo_(bar)").single()
        assertEquals("https://en.wikipedia.org/wiki/Foo_(bar)", url.value)
    }

    @Test
    fun uppercaseSchemeIsLoweredSoTheBrowserAcceptsIt() {
        val url = extractEntities("HTTPS://Example.com/Path").single()
        assertEquals("https://Example.com/Path", url.value)
    }

    @Test
    fun emailDomainIsNotAlsoALink() {
        val entities = extractEntities("Write to jane@example.com today")
        assertEquals(listOf(OcrEntityType.EMAIL), entities.map { it.type })
    }

    @Test
    fun wwwInsideAWordIsNotTreatedAsAPrefix() {
        val url = extractEntities("awww.com").single()
        assertEquals("https://awww.com", url.value)
    }

    @Test
    fun chipTextDropsTheSchemeAndCapsLongLinks() {
        assertEquals("example.com", urlChipText("https://www.example.com/"))
        val long = urlChipText("https://example.com/a/really/long/path/that/keeps/going/on")
        assertEquals(32, long.length)
        assertEquals(ELLIPSIS, long.last())
    }

    private companion object {
        val ELLIPSIS = Char(0x2026)
    }
}
