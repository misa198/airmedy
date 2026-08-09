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

    fun prepare(file: File) {
        check(handle != 0L) { "Decoder is closed" }
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            nativePrepare(handle, descriptor.detachFd())
        }
    }

    fun play() = nativePlay(requireHandle())
    fun pause() = nativePause(requireHandle())
    fun stop() = nativeStop(requireHandle())
    fun seekTo(positionMs: Long) = nativeSeekTo(requireHandle(), positionMs)
    fun durationMs(): Long = nativeDurationMs(requireHandle())
    fun positionMs(): Long = nativePositionMs(requireHandle())
    fun isFinished(): Boolean = nativeIsFinished(requireHandle())

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
        @JvmStatic private external fun nativePrepare(handle: Long, fd: Int)
        @JvmStatic private external fun nativePlay(handle: Long)
        @JvmStatic private external fun nativePause(handle: Long)
        @JvmStatic private external fun nativeStop(handle: Long)
        @JvmStatic private external fun nativeSeekTo(handle: Long, positionMs: Long)
        @JvmStatic private external fun nativeDurationMs(handle: Long): Long
        @JvmStatic private external fun nativePositionMs(handle: Long): Long
        @JvmStatic private external fun nativeIsFinished(handle: Long): Boolean
    }
}
