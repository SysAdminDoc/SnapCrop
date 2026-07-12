package com.sysadmindoc.snapcrop

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class AccessibilityDisclosureTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun `consent is purpose specific and timestamped`() {
        AccessibilityDisclosure.recordConsent(context, AccessibilityPurpose.LONG_SCREENSHOT, acceptedAt = 1234L)

        assertTrue(AccessibilityDisclosure.hasCurrentConsent(context, AccessibilityPurpose.LONG_SCREENSHOT))
        assertFalse(AccessibilityDisclosure.hasCurrentConsent(context, AccessibilityPurpose.STEP_CAPTURE))
        assertEquals(1234L, AccessibilityDisclosure.acceptedAt(context, AccessibilityPurpose.LONG_SCREENSHOT))
    }

    @Test
    fun `old consent version fails closed`() {
        context.getSharedPreferences("snapcrop_accessibility_consent", Context.MODE_PRIVATE).edit()
            .putInt(AccessibilityDisclosure.versionKey(AccessibilityPurpose.STEP_CAPTURE), 0)
            .apply()

        assertFalse(AccessibilityDisclosure.hasCurrentConsent(context, AccessibilityPurpose.STEP_CAPTURE))
    }

    @Test
    fun `intent round trips only known purposes`() {
        val intent = AccessibilityDisclosure.intent(context, AccessibilityPurpose.STEP_CAPTURE)

        assertEquals(AccessibilityPurpose.STEP_CAPTURE, AccessibilityDisclosure.purpose(intent))
        intent.putExtra(AccessibilityDisclosure.EXTRA_PURPOSE, "UNKNOWN")
        assertNull(AccessibilityDisclosure.purpose(intent))
    }

    @Test
    fun `routing discloses before setup and starts only when ready`() {
        assertEquals(
            AccessibilityAction.SHOW_DISCLOSURE,
            AccessibilityDisclosure.route(AccessibilityPurpose.LONG_SCREENSHOT, false, false, false)
        )
        assertEquals(
            AccessibilityAction.OPEN_SETTINGS,
            AccessibilityDisclosure.route(AccessibilityPurpose.LONG_SCREENSHOT, false, false, true)
        )
        assertEquals(
            AccessibilityAction.START,
            AccessibilityDisclosure.route(AccessibilityPurpose.LONG_SCREENSHOT, true, false, true)
        )
    }

    @Test
    fun `active step session always routes to stop`() {
        assertEquals(
            AccessibilityAction.STOP,
            AccessibilityDisclosure.route(AccessibilityPurpose.STEP_CAPTURE, true, true, false)
        )
    }
}
