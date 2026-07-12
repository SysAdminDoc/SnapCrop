package com.sysadmindoc.snapcrop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.RandomAccessFile
import javax.crypto.KeyGenerator

@RunWith(RobolectricTestRunner::class)
class NetworkCredentialStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private fun newKey() = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    @Test
    fun encryptedFileRoundTripsOnlySupportedCredentials() {
        val file = temporaryFolder.newFile("credentials.bin")
        file.delete()
        val encryptedFile = EncryptedCredentialFile(file, newKey())

        encryptedFile.write(
            mapOf(
                NetworkExportSettings.PREF_AUTHORIZATION to "Bearer secret",
                NetworkExportSettings.PREF_IMGUR_CLIENT_ID to "client-id",
                "ignored" to "not persisted"
            )
        )

        assertEquals(
            mapOf(
                NetworkExportSettings.PREF_AUTHORIZATION to "Bearer secret",
                NetworkExportSettings.PREF_IMGUR_CLIENT_ID to "client-id"
            ),
            encryptedFile.read()
        )
        assertFalse(String(file.readBytes(), Charsets.ISO_8859_1).contains("Bearer secret"))
    }

    @Test
    fun tamperedCiphertextFailsClosed() {
        val file = temporaryFolder.newFile("tampered.bin")
        file.delete()
        val key = newKey()
        EncryptedCredentialFile(file, key).write(
            mapOf(NetworkExportSettings.PREF_AUTHORIZATION to "Basic hidden")
        )
        RandomAccessFile(file, "rw").use { random ->
            val position = random.length() - 1
            random.seek(position)
            val original = random.read()
            random.seek(position)
            random.write(original.xor(0x01))
        }

        val store = NetworkCredentialStore.openForTest(file, key)

        assertEquals(NetworkCredentialStore.Status.RECOVERY_REQUIRED, store.status)
        assertEquals("", store.getString(NetworkExportSettings.PREF_AUTHORIZATION))
        assertFalse(store.putString(NetworkExportSettings.PREF_AUTHORIZATION, "replacement"))
    }

    @Test
    fun migrationKeepsFirstNonBlankValueFromLegacySources() {
        val merged = NetworkCredentialStore.mergeCredentialSources(
            mapOf(NetworkExportSettings.PREF_AUTHORIZATION to "encrypted auth"),
            mapOf(
                NetworkExportSettings.PREF_AUTHORIZATION to "plain fallback",
                NetworkExportSettings.PREF_IMGUR_CLIENT_ID to "legacy client"
            ),
            mapOf(NetworkExportSettings.PREF_IMGUR_CLIENT_ID to "old main client")
        )

        assertEquals("encrypted auth", merged[NetworkExportSettings.PREF_AUTHORIZATION])
        assertEquals("legacy client", merged[NetworkExportSettings.PREF_IMGUR_CLIENT_ID])
    }

    @Test
    fun oversizedCredentialDisablesStoreWithoutPersistingPlaintext() {
        val file = temporaryFolder.newFile("oversized.bin")
        file.delete()
        val store = NetworkCredentialStore.openForTest(file, newKey())

        assertFalse(store.putString(NetworkExportSettings.PREF_AUTHORIZATION, "x".repeat(17 * 1024)))
        assertEquals(NetworkCredentialStore.Status.RECOVERY_REQUIRED, store.status)
        assertTrue(!file.exists() || file.length() == 0L)
    }
}
