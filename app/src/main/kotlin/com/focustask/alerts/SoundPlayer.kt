package com.focustask.alerts

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import java.io.File

/** The sounds that ship with the app. All of them were synthesized for FocusTask. */
data class BuiltInSound(val id: String, val label: String)

object BuiltInSounds {
    val all = listOf(
        BuiltInSound("bell", "Soft bell"),
        BuiltInSound("glass", "Glass"),
        BuiltInSound("marimba", "Marimba"),
        BuiltInSound("pop", "Soft pop"),
        BuiltInSound("rise", "Rising tone"),
        BuiltInSound("drop", "Gentle drop"),
    )
}

/** Plays one alert sound, shortened and faded out when it is longer than the user's limit. */
object SoundPlayer {
    private val main = Handler(Looper.getMainLooper())
    private var current: Playback? = null

    class Playback(private val mp: MediaPlayer, private val onFinished: () -> Unit) {
        private var done = false
        private val timers = ArrayList<Runnable>()

        fun later(ms: Long, r: Runnable) { timers += r; main.postDelayed(r, ms) }

        fun stop() {
            if (done) return
            done = true
            timers.forEach { main.removeCallbacks(it) }
            runCatching { if (mp.isPlaying) mp.stop() }
            runCatching { mp.release() }
            if (current === this) current = null
            onFinished()
        }

        fun setVolume(v: Float) { if (!done) runCatching { mp.setVolume(v, v) } }
    }

    fun uriFor(ctx: Context, source: String): Uri? = when {
        source.startsWith("builtin:") -> {
            val id = ctx.resources.getIdentifier("snd_" + source.removePrefix("builtin:"), "raw", ctx.packageName)
            if (id == 0) null else Uri.parse("android.resource://${ctx.packageName}/$id")
        }
        source.startsWith("file:") -> File(source.removePrefix("file:")).takeIf { it.exists() }?.let { Uri.fromFile(it) }
        else -> null
    }

    fun durationMs(ctx: Context, source: String): Long? {
        val uri = uriFor(ctx, source) ?: return null
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(ctx, uri)
            r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        } catch (e: Exception) {
            null
        } finally {
            runCatching { r.release() }
        }
    }

    /** Starts playing. [onFinished] is called when it ends or fails. Must be called from any thread. */
    fun play(ctx: Context, source: String, maxSec: Int, fade: Boolean, onFinished: () -> Unit = {}): Playback? {
        val app = ctx.applicationContext
        val uri = uriFor(app, source)
        if (uri == null) { onFinished(); return null }
        var pb: Playback? = null
        val mp = MediaPlayer()
        try {
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            mp.setDataSource(app, uri)
        } catch (e: Exception) {
            runCatching { mp.release() }
            onFinished()
            return null
        }
        val playback = Playback(mp, onFinished)
        pb = playback
        main.post {
            current?.stop()
            current = playback
            mp.setOnCompletionListener { playback.stop() }
            mp.setOnErrorListener { _, _, _ -> playback.stop(); true }
            mp.setOnPreparedListener { p ->
                p.start()
                val limit = maxSec * 1000L
                if (p.duration > limit) {
                    val fadeMs = if (fade) minOf(1500L, limit) else 0L
                    if (fadeMs > 0) {
                        val steps = 30
                        for (i in 1..steps) {
                            playback.later(limit - fadeMs + fadeMs * i / steps, Runnable {
                                playback.setVolume(1f - i.toFloat() / steps)
                            })
                        }
                    }
                    playback.later(limit, Runnable { playback.stop() })
                }
            }
            runCatching { mp.prepareAsync() }.onFailure { playback.stop() }
        }
        return pb
    }

    fun stopPreview() { main.post { current?.stop() } }
}
