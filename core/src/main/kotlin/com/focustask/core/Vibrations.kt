package com.focustask.core

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.roundToInt

/** How an alert feels in the hand. */
enum class VibrationFeel {
    /** A block starts or ends soon: triangle pulses, each one stronger than the last. */
    RISING,

    /** Time for a break: a short, soft wave that fades away. */
    SOFT_FADE,

    /** Back to work: the same short fading wave, but hard pulses with sharp edges. */
    HARD_FADE,
}

/** A vibration as steps of [timings] milliseconds, each at an [amplitudes] strength (0 = off, 255 = strongest). */
class Waveform(val timings: LongArray, val amplitudes: IntArray) {
    val totalMs: Long get() = timings.sum()
}

object Vibrations {
    const val STEP_MS = 20L

    fun feelFor(kind: AlertKind): VibrationFeel = when (kind) {
        AlertKind.BLOCK_STARTING, AlertKind.BLOCK_ENDING -> VibrationFeel.RISING
        AlertKind.PHASE_CUE -> VibrationFeel.SOFT_FADE
        AlertKind.CYCLE_CHIME -> VibrationFeel.HARD_FADE
    }

    /**
     * How long before its moment an alert starts, so that the vibration ends right on it: the growing pulses of a
     * block reach their strongest as the block starts or ends.
     */
    fun leadMs(kind: AlertKind): Long =
        if (feelFor(kind) == VibrationFeel.RISING) waveform(VibrationFeel.RISING).totalMs else 0L

    /** For phones that can set the strength of their vibration. */
    fun waveform(feel: VibrationFeel): Waveform {
        val amps = when (feel) {
            VibrationFeel.RISING -> rising()
            VibrationFeel.SOFT_FADE -> fading(hard = false)
            VibrationFeel.HARD_FADE -> fading(hard = true)
        }
        return Waveform(LongArray(amps.size) { STEP_MS }, amps.toIntArray())
    }

    /**
     * For phones that can only switch the motor on and off: alternating off and on times, starting with a pause.
     * Longer buzzes stand in for stronger ones.
     */
    fun onOff(feel: VibrationFeel): LongArray = when (feel) {
        VibrationFeel.RISING -> longArrayOf(0, 60, 140, 100, 140, 140, 140, 190, 140, 260)
        VibrationFeel.SOFT_FADE -> longArrayOf(0, 110, 90, 80, 90, 55, 90, 35)
        VibrationFeel.HARD_FADE -> longArrayOf(0, 160, 60, 120, 60, 85, 60, 50)
    }

    private const val PULSES = 5
    private const val RAMP_STEPS = 12      // 240 ms up, 240 ms down
    private const val GAP_STEPS = 5        // 100 ms between pulses

    /** Triangles whose peak grows from a fifth of full strength to full strength. */
    private fun rising(): List<Int> = buildList {
        for (k in 1..PULSES) {
            val peak = 255.0 * k / PULSES
            for (i in 1..RAMP_STEPS) add((peak * i / RAMP_STEPS).roundToInt())
            for (i in RAMP_STEPS - 1 downTo 0) add((peak * i / RAMP_STEPS).roundToInt())
            if (k < PULSES) repeat(GAP_STEPS) { add(0) }
        }
    }

    private const val FADE_MS = 1200.0
    private const val WAVE_HZ = 5.0
    private const val DECAY_MS = 450.0

    /** A wave that dies away: smooth swells when soft, square full-on / off pulses when hard. */
    private fun fading(hard: Boolean): List<Int> {
        val steps = (FADE_MS / STEP_MS).toInt()
        val period = 1000.0 / WAVE_HZ
        return (0 until steps).map { i ->
            val t = i * STEP_MS.toDouble()
            val envelope = exp(-t / DECAY_MS)
            if (hard) {
                val on = (t % period) < period / 2
                if (on) (255 * envelope).roundToInt().coerceAtLeast(40) else 0
            } else {
                val swell = 0.5 - 0.5 * cos(2 * PI * WAVE_HZ * t / 1000.0)
                (200 * envelope * swell).roundToInt()
            }
        }
    }
}
