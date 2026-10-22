package com.focustask.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VibrationsTest {

    /** The strongest step of each burst of vibration, in order. */
    private fun peaks(amps: IntArray): List<Int> {
        val out = ArrayList<Int>()
        var cur = 0
        for (a in amps + 0) {
            if (a > 0) cur = maxOf(cur, a) else if (cur > 0) { out += cur; cur = 0 }
        }
        return out
    }

    @Test
    fun eachMomentHasItsOwnFeel() {
        assertEquals(VibrationFeel.RISING, Vibrations.feelFor(AlertKind.BLOCK_STARTING))
        assertEquals(VibrationFeel.RISING, Vibrations.feelFor(AlertKind.BLOCK_ENDING))
        assertEquals(VibrationFeel.SOFT_FADE, Vibrations.feelFor(AlertKind.PHASE_CUE))
        assertEquals(VibrationFeel.HARD_FADE, Vibrations.feelFor(AlertKind.CYCLE_CHIME))
    }

    @Test
    fun theBlockVibrationEndsRightWhenTheBlockStartsOrEnds() {
        val rising = Vibrations.waveform(VibrationFeel.RISING).totalMs
        assertEquals(rising, Vibrations.leadMs(AlertKind.BLOCK_STARTING))
        assertEquals(rising, Vibrations.leadMs(AlertKind.BLOCK_ENDING))
        assertEquals(0L, Vibrations.leadMs(AlertKind.PHASE_CUE))
        assertEquals(0L, Vibrations.leadMs(AlertKind.CYCLE_CHIME))
    }

    @Test
    fun nearAStartOrAnEndTheTrianglesGetStrongerAndStronger() {
        val w = Vibrations.waveform(VibrationFeel.RISING)
        val p = peaks(w.amplitudes)
        assertEquals(5, p.size)
        assertTrue(p.zipWithNext().all { (a, b) -> b > a }, "every pulse is stronger: $p")
        assertEquals(255, p.last())
        // a triangle: up step by step, then down step by step
        val first = w.amplitudes.takeWhile { it > 0 }
        val top = first.indexOf(first.max())
        assertTrue(first.take(top + 1).zipWithNext().all { (a, b) -> b >= a })
        assertTrue(first.drop(top).zipWithNext().all { (a, b) -> b <= a })
    }

    @Test
    fun aBreakIsAShortSoftWaveThatFadesAway() {
        val w = Vibrations.waveform(VibrationFeel.SOFT_FADE)
        val p = peaks(w.amplitudes)
        assertTrue(p.size >= 4)
        assertTrue(p.zipWithNext().all { (a, b) -> b < a }, "every swell is weaker: $p")
        assertTrue(w.totalMs <= 1500)
        // soft: it swells instead of switching on at full strength
        assertTrue(w.amplitudes[1] < w.amplitudes.max() / 2)
    }

    @Test
    fun backToWorkIsTheSameShortWaveButHard() {
        val soft = Vibrations.waveform(VibrationFeel.SOFT_FADE)
        val hard = Vibrations.waveform(VibrationFeel.HARD_FADE)
        assertEquals(soft.totalMs, hard.totalMs)
        val p = peaks(hard.amplitudes)
        assertEquals(peaks(soft.amplitudes).size, p.size)
        assertTrue(p.zipWithNext().all { (a, b) -> b <= a }, "it fades too: $p")
        assertEquals(255, hard.amplitudes.max())
        assertTrue(hard.amplitudes.max() > soft.amplitudes.max())
        // hard: each pulse starts at its full strength straight away
        assertEquals(255, hard.amplitudes[0])
    }

    @Test
    fun everyStrengthIsOneThePhoneAccepts() {
        for (f in VibrationFeel.values()) {
            val w = Vibrations.waveform(f)
            assertEquals(w.timings.size, w.amplitudes.size)
            assertTrue(w.amplitudes.all { it in 0..255 })
            assertTrue(w.timings.all { it > 0 })
            val onOff = Vibrations.onOff(f)
            assertEquals(0L, onOff[0])
            assertTrue(onOff.drop(1).all { it > 0 })
        }
    }
}
