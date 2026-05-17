package com.sysadmindoc.snapcrop

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UserAppProfileStoreTest {
    @Test
    fun encodeDecode_roundTripsProfilePack() {
        val profile = UserAppCropProfile(
            id = "user-discord",
            label = "Discord",
            enabled = true,
            sourceHints = listOf("com.discord", "discord"),
            ocrKeywords = listOf("server"),
            cropLeftFraction = 0.02f,
            cropTopFraction = 0.08f,
            cropRightFraction = 0.03f,
            cropBottomFraction = 0.09f,
            albumName = "Discord",
            redactSensitiveText = true,
            exportFormat = "jpeg"
        )

        val decoded = UserAppProfileStore.decode(UserAppProfileStore.encode(listOf(profile)))

        assertEquals(listOf(profile), decoded)
    }

    @Test
    fun match_prefersCombinedSourceAndOcrEvidence() {
        val profile = UserAppCropProfile(
            id = "user-bank",
            label = "Bank",
            enabled = true,
            sourceHints = listOf("com.bank.app"),
            ocrKeywords = listOf("checking"),
            cropLeftFraction = 0f,
            cropTopFraction = 0.1f,
            cropRightFraction = 0f,
            cropBottomFraction = 0.1f,
            albumName = "Banking",
            redactSensitiveText = true,
            exportFormat = "png"
        )

        val match = UserAppProfileStore.match(
            profiles = listOf(profile),
            sourceHints = listOf("owner_package_name=com.bank.app"),
            ocrTextHints = listOf("Checking balance")
        )

        assertNotNull(match)
        assertEquals("user-bank", match?.profile?.id)
        assertEquals(0.98f, match?.confidence ?: 0f, 0.001f)
    }

    @Test
    fun appCropProfiles_appliesUserProfileCropBands() {
        val bitmap = Bitmap.createBitmap(400, 800, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        val profile = UserAppCropProfile(
            id = "user-chat",
            label = "Chat",
            enabled = true,
            sourceHints = listOf("com.chat.app"),
            ocrKeywords = emptyList(),
            cropLeftFraction = 0.05f,
            cropTopFraction = 0.10f,
            cropRightFraction = 0.05f,
            cropBottomFraction = 0.10f,
            albumName = "Chat",
            redactSensitiveText = false,
            exportFormat = "default"
        )

        val result = AppCropProfiles.apply(
            bitmap = bitmap,
            baseResult = AutoCrop.CropResult(Rect(0, 0, 400, 800), "full"),
            statusBarPx = 0,
            navBarPx = 0,
            sourceHints = listOf("content://media/com.chat.app/screenshot.png"),
            userProfiles = listOf(profile),
            enabled = true
        )

        assertEquals("profile:Chat", result.method)
        assertEquals(Rect(20, 80, 380, 720), result.rect)
    }
}
