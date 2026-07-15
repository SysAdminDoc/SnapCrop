package com.sysadmindoc.snapcrop

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveNavigationTest {
    @Test
    fun expandedShellStartsAtAndroidExpandedWidth() {
        assertEquals(AppShellLayoutClass.Compact, appShellLayoutClass(393f))
        assertEquals(AppShellLayoutClass.Compact, appShellLayoutClass(839.9f))
        assertEquals(AppShellLayoutClass.Expanded, appShellLayoutClass(840f))
        assertEquals(AppShellLayoutClass.Expanded, appShellLayoutClass(1280f))
    }

    @Test
    fun shellSwapsBottomNavigationForRailWithoutReplacingTabState() {
        val main = source("main", "MainActivity.kt")

        assertTrue(main.contains("BoxWithConstraints(Modifier.fillMaxSize())"))
        assertTrue(main.contains("shellLayout == AppShellLayoutClass.Expanded"))
        assertTrue(main.contains("shellLayout == AppShellLayoutClass.Compact"))
        assertTrue(main.contains("SnapCropNavigationRail("))
        assertTrue(main.contains("NavigationRailItem("))
        assertTrue(main.contains("onOpenSettings"))
        assertTrue(main.contains("rememberSaveable { mutableIntStateOf(0) }"))
        assertFalse(main.contains("remember(shellLayout) { mutableIntStateOf"))
    }

    @Test
    fun libraryUsesOneSaveableSelectionAcrossCompactAndListDetailLayouts() {
        val gallery = source("main", "GalleryScreen.kt")

        assertEquals(1, "var viewerIdentity by rememberSaveable".toRegex().findAll(gallery).count())
        assertTrue(gallery.contains("val photoGridState = rememberLazyGridState()"))
        assertTrue(gallery.contains("val selectedUris = rememberSaveable("))
        assertTrue(gallery.contains("var encodedFilters by rememberSaveable"))
        assertTrue(gallery.contains("LibraryListDetailLayout("))
        assertTrue(gallery.contains("if (!expandedLayout && viewerVisible)"))
        assertTrue(gallery.contains("activeViewerIdentity = if (expandedLayout) viewerIdentity else null"))
        assertTrue(gallery.contains("PredictiveBackHandler { progress ->"))
        assertTrue(gallery.contains("progress.collect { }"))
        assertTrue(gallery.contains("hingeWidth > 0.dp"))
    }

    @Test
    fun expandedSettingsAddsCategoriesWithoutForkingPreferenceState() {
        val settings = source("main", "SettingsActivity.kt")

        assertTrue(settings.contains("val settingsScrollState = rememberScrollState()"))
        assertTrue(settings.contains("val expandedSettings = appShellLayoutClass(maxWidth.value)"))
        assertTrue(settings.contains("SettingsCategoryNavigation("))
        assertTrue(settings.contains("onOpen = openDestination"))
        assertTrue(settings.contains(".verticalScroll(settingsScrollState)"))
        assertFalse(settings.contains("if (expandedSettings) { recreate()"))
    }

    @Test
    fun settingsSearchDestinationsResolveToStableExpandedCategories() {
        assertEquals(SettingsDestination.THEME, settingsCategoryAnchor(SettingsDestination.RECENT_WORKFLOWS))
        assertEquals(SettingsDestination.DELETE_ORIGINAL, settingsCategoryAnchor(SettingsDestination.APP_CROP_PROFILES))
        assertEquals(SettingsDestination.IMAGE_FORMAT, settingsCategoryAnchor(SettingsDestination.ML_MODELS))
        assertEquals(SettingsDestination.NETWORK_EXPORTS, settingsCategoryAnchor(SettingsDestination.LOCAL_NETWORK))
        assertEquals(SettingsDestination.WATERMARK, settingsCategoryAnchor(SettingsDestination.EXPORT_PRESETS))
        assertEquals(SettingsDestination.AUTO_START, settingsCategoryAnchor(SettingsDestination.SECURE_EDITOR))
        assertEquals(SettingsDestination.ABOUT, settingsCategoryAnchor(SettingsDestination.CRASH_LOGS))
    }

    @Test
    fun headlessReferenceCoversExpandedHingeAndExplicitInsets() {
        val reference = source("screenshotTest", "WorkflowStateScreenshotTest.kt")

        assertTrue(reference.contains("expanded-dark-hinge-insets"))
        assertTrue(reference.contains("hingeWidth = 32.dp"))
        assertTrue(reference.contains("contentPadding = PaddingValues("))
        assertTrue(reference.contains("SnapCropNavigationRail("))
        assertTrue(reference.contains("GalleryDetailEmptyState()"))
    }

    private fun source(sourceSet: String, name: String): String =
        File("src/$sourceSet/java/com/sysadmindoc/snapcrop/$name").readText()
}
