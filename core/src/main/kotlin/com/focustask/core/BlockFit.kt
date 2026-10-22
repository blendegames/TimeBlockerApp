package com.focustask.core

/** A block on one day of a grid, in minutes from midnight. A [fixed] block cannot be made shorter. */
data class Span(val id: Long, val start: Int, val end: Int, val fixed: Boolean = false) {
    val length get() = end - start
}

/**
 * Keeps the blocks of one day from overlapping while one of them is dragged: only one list runs at a time.
 *
 * The block that is being changed pushes its neighbours: the block above it loses its end, the block below it
 * loses its start. A neighbour never grows back during the same drag, even when the dragged block moves away again,
 * and it never gets shorter than [MIN_LEN] (the dragged block stops there instead). Blocks that already overlapped
 * the dragged one when the drag began are left alone.
 *
 * With [canShrink] false (a block that is only being placed) the neighbours are walls.
 */
class BlockFit(
    private val baseStart: Int,
    private val baseEnd: Int,
    others: List<Span>,
    private val canShrink: Boolean = true,
) {
    private class Slot(val above: Boolean, val keep: Int, val original: Span, var start: Int, var end: Int)

    private val slots = others.filter { !(it.start < baseEnd && it.end > baseStart) }.map {
        Slot(
            above = it.end <= baseStart,
            keep = if (canShrink && !it.fixed) minOf(MIN_LEN, it.length) else it.length,
            original = it, start = it.start, end = it.end,
        )
    }
    private val lowest = slots.filter { it.above }.maxOfOrNull { it.original.start + it.keep } ?: 0
    private val highest = slots.filter { !it.above }.minOfOrNull { it.original.end - it.keep } ?: DAY

    /** Moves the whole block: its start follows [rawStart] as far as the neighbours allow. Returns the new (start, end). */
    fun move(rawStart: Int): Pair<Int, Int> {
        val len = baseEnd - baseStart
        val lo = maxOf(0, lowest)
        val hi = minOf(DAY, highest) - len
        val s = if (lo <= hi) rawStart.coerceIn(lo, hi) else baseStart
        push(s, s + len)
        return s to s + len
    }

    /** Drags the top edge. Returns the new start. */
    fun top(rawStart: Int): Int {
        val lo = maxOf(0, lowest)
        val hi = baseEnd - MIN_LEN
        val s = if (lo <= hi) rawStart.coerceIn(lo, hi) else rawStart.coerceIn(0, hi)
        push(s, baseEnd)
        return s
    }

    /** Drags the bottom edge. Returns the new end. */
    fun bottom(rawEnd: Int): Int {
        val lo = baseStart + MIN_LEN
        val hi = minOf(DAY, highest)
        val e = if (lo <= hi) rawEnd.coerceIn(lo, hi) else rawEnd.coerceIn(lo, DAY)
        push(baseStart, e)
        return e
    }

    private fun push(s: Int, e: Int) {
        for (slot in slots) {
            if (slot.above) slot.end = minOf(slot.end, maxOf(s, slot.start + slot.keep))
            else slot.start = maxOf(slot.start, minOf(e, slot.end - slot.keep))
        }
    }

    /** The neighbours as they look now: only those that were made shorter. */
    fun shortened(): List<Span> =
        slots.filter { it.start != it.original.start || it.end != it.original.end }
            .map { it.original.copy(start = it.start, end = it.end) }

    companion object {
        const val MIN_LEN = 15
        const val DAY = 24 * 60

        /**
         * The free room for a new block that would like to start at [from] and last [wanted] minutes, after a tap at
         * [tap]: it starts after the previous block ends and ends where the next block begins. Null when less than
         * [MIN_LEN] minutes are free there.
         */
        fun room(from: Int, tap: Int, wanted: Int, others: List<Span>): Pair<Int, Int>? {
            val prevEnd = others.filter { it.start <= tap }.maxOfOrNull { it.end }?.coerceAtLeast(0) ?: 0
            var s = maxOf(from, prevEnd)
            val nextStart = others.filter { it.start >= s }.minOfOrNull { it.start } ?: DAY
            var e = minOf(s + wanted, nextStart, DAY)
            if (e - s < MIN_LEN) { s = maxOf(e - MIN_LEN, prevEnd) }
            return if (e - s >= MIN_LEN) s to e else null
        }
    }
}
