package com.sysadmindoc.snapcrop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkExportClientTest {
    @Test
    fun httpAndWebDavRequireExplicitEndpoint() {
        val disabled = NetworkExportSettings(
            enabled = false,
            target = NetworkExportTarget.HTTP,
            endpoint = "https://example.test/upload",
            authorizationHeader = "",
            imgurClientId = ""
        )
        val enabledHttp = disabled.copy(enabled = true)
        val missingEndpoint = enabledHttp.copy(endpoint = "")

        assertFalse(disabled.isConfigured)
        assertTrue(enabledHttp.isConfigured)
        assertFalse(missingEndpoint.isConfigured)
    }

    @Test
    fun imgurRequiresClientId() {
        val settings = NetworkExportSettings(
            enabled = true,
            target = NetworkExportTarget.IMGUR,
            endpoint = "",
            authorizationHeader = "",
            imgurClientId = ""
        )

        assertFalse(settings.isConfigured)
        assertTrue(settings.copy(imgurClientId = "client123").isConfigured)
    }

    @Test
    fun webDavFolderEndpointAppendsEncodedFileName() {
        val result = NetworkExportClient.appendWebDavFileName(
            endpoint = "https://nextcloud.example/remote.php/dav/files/me/SnapCrop/",
            fileName = "Incident Report 01.pdf"
        )

        assertEquals(
            "https://nextcloud.example/remote.php/dav/files/me/SnapCrop/Incident%20Report%2001.pdf",
            result
        )
    }
}
