package com.focustask.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BlockFitTest {
    private fun h(x: Int) = x * 60
    private val above = Span(1, h(8), h(10))
    private val below = Span(2, h(14), h(16))

    @Test fun aBlockInOpenSpaceMovesFreely() {
        val f = BlockFit(h(11), h(12), listOf(above, below))
        assertEquals(h(12) to h(13), f.move(h(12)))
        assertTrue(f.shortened().isEmpty())
    }

    @Test fun draggingTheTopIntoTheBlockAboveShortensIt() {
        val f = BlockFit(h(11), h(12), listOf(above, below))
        assertEquals(h(9), f.top(h(9)))
        assertEquals(listOf(above.copy(end = h(9))), f.shortened())
    }

    @Test fun draggingTheBottomIntoTheBlockBelowShortensIt() {
        val f = BlockFit(h(11), h(12), listOf(above, below))
        assertEquals(h(15), f.bottom(h(15)))
        assertEquals(listOf(below.copy(start = h(15))), f.shortened())
    }

    @Test fun theNeighbourDoesNotGrowBackWhenTheDraggedBlockRetreats() {
        val f = BlockFit(h(11), h(12), listOf(above, below))
        f.top(h(9))
        assertEquals(h(11), f.top(h(11)))
        assertEquals(listOf(above.copy(end = h(9))), f.shortened())
    }

    @Test fun aNeighbourStopsAtTheMinimumAndTheDraggedBlockStopsWithIt() {
        val f = BlockFit(h(11), h(12), listOf(above, below))
        assertEquals(h(8) + 15, f.top(h(5)))
        assertEquals(above.copy(end = h(8) + 15), f.shortened().single())
    }

    @Test fun movingNeverJumpsOverANeighbour() {
        val f = BlockFit(h(11), h(12), listOf(above, below))
        // the block below is pressed down to its last quarter hour, and the dragged block stops there
        assertEquals(h(16) - 15 - 60 to h(16) - 15, f.move(h(20)))
        assertEquals(below.copy(start = h(16) - 15), f.shortened().single())
    }

    @Test fun movingDownPushesTheBlockBelowDown() {
        val f = BlockFit(h(11), h(12), listOf(above, below))
        assertEquals(h(13) + 30 to h(14) + 30, f.move(h(13) + 30))
        assertEquals(below.copy(start = h(14) + 30), f.shortened().single())
    }

    @Test fun wallsDoNotShrinkWhenPlacingANewBlock() {
        val f = BlockFit(h(11), h(12), listOf(above, below), canShrink = false)
        assertEquals(h(10), f.top(h(5)))
        assertEquals(h(14), f.bottom(h(20)))
        assertTrue(f.shortened().isEmpty())
    }

    @Test fun aBlockThatCannotShrinkIsAWall() {
        val f = BlockFit(h(11), h(12), listOf(above.copy(fixed = true)))
        assertEquals(h(10), f.top(h(5)))
        assertTrue(f.shortened().isEmpty())
    }

    @Test fun blocksThatAlreadyOverlapAreIgnored() {
        val f = BlockFit(h(11), h(12), listOf(Span(3, h(11) + 30, h(13))))
        assertEquals(h(12) + 30, f.bottom(h(12) + 30))
        assertTrue(f.shortened().isEmpty())
    }

    @Test fun aNewBlockFitsIntoTheFreeRoom() {
        assertEquals(h(11) to h(12), BlockFit.room(h(11), h(11), 60, listOf(above, below)))
        assertEquals(h(13) to h(14), BlockFit.room(h(13), h(13), 60, listOf(above, below)))
        // the hour would run into the next block: it ends there
        assertEquals(h(9) to h(10), BlockFit.room(h(9), h(9) + 30, 60, listOf(Span(5, h(10), h(11)))))
        // the tap fell where the previous block ends a little later
        assertEquals(h(9) + 20 to h(10), BlockFit.room(h(9), h(9) + 30, 60, listOf(Span(6, h(8), h(9) + 20), Span(5, h(10), h(11)))))
    }

    @Test fun noRoomWhenTheGapIsTooSmall() {
        assertNull(BlockFit.room(h(10), h(10) + 15, 60, listOf(Span(1, h(9), h(10) + 10), Span(2, h(10) + 20, h(12)))))
    }
}
