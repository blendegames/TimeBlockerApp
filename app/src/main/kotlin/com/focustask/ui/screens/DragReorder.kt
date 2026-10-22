package com.focustask.ui.screens

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The state of dragging one item of a short, vertical, ordered list to a new place.
 *
 * It works on ids and on the sizes the items had when the drag began, so the same logic serves the
 * lazy list of tasks and the column of subtasks. Positions are computed from the order itself (items
 * stacked one after the other), which keeps the swaps correct even before the screen has laid out the
 * new order.
 *
 * While a drag (and its saving) runs, [order] is the order to show; the saved order takes over again as
 * soon as the database agrees ([sync]).
 */
@Stable
class DragReorder {
    var dragId by mutableStateOf<Long?>(null)
        private set
    var order by mutableStateOf<List<Long>?>(null)
        private set
    private var total by mutableFloatStateOf(0f)
    private var scrolled by mutableFloatStateOf(0f)
    private var startTop = 0f
    private var base = 0f
    private var gap = 0f
    private var sizes: Map<Long, Float> = emptyMap()

    /** Where the top of the dragged item is on screen now (stays inside the list). */
    val visualTop: Float get() = clamped(startTop + total + scrolled) - scrolled

    /** Where the dragged item's top is in the same coordinates as [topOf]. */
    val dragTop: Float get() = clamped(startTop + total + scrolled)

    private fun size(id: Long) = sizes[id] ?: 0f

    private fun clamped(top: Float): Float {
        val id = dragId ?: return top
        val ord = order ?: return top
        val end = base + offsetBefore(ord.lastOrNull() ?: id, ord) + size(ord.lastOrNull() ?: id)
        return top.coerceIn(base, maxOf(base, end - size(id)))
    }

    private fun offsetBefore(id: Long, ord: List<Long>): Float {
        var y = 0f
        for (k in ord) { if (k == id) break; y += size(k) + gap }
        return y
    }

    /**
     * Starts dragging [id]. [ids] is the order on screen, [sizeOf] the height of an item (unmeasured
     * items count as the average), [gap] the space between items. [top] is where the dragged item is
     * now; when null the list is taken to start at 0.
     */
    fun begin(id: Long, ids: List<Long>, sizeOf: (Long) -> Float?, gap: Float = 0f, top: Float? = null) {
        val known = ids.mapNotNull(sizeOf)
        val avg = if (known.isEmpty()) 0f else known.sum() / known.size
        sizes = ids.associateWith { sizeOf(it) ?: avg }
        this.gap = gap
        total = 0f; scrolled = 0f
        order = ids
        dragId = id
        base = if (top == null) 0f else top - offsetBefore(id, ids)
        startTop = top ?: offsetBefore(id, ids)
    }

    /** The finger moved by [dy] pixels. */
    fun drag(dy: Float) { total += dy; settle() }

    /** The list scrolled by [by] pixels under the finger (towards later items when positive). */
    fun scroll(by: Float) { scrolled += by; settle() }

    /** Top of [id] in the current order; null when nothing is being reordered. */
    fun topOf(id: Long): Float? {
        val ord = order ?: return null
        if (id !in ord) return null
        return base + offsetBefore(id, ord)
    }

    private fun settle() {
        val id = dragId ?: return
        var guard = 0
        while (guard++ < 16) {
            val ord = order ?: return
            val i = ord.indexOf(id)
            if (i < 0) return
            // the finger decides, not the clamped picture: otherwise the last place could never be reached
            val middle = startTop + total + scrolled + size(id) / 2
            fun mid(k: Long) = base + offsetBefore(k, ord) + size(k) / 2
            val next = ord.getOrNull(i + 1)
            val prev = ord.getOrNull(i - 1)
            val j = when {
                next != null && middle > mid(next) -> i + 1
                prev != null && middle < mid(prev) -> i - 1
                else -> return
            }
            order = ord.toMutableList().also { it[i] = ord[j]; it[j] = id }
        }
    }

    /** Ends the drag. Returns the new order when it differs from [saved], else null. */
    fun finish(saved: List<Long>): List<Long>? {
        dragId = null
        total = 0f; scrolled = 0f
        val result = order?.takeIf { it != saved }
        if (result == null) order = null
        return result
    }

    /** The order to show for the saved order [ids]. */
    fun effective(ids: List<Long>): List<Long> =
        order?.let { o -> o.filter { it in ids } + ids.filter { it !in o } } ?: ids

    /** Lets go of the local order once it is saved (or no longer fits the list). */
    fun sync(ids: List<Long>) {
        val o = order ?: return
        if (dragId == null && (o == ids || o.toSet() != ids.toSet())) order = null
    }
}
