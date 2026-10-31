package me.misa198.airmedy.ui.navigation

import android.animation.ValueAnimator
import android.database.ContentObserver
import android.graphics.Bitmap
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES20
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.nio.ByteBuffer
import java.nio.ByteOrder

@Composable
internal fun LivingArtworkBackground(
    palette: FloatArray?,
    outgoingPalette: FloatArray?,
    isPlaying: Boolean,
    crossfadeId: Long?,
    crossfadeProgress: Float,
    onFailure: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val lifecycleState by lifecycle.currentStateFlow.collectAsState()
    var motionEnabled by remember { mutableStateOf(ValueAnimator.areAnimatorsEnabled()) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    var image by remember { mutableStateOf<ImageBitmap?>(null) }
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { motionEnabled = ValueAnimator.areAnimatorsEnabled() }
        }
        context.contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    val renderer = remember { LivingArtworkRenderer(onFrame = { image = it }, onFailure = onFailure) }
    DisposableEffect(renderer) { onDispose { renderer.close() } }
    LaunchedEffect(palette?.toList(), outgoingPalette?.toList(), crossfadeId, crossfadeProgress,
        isPlaying, lifecycleState, motionEnabled, size) {
        renderer.update(palette, outgoingPalette, crossfadeId, crossfadeProgress, isPlaying,
            lifecycleState.isAtLeast(Lifecycle.State.STARTED), motionEnabled, size)
    }
    // Compose owns the final pixels, including panel transforms, clipping and Haze recording.
    Box(Modifier.fillMaxSize().clipToBounds().onSizeChanged { size = it }.testTag("living_artwork_background")) {
        image?.let {
            Image(
                bitmap = it, contentDescription = null, contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = 1.2f; scaleY = 1.2f }
                    .blur(32.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded),
            )
            ArtworkDither()
        }
    }
}

/** A small offscreen GL buffer avoids native-view layers escaping Compose/Haze transforms. */
// shortcut: low-resolution readback copies frames, use GPU image sharing if profiling shows copy/GC pressure.
private class LivingArtworkRenderer(
    private val onFrame: (ImageBitmap) -> Unit,
    private val onFailure: () -> Unit,
) {
    private val thread = HandlerThread("LivingArtwork").apply { start() }
    private val handler = Handler(thread.looper)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var display = EGL14.EGL_NO_DISPLAY
    private var context = EGL14.EGL_NO_CONTEXT
    private var surface = EGL14.EGL_NO_SURFACE
    private var config: EGLConfig? = null
    private var program = 0
    private var width = 0
    private var height = 0
    private var pixels: ByteBuffer? = null
    private val paletteAnimation = ArtworkPaletteAnimation()
    private var playing = false
    @Volatile private var active = false
    private var motion = true
    private var failed = false
    @Volatile private var closed = false
    @Volatile private var pendingImage: ImageBitmap? = null
    private val publishFrame = Runnable {
        if (!closed && active) pendingImage?.let(onFrame)
    }
    private var lastFrame = 0L
    private var time = 0f
    private val vertices = ByteBuffer.allocateDirect(6 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
        put(floatArrayOf(-1f, -1f, 3f, -1f, -1f, 3f)); position(0)
    }
    private val frame = object : Runnable {
        override fun run() {
            if (!active || failed || program == 0) return
            try {
                val now = System.nanoTime()
                val delta = if (lastFrame == 0L) 0f else ((now - lastFrame) / 1_000_000_000f).coerceAtMost(0.1f)
                lastFrame = now
                if (motion) time += delta * if (playing) 1f else 0.1f
                val current = paletteAnimation.frame(delta, motion) ?: return
                GLES20.glViewport(0, 0, width, height)
                GLES20.glUseProgram(program)
                GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uTime"), time)
                GLES20.glUniform2f(GLES20.glGetUniformLocation(program, "uResolution"), width.toFloat(), height.toFloat())
                listOf("uColor1", "uColor2", "uColor3", "uBase").forEachIndexed { index, name ->
                    GLES20.glUniform3fv(GLES20.glGetUniformLocation(program, name), 1, current, index * 3)
                }
                val position = GLES20.glGetAttribLocation(program, "position")
                GLES20.glEnableVertexAttribArray(position)
                GLES20.glVertexAttribPointer(position, 2, GLES20.GL_FLOAT, false, 0, vertices)
                GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 3)
                val buffer = checkNotNull(pixels)
                buffer.rewind()
                GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer)
                check(GLES20.glGetError() == GLES20.GL_NO_ERROR) { "Cannot read living artwork frame" }
                buffer.rewind()
                // Never mutate published bitmaps: Compose and Haze may still be drawing them.
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bitmap.copyPixelsFromBuffer(buffer)
                val image = bitmap.asImageBitmap()
                pendingImage = image
                mainHandler.removeCallbacks(publishFrame)
                mainHandler.post(publishFrame)
                if (motion) handler.postDelayed(this, 34L)
            } catch (error: Exception) { fail(error) }
        }
    }

    fun update(
        colors: FloatArray?, outgoing: FloatArray?, transitionId: Long?, progress: Float,
        isPlaying: Boolean, started: Boolean, animations: Boolean, size: IntSize,
    ) {
        if (closed) return
        handler.post {
            if (failed || closed) return@post
            paletteAnimation.update(colors, outgoing, transitionId, progress)
            playing = isPlaying
            active = started
            motion = animations
            try {
                if (active && size.width > 0 && size.height > 0) {
                    resize((size.width * 0.15f).toInt().coerceAtLeast(1), (size.height * 0.15f).toInt().coerceAtLeast(1))
                }
                schedule()
            } catch (error: Exception) { fail(error) }
        }
    }

    private fun schedule() {
        if (active && !failed && program != 0) {
            if (!handler.hasCallbacks(frame)) handler.post(frame)
        } else {
            handler.removeCallbacks(frame)
            lastFrame = 0L
        }
    }

    private fun resize(newWidth: Int, newHeight: Int) {
        if (width == newWidth && height == newHeight && program != 0) return
        if (display == EGL14.EGL_NO_DISPLAY) {
            display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            check(EGL14.eglInitialize(display, IntArray(2), 0, IntArray(2), 0))
            val configs = arrayOfNulls<EGLConfig>(1)
            val count = IntArray(1)
            check(EGL14.eglChooseConfig(display, intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_NONE,
            ), 0, configs, 0, 1, count, 0) && count[0] > 0)
            config = configs[0]
            context = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)
            check(context != EGL14.EGL_NO_CONTEXT)
        }
        EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
        if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
        surface = EGL14.eglCreatePbufferSurface(display, config, intArrayOf(
            EGL14.EGL_WIDTH, newWidth, EGL14.EGL_HEIGHT, newHeight, EGL14.EGL_NONE,
        ), 0)
        check(surface != EGL14.EGL_NO_SURFACE && EGL14.eglMakeCurrent(display, surface, surface, context))
        width = newWidth
        height = newHeight
        pixels = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.nativeOrder())
        if (program == 0) {
            val vertex = compileShader(GLES20.GL_VERTEX_SHADER, "attribute vec2 position; void main() { gl_Position = vec4(position, 0.0, 1.0); }")
            try {
                val fragment = compileShader(GLES20.GL_FRAGMENT_SHADER, LivingArtworkFragmentShader)
                try {
                    program = GLES20.glCreateProgram()
                    GLES20.glAttachShader(program, vertex)
                    GLES20.glAttachShader(program, fragment)
                    GLES20.glLinkProgram(program)
                    val linked = IntArray(1)
                    GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linked, 0)
                    check(linked[0] != 0) { GLES20.glGetProgramInfoLog(program) }
                } finally { GLES20.glDeleteShader(fragment) }
            } finally { GLES20.glDeleteShader(vertex) }
        }
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val compiled = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == 0) {
            val message = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            error(message)
        }
        return shader
    }

    private fun fail(error: Exception) {
        android.util.Log.w("LivingArtwork", "Falling back to solid background", error)
        failed = true
        release()
        mainHandler.post { if (!closed) onFailure() }
    }

    private fun release() {
        handler.removeCallbacks(frame)
        if (display != EGL14.EGL_NO_DISPLAY) {
            if (program != 0) GLES20.glDeleteProgram(program)
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
            if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
            EGL14.eglTerminate(display)
            EGL14.eglReleaseThread()
        }
        program = 0
        display = EGL14.EGL_NO_DISPLAY
        context = EGL14.EGL_NO_CONTEXT
        surface = EGL14.EGL_NO_SURFACE
        pixels = null
        mainHandler.removeCallbacks(publishFrame)
        pendingImage = null
    }

    fun close() {
        if (closed) return
        closed = true
        handler.post { release(); thread.quitSafely() }
    }
}
