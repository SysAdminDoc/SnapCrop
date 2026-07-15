package com.sysadmindoc.snapcrop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class HostUiStateContractTest {
    @Test
    fun matrixCoversEveryWorkflowAndRequiredState() {
        assertEquals(11, HostUiStateContract.workflows.size)
        assertEquals(HostWorkflow.entries.toSet(), HostUiStateContract.workflows.toSet())
        assertEquals(HostUiState.entries.toSet(), HostUiStateContract.states.toSet())
        assertEquals(44, HostUiStateContract.workflows.size * HostUiStateContract.states.size)
        assertTrue(HostUiStateContract.workflows.all { it.titleRes != 0 })
        assertTrue(HostUiStateContract.states.all { it.labelRes != 0 })
    }

    @Test
    fun interactiveNodesHaveButtonRolesOrderedTraversalAndFortyEightDpTargets() {
        HostUiStateContract.states.forEach { state ->
            val nodes = HostUiStateContract.nodes(state)
            assertEquals(nodes.size, nodes.map { it.key }.distinct().size)
            assertEquals(nodes.map { it.traversalIndex }.sorted(), nodes.map { it.traversalIndex })
            assertEquals(nodes.size, nodes.map { it.traversalIndex }.distinct().size)
            nodes.filter { it.role == HostSemanticRole.BUTTON }.forEach { node ->
                assertEquals(HostUiStateContract.MINIMUM_TOUCH_TARGET_DP, node.minimumTouchTargetDp)
            }
            nodes.filter { it.minimumTouchTargetDp != null }.forEach { node ->
                assertEquals(HostSemanticRole.BUTTON, node.role)
                assertTrue(node.minimumTouchTargetDp!! >= 48)
            }
        }
    }

    @Test
    fun everyReferenceHasLocalizedNamesAndStateDescriptions() {
        val context = RuntimeEnvironment.getApplication()
        HostUiStateContract.workflows.forEach { workflow ->
            val name = context.getString(workflow.titleRes)
            assertTrue(name.isNotBlank())
            HostUiStateContract.states.forEach { state ->
                val stateDescription = context.getString(state.labelRes)
                assertTrue(stateDescription.isNotBlank())
                assertTrue(
                    context.getString(R.string.ui_matrix_root_description, name, stateDescription)
                        .contains(name),
                )
            }
        }
    }
}
