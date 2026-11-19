package cn.ppps.forwarder.core.editor

import org.junit.Assert.*
import org.junit.Test

/** Pure sizing decisions for the Delete/Save/Test row (JVM; no rendering). */
class ActionRowSizingTest {
    @Test fun sideBySideWeightsFollowLabelWidths() {
        val plan = ActionRowSizing.plan(1000, listOf(100f, 80f, 120f), 32f)
        assertFalse(plan.stacked)
        assertEquals(listOf(132f, 112f, 152f), plan.weights)
    }

    @Test fun stacksWhenLabelsDoNotFit() {
        val plan = ActionRowSizing.plan(300, listOf(100f, 80f, 120f), 32f)
        assertTrue(plan.stacked)
        assertEquals(listOf(0f, 0f, 0f), plan.weights)
    }

    @Test fun exactFitStaysSideBySide() {
        assertFalse(ActionRowSizing.plan(396, listOf(100f, 80f, 120f), 32f).stacked)
        assertTrue(ActionRowSizing.plan(395, listOf(100f, 80f, 120f), 32f).stacked)
    }

    @Test fun goneButtonDoesNotForceStacking() {
        // Task editor: Delete + Save visible, Test GONE. 132 + 112 fits 300; with Test it would not.
        val plan = ActionRowSizing.plan(300, listOf(100f, 80f, null), 32f)
        assertFalse(plan.stacked)
        assertEquals(listOf(132f, 112f, 0f), plan.weights)
        assertTrue(ActionRowSizing.plan(300, listOf(100f, 80f, 120f), 32f).stacked)
    }

    @Test fun buttonVisibilityChangeReplans() {
        val memo = ActionRowSizingMemo()
        assertTrue(memo.shouldApply(500, listOf(100f, 80f, null)))
        assertTrue(memo.shouldApply(500, listOf(100f, 80f, 120f)))
    }

    @Test fun memoAppliesOnceForUnchangedInputAndAgainOnRealChange() {
        val memo = ActionRowSizingMemo()
        val labels = listOf(100f, 80f, 120f)
        assertTrue(memo.shouldApply(1000, labels))
        repeat(10) { assertFalse("own relayout must not loop", memo.shouldApply(1000, labels)) }
        assertTrue("rotation/wide padding width", memo.shouldApply(1600, labels))
        assertTrue("Delete -> Discard label", memo.shouldApply(1600, listOf(130f, 80f, 120f)))
        assertFalse(memo.shouldApply(1600, listOf(130f, 80f, 120f)))
    }

    @Test fun zeroWidthNeverPlansStacking() {
        val memo = ActionRowSizingMemo()
        assertFalse("pre-layout or GONE row", memo.shouldApply(0, listOf(100f, 80f)))
        assertFalse(memo.needsApply(-20, listOf(100f, 80f)))
        assertTrue("first real width still applies", memo.shouldApply(500, listOf(100f, 80f)))
    }

    @Test fun needsApplyDoesNotRecord() {
        val memo = ActionRowSizingMemo()
        assertTrue(memo.needsApply(500, listOf(1f)))
        assertTrue(memo.needsApply(500, listOf(1f)))
        assertTrue(memo.shouldApply(500, listOf(1f)))
        assertFalse(memo.needsApply(500, listOf(1f)))
    }
}
