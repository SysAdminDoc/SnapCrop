@file:Suppress("DEPRECATION")

package com.sysadmindoc.snapcrop

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal class CredentialStoreCorruptException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

/** Versioned, bounded AES-GCM payload. The key itself never leaves Android Keystore. */
internal class EncryptedCredentialFile(
    file: File,
    private val key: SecretKey
) {
    private val atomicFile = AtomicFile(file)

    fun read(): Map<String, String> {
        if (!atomicFile.baseFile.isFile) return emptyMap()
        if (atomicFile.baseFile.length() !in 1..MAX_FILE_BYTES) {
            throw CredentialStoreCorruptException("Credential file size is invalid")
        }
        return try {
            DataInputStream(BufferedInputStream(atomicFile.openRead())).use { input ->
                if (input.readInt() != MAGIC) throw CredentialStoreCorruptException("Credential file header is invalid")
                if (input.readUnsignedByte() != FORMAT_VERSION) throw CredentialStoreCorruptException("Credential file version is unsupported")
                val ivLength = input.readUnsignedByte()
                if (ivLength !in 12..16) throw CredentialStoreCorruptException("Credential IV length is invalid")
                val cipherLength = input.readInt()
                if (cipherLength !in 16..MAX_CIPHERTEXT_BYTES) throw CredentialStoreCorruptException("Credential payload length is invalid")
                val iv = ByteArray(ivLength).also(input::readFully)
                val encrypted = ByteArray(cipherLength).also(input::readFully)
                if (input.read() != -1) throw CredentialStoreCorruptException("Credential file has trailing data")

                val cipher = Cipher.getInstance(CIPHER)
                cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
                cipher.updateAAD(AAD)
                decode(cipher.doFinal(encrypted))
            }
        } catch (e: CredentialStoreCorruptException) {
            throw e
        } catch (e: Exception) {
            throw CredentialStoreCorruptException("Credential file could not be decrypted", e)
        }
    }

    fun write(values: Map<String, String>) {
        val encoded = encode(values)
        val cipher = Cipher.getInstance(CIPHER)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        cipher.updateAAD(AAD)
        val encrypted = cipher.doFinal(encoded)
        check(encrypted.size <= MAX_CIPHERTEXT_BYTES) { "Credential payload is too large" }

        atomicFile.baseFile.parentFile?.mkdirs()
        var output: FileOutputStream? = atomicFile.startWrite()
        try {
            DataOutputStream(BufferedOutputStream(output)).let { data ->
                data.writeInt(MAGIC)
                data.writeByte(FORMAT_VERSION)
                data.writeByte(cipher.iv.size)
                data.writeInt(encrypted.size)
                data.write(cipher.iv)
                data.write(encrypted)
                data.flush()
                atomicFile.finishWrite(output)
                output = null
            }
        } catch (e: Exception) {
            output?.let(atomicFile::failWrite)
            throw e
        }
    }

    private fun encode(values: Map<String, String>): ByteArray {
        val json = JSONObject().put("schemaVersion", FORMAT_VERSION)
        SUPPORTED_KEYS.forEach { key ->
            values[key]?.takeIf(String::isNotBlank)?.let { value ->
                require(value.length <= MAX_VALUE_CHARS) { "Credential value is too large" }
                json.put(key, value)
            }
        }
        return json.toString().toByteArray(Charsets.UTF_8).also {
            require(it.size <= MAX_PLAINTEXT_BYTES) { "Credential payload is too large" }
        }
    }

    private fun decode(bytes: ByteArray): Map<String, String> {
        if (bytes.size > MAX_PLAINTEXT_BYTES) throw CredentialStoreCorruptException("Credential payload is too large")
        return try {
            val json = JSONObject(bytes.toString(Charsets.UTF_8))
            if (json.optInt("schemaVersion", -1) != FORMAT_VERSION) {
                throw CredentialStoreCorruptException("Credential payload version is unsupported")
            }
            buildMap {
                SUPPORTED_KEYS.forEach { key ->
                    if (json.has(key)) {
                        val value = json.getString(key)
                        if (value.length > MAX_VALUE_CHARS) throw CredentialStoreCorruptException("Credential value is too large")
                        if (value.isNotBlank()) put(key, value)
                    }
                }
            }
        } catch (e: CredentialStoreCorruptException) {
            throw e
        } catch (e: Exception) {
            throw CredentialStoreCorruptException("Credential payload is invalid", e)
        }
    }

    companion object {
        private const val MAGIC = 0x53434E43 // SCNC
        private const val FORMAT_VERSION = 1
        private const val CIPHER = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
        private const val MAX_FILE_BYTES = 80L * 1024L
        private const val MAX_CIPHERTEXT_BYTES = 64 * 1024
        private const val MAX_PLAINTEXT_BYTES = 48 * 1024
        private const val MAX_VALUE_CHARS = 16 * 1024
        private val AAD = "SnapCrop.NetworkCredentials.v1".toByteArray(Charsets.UTF_8)
        private val SUPPORTED_KEYS = setOf(
            NetworkExportSettings.PREF_AUTHORIZATION,
            NetworkExportSettings.PREF_IMGUR_CLIENT_ID
        )
    }
}

class NetworkCredentialStore private constructor(
    private val encryptedFile: EncryptedCredentialFile?,
    initialValues: Map<String, String>,
    initialStatus: Status
) {
    enum class Status {
        READY,
        MIGRATED,
        RECOVERY_REQUIRED;

        val isUsable: Boolean get() = this != RECOVERY_REQUIRED
    }

    private val values = initialValues.toMutableMap()

    @Volatile
    var status: Status = initialStatus
        private set

    @Synchronized
    fun getString(key: String, defaultValue: String = ""): String =
        if (status.isUsable) values[key] ?: defaultValue else defaultValue

    @Synchronized
    fun putString(key: String, value: String): Boolean {
        if (!status.isUsable || encryptedFile == null || key !in SUPPORTED_KEYS) return false
        val updated = values.toMutableMap().apply {
            if (value.isBlank()) remove(key) else put(key, value)
        }
        return try {
            encryptedFile.write(updated)
            values.clear()
            values.putAll(updated)
            true
        } catch (_: Exception) {
            values.clear()
            status = Status.RECOVERY_REQUIRED
            false
        }
    }

    companion object {
        private const val KEY_ALIAS = "snapcrop.network.credentials.v2"
        private const val FILE_NAME = "network-credentials-v2.bin"
        private const val LEGACY_STORE = "snapcrop_credentials"
        private const val LEGACY_PLAIN_STORE = "snapcrop_credentials_plain"
        private const val MAIN_STORE = "snapcrop"
        private const val MIGRATION_MARKER = "network_credentials_v2_migrated"
        private val SUPPORTED_KEYS = setOf(
            NetworkExportSettings.PREF_AUTHORIZATION,
            NetworkExportSettings.PREF_IMGUR_CLIENT_ID
        )

        fun open(context: Context): NetworkCredentialStore {
            val appContext = context.applicationContext
            val encryptedFile = try {
                EncryptedCredentialFile(appContext.noBackupFilesDir.resolve(FILE_NAME), getOrCreateKey())
            } catch (_: Exception) {
                return NetworkCredentialStore(null, emptyMap(), Status.RECOVERY_REQUIRED)
            }

            if (encryptedFileBase(appContext).isFile) {
                return try {
                    val values = encryptedFile.read()
                    finishLegacyCleanup(appContext)
                    NetworkCredentialStore(encryptedFile, values, Status.READY)
                } catch (_: CredentialStoreCorruptException) {
                    NetworkCredentialStore(null, emptyMap(), Status.RECOVERY_REQUIRED)
                }
            }

            val mainPrefs = appContext.getSharedPreferences(MAIN_STORE, Context.MODE_PRIVATE)
            if (mainPrefs.getBoolean(MIGRATION_MARKER, false)) {
                return NetworkCredentialStore(encryptedFile, emptyMap(), Status.READY)
            }

            val legacyValues = try {
                readLegacyEncrypted(appContext)
            } catch (_: Exception) {
                return NetworkCredentialStore(null, emptyMap(), Status.RECOVERY_REQUIRED)
            }
            val plainFallback = readValues(appContext.getSharedPreferences(LEGACY_PLAIN_STORE, Context.MODE_PRIVATE))
            val oldMainValues = readValues(mainPrefs)
            val migrated = mergeCredentialSources(legacyValues, plainFallback, oldMainValues)
            return try {
                if (migrated.isNotEmpty()) encryptedFile.write(migrated)
                finishLegacyCleanup(appContext)
                NetworkCredentialStore(
                    encryptedFile,
                    migrated,
                    if (migrated.isEmpty()) Status.READY else Status.MIGRATED
                )
            } catch (_: Exception) {
                NetworkCredentialStore(null, emptyMap(), Status.RECOVERY_REQUIRED)
            }
        }

        fun reset(context: Context): NetworkCredentialStore {
            val appContext = context.applicationContext
            AtomicFile(encryptedFileBase(appContext)).delete()
            appContext.deleteSharedPreferences(LEGACY_STORE)
            appContext.deleteSharedPreferences(LEGACY_PLAIN_STORE)
            appContext.getSharedPreferences(MAIN_STORE, Context.MODE_PRIVATE).edit()
                .remove(NetworkExportSettings.PREF_AUTHORIZATION)
                .remove(NetworkExportSettings.PREF_IMGUR_CLIENT_ID)
                .putBoolean(MIGRATION_MARKER, true)
                .commit()
            runCatching {
                val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
                keyStore.deleteEntry(KEY_ALIAS)
            }
            return open(appContext)
        }

        internal fun openForTest(file: File, key: SecretKey): NetworkCredentialStore {
            val encryptedFile = EncryptedCredentialFile(file, key)
            return try {
                NetworkCredentialStore(encryptedFile, encryptedFile.read(), Status.READY)
            } catch (_: CredentialStoreCorruptException) {
                NetworkCredentialStore(null, emptyMap(), Status.RECOVERY_REQUIRED)
            }
        }

        internal fun mergeCredentialSources(vararg sources: Map<String, String>): Map<String, String> =
            buildMap {
                sources.forEach { source ->
                    SUPPORTED_KEYS.forEach { key ->
                        if (key !in this) source[key]?.takeIf(String::isNotBlank)?.let { put(key, it) }
                    }
                }
            }

        private fun encryptedFileBase(context: Context): File = context.noBackupFilesDir.resolve(FILE_NAME)

        private fun getOrCreateKey(): SecretKey {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
            return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
                init(
                    KeyGenParameterSpec.Builder(
                        KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                    )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        .setRandomizedEncryptionRequired(true)
                        .build()
                )
                generateKey()
            }
        }

        @Suppress("DEPRECATION")
        private fun readLegacyEncrypted(context: Context): Map<String, String> {
            val legacyFile = File(context.applicationInfo.dataDir, "shared_prefs/$LEGACY_STORE.xml")
            if (!legacyFile.isFile) return emptyMap()
            val masterKey = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            val prefs = EncryptedSharedPreferences.create(
                LEGACY_STORE,
                masterKey,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            return readValues(prefs)
        }

        private fun readValues(prefs: android.content.SharedPreferences): Map<String, String> =
            buildMap {
                SUPPORTED_KEYS.forEach { key ->
                    prefs.getString(key, null)?.takeIf(String::isNotBlank)?.let { put(key, it) }
                }
            }

        private fun finishLegacyCleanup(context: Context) {
            context.deleteSharedPreferences(LEGACY_STORE)
            context.deleteSharedPreferences(LEGACY_PLAIN_STORE)
            context.getSharedPreferences(MAIN_STORE, Context.MODE_PRIVATE).edit()
                .remove(NetworkExportSettings.PREF_AUTHORIZATION)
                .remove(NetworkExportSettings.PREF_IMGUR_CLIENT_ID)
                .putBoolean(MIGRATION_MARKER, true)
                .commit()
        }
    }
}
