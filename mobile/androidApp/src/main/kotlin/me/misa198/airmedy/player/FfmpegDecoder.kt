package me.misa198.airmedy.player

import android.os.ParcelFileDescriptor
import java.io.Closeable
import java.io.File

/**
 * Thin JNI boundary. FFmpeg owns both demuxing and decoding; Android only
 * receives already-decoded float PCM through AAudio.
 */
internal class FfmpegDecoder : Closeable {
    private var handle: Long = nativeCreate()

    fun prepare(file: File, normalizationGainDb: Float = 0f) {
        check(handle != 0L) { "Decoder is closed" }
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            nativePrepare(handle, descriptor.detachFd(), normalizationGainDb)
        }
    }

    /** Opens and decodes the next item into the engine's idle source slot. */
    fun preload(file: File, normalizationGainDb: Float = 0f): Boolean {
        check(handle != 0L) { "Decoder is closed" }
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            return nativePreload(handle, descriptor.detachFd(), normalizationGainDb)
        }
    }

    fun setNormalizationGains(activeDb: Float, preloadedDb: Float) {
        nativeSetNormalizationGains(requireHandle(), activeDb, preloadedDb)
    }

    fun clearPreloaded() = nativeClearPreloaded(requireHandle())
    fun hasPreloaded(): Boolean = nativeHasPreloaded(requireHandle())
    fun beginCrossfade(durationMs: Long) = nativeBeginCrossfade(requireHandle(), durationMs)
    fun finishCrossfade() = nativeFinishCrossfade(requireHandle())
    fun snapCrossfade() = nativeSnapCrossfade(requireHandle())
    fun isCrossfading(): Boolean = nativeIsCrossfading(requireHandle())
    fun consumeTransition(): NativeTransition? = when (nativeConsumeTransition(requireHandle())) {
        NativeTransition.GaplessPromoted.code -> NativeTransition.GaplessPromoted
        NativeTransition.CrossfadeStarted.code -> NativeTransition.CrossfadeStarted
        else -> null
    }
    fun setGlobalDspConfig(config: GlobalDspConfig) = nativeSetGlobalDspConfig(
        requireHandle(), config.preampGainDb, config.stereoWidth, config.eqBandGainsDb,
    )

    fun play() = nativePlay(requireHandle())
    fun pause() = nativePause(requireHandle())
    fun stop() = nativeStop(requireHandle())
    fun seekTo(positionMs: Long) = nativeSeekTo(requireHandle(), positionMs)
    fun durationMs(): Long = nativeDurationMs(requireHandle())
    fun positionMs(): Long = nativePositionMs(requireHandle())
    fun preloadedDurationMs(): Long = nativePreloadedDurationMs(requireHandle())
    fun preloadedPositionMs(): Long = nativePreloadedPositionMs(requireHandle())
    fun isFinished(): Boolean = nativeIsFinished(requireHandle())
    fun isOutputDisconnected(): Boolean = nativeIsOutputDisconnected(requireHandle())

    override fun close() {
        val current = handle
        handle = 0L
        if (current != 0L) nativeDestroy(current)
    }

    private fun requireHandle(): Long = checkNotNull(handle.takeIf { it != 0L }) { "Decoder is closed" }

    private companion object {
        init { System.loadLibrary("airmedy_player") }

        @JvmStatic private external fun nativeCreate(): Long
        @JvmStatic private external fun nativeDestroy(handle: Long)
        @JvmStatic private external fun nativePrepare(handle: Long, fd: Int, normalizationGainDb: Float)
        @JvmStatic private external fun nativePreload(handle: Long, fd: Int, normalizationGainDb: Float): Boolean
        @JvmStatic private external fun nativeSetNormalizationGains(handle: Long, activeDb: Float, preloadedDb: Float)
        @JvmStatic private external fun nativeClearPreloaded(handle: Long)
        @JvmStatic private external fun nativeHasPreloaded(handle: Long): Boolean
        @JvmStatic private external fun nativeBeginCrossfade(handle: Long, durationMs: Long)
        @JvmStatic private external fun nativeFinishCrossfade(handle: Long)
        @JvmStatic private external fun nativeSnapCrossfade(handle: Long)
        @JvmStatic private external fun nativeIsCrossfading(handle: Long): Boolean
        @JvmStatic private external fun nativeConsumeTransition(handle: Long): Int
        @JvmStatic private external fun nativeSetGlobalDspConfig(handle: Long, preampGainDb: Float, stereoWidth: Float, eqBandGainsDb: FloatArray)
        @JvmStatic private external fun nativePlay(handle: Long)
        @JvmStatic private external fun nativePause(handle: Long)
        @JvmStatic private external fun nativeStop(handle: Long)
        @JvmStatic private external fun nativeSeekTo(handle: Long, positionMs: Long)
        @JvmStatic private external fun nativeDurationMs(handle: Long): Long
        @JvmStatic private external fun nativePositionMs(handle: Long): Long
        @JvmStatic private external fun nativePreloadedDurationMs(handle: Long): Long
        @JvmStatic private external fun nativePreloadedPositionMs(handle: Long): Long
        @JvmStatic private external fun nativeIsFinished(handle: Long): Boolean
        @JvmStatic private external fun nativeIsOutputDisconnected(handle: Long): Boolean
    }
}

internal enum class NativeTransition(internal val code: Int) {
    GaplessPromoted(1),
    CrossfadeStarted(2),
}

/** Reserved global post-mix DSP snapshot. The native stage is neutral today. */
internal data class GlobalDspConfig(
    val preampGainDb: Float = 0f,
    val stereoWidth: Float = 1f,
    val eqBandGainsDb: FloatArray = FloatArray(10),
)
