package com.sysadmindoc.snapcrop

import android.content.res.Resources
import java.lang.reflect.Modifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * The editor vocabulary, ML Kit feature names and help catalog render straight into the UI, so
 * they have to come from `strings.xml` or no translation can reach them.
 */
@RunWith(RobolectricTestRunner::class)
class DisplayLabelResourcesTest {
    private val resources: Resources get() = RuntimeEnvironment.getApplication().resources

    @Test
    fun labelCarryingTypesHoldNoTextOfTheirOwn() {
        // HelpEntry.id is a stable search key; it is never shown.
        val allowed = setOf("HelpEntry.id", "HelpEntry.getId()")
        val enums = listOf(DrawTool::class.java, ImageFilter::class.java, AspectRatio::class.java, MlKitFeature::class.java)
        val dataClasses = listOf(DrawColor::class.java, HelpEntry::class.java)

        val found = enums.flatMap { type ->
            // A constant with its own body compiles to a subclass that could carry text too.
            val bodies = type.enumConstants.filter { it.javaClass != type }
                .flatMap { constant -> textMembers("${type.simpleName}.${(constant as Enum<*>).name}", constant.javaClass, generated = emptySet()) }
            textMembers(type.simpleName, type, generated = emptySet()) + bodies
        } + dataClasses.flatMap { type ->
            // The compiler writes toString() and componentN() for a data class; they mirror the fields checked here.
            textMembers(type.simpleName, type, generated = setOf("toString") + (1..9).map { "component$it" })
        }

        assertEquals(emptyList<String>(), found - allowed)
    }

    /** Instance fields and no-argument methods of [type] that hold or return text. */
    private fun textMembers(owner: String, type: Class<*>, generated: Set<String>): List<String> {
        val isText = { cls: Class<*> -> CharSequence::class.java.isAssignableFrom(cls) }
        val fields = type.declaredFields
            .filter { !Modifier.isStatic(it.modifiers) && !it.isSynthetic && isText(it.type) }
            .map { "$owner.${it.name}" }
        val methods = type.declaredMethods
            .filter { !Modifier.isStatic(it.modifiers) && !it.isSynthetic && it.parameterCount == 0 && isText(it.returnType) }
            .filter { it.name !in generated }
            .map { "$owner.${it.name}()" }
        return fields + methods
    }

    @Test
    fun everyEditorLabelResolvesToDistinctText() {
        assertDistinctText("DrawTool", DrawTool.entries.map { it.labelRes })
        assertDistinctText("ImageFilter", ImageFilter.entries.map { it.labelRes })
        assertDistinctText("AspectRatio", AspectRatio.entries.map { it.labelRes })
        assertDistinctText("drawColors", drawColors.map { it.nameRes })
    }

    @Test
    fun everyMlKitFeatureHasBothForms() {
        assertDistinctText("MlKitFeature labels", MlKitFeature.entries.map { it.labelRes })
        assertDistinctText("MlKitFeature subjects", MlKitFeature.entries.map { it.subjectRes })
    }

    @Test
    fun everyHelpEntryHasItsOwnTitleAndSummary() {
        assertDistinctText("help titles", LocalHelpCatalog.entries.map { it.titleRes })
        assertDistinctText("help summaries", LocalHelpCatalog.entries.map { it.summaryRes })
    }

    private fun assertDistinctText(what: String, ids: List<Int>) {
        val text = ids.map(resources::getString)
        assertTrue("$what has a blank label: $text", text.none(String::isBlank))
        assertEquals("$what labels repeat: $text", text.size, text.toSet().size)
    }
}
