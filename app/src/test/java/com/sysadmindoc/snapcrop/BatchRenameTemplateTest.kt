package com.sysadmindoc.snapcrop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class BatchRenameTemplateTest {
    @Test
    fun resolvesAppDateCounterAndProfileTokens() {
        val item = ExportItemMetadata(
            displayName = "Screenshot.png",
            relativePath = "Pictures/Screenshots/",
            sourceHint = "com.example.chat"
        )

        val result = BatchRenameTemplate.resolve(
            template = "%app%_%date%_%counter%_%profile%",
            item = item,
            counter = 7,
            nowMillis = 1_704_110_400_000L,
            profileName = "Incident"
        )

        assertEquals("chat_2024-01-01_0007_Incident", result)
    }

    @Test
    fun sanitizesInvalidCharactersAndWhitespace() {
        val item = ExportItemMetadata(displayName = "bad:name.png")

        val result = BatchRenameTemplate.resolve(
            template = "Case: 42 / %counter% * %app%",
            item = item,
            counter = 3,
            nowMillis = 1_704_110_400_000L,
            profileName = ""
        )

        assertFalse(result.contains(':'))
        assertFalse(result.contains('/'))
        assertFalse(result.contains('*'))
        assertEquals("Case_42_0003_bad_name", result)
    }
}
