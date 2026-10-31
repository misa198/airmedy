package me.misa198.airmedy.update

import android.graphics.Bitmap
import android.text.Spanned
import android.text.style.StyleSpan
import android.widget.TextView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.matcher.ViewMatchers.withText
import io.noties.markwon.core.spans.HeadingSpan
import io.noties.markwon.core.spans.LinkSpan
import io.noties.markwon.image.AsyncDrawableSpan
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.Executors
import me.misa198.airmedy.settings.ThemeMode
import me.misa198.airmedy.ui.theme.AirmedyColors
import me.misa198.airmedy.ui.theme.AirmedyTheme
import me.misa198.airmedy.ui.theme.LocalAirmedyColors
import org.hamcrest.Matchers.containsString
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ReleaseNotesMarkdownTest {
    @get:Rule val compose = createComposeRule()

    @Test fun rendersMarkdownAndHtmlImagesWithThemeChangesAndClickableLinks() {
        ImageServer().use { server ->
            var mode by mutableStateOf(ThemeMode.Light)
            var visible by mutableStateOf(true)
            var opened: String? = null
            lateinit var colors: AirmedyColors
            compose.setContent {
                AirmedyTheme(mode) {
                    colors = LocalAirmedyColors.current
                    if (visible) AppUpdateDialog(
                        AppUpdate("2.0.0", """
                            ## New features

                            **Bold** and *italic*, ~~removed~~ and `code`.

                            - List item
                            - [x] Completed

                            [Details](https://example.com/notes)

                            ![Artwork](${server.url}/image.png)

                            <img src="${server.url}/image.png" width="1200" alt="Preview">

                            ![Vector](${server.url}/image.svg)

                            ![Unavailable](${server.url}/missing.png)
                        """.trimIndent(), "https://github.com/misa198/airmedy/releases/tag/mobile-v2.0.0"),
                        { visible = false }, { opened = it },
                    )
                }
            }
            lateinit var notesView: TextView
            onView(withText(containsString("New features"))).check { view, error ->
                if (error != null) throw error
                notesView = view as TextView
                val text = view.text as Spanned
                assertFalse(text.toString().contains("##"))
                assertFalse(text.toString().contains("**Bold**"))
                assertEquals(1, text.getSpans(0, text.length, HeadingSpan::class.java).size)
                assertTrue(text.getSpans(0, text.length, StyleSpan::class.java).size >= 2)
                assertEquals(4, text.getSpans(0, text.length, AsyncDrawableSpan::class.java).size)
                assertEquals(colors.textMain.toArgb(), view.currentTextColor)
                assertEquals(colors.primary.toArgb(), view.linkTextColors.defaultColor)
                text.getSpans(0, text.length, LinkSpan::class.java).single().onClick(view)
            }
            assertEquals("https://example.com/notes", opened)
            compose.waitUntil(timeoutMillis = 5_000) {
                var loaded = false
                compose.runOnIdle {
                    val text = notesView.text as Spanned
                    loaded = text.getSpans(0, text.length, AsyncDrawableSpan::class.java)
                        .count { it.drawable.hasResult() } == 3
                }
                loaded
            }
            compose.runOnIdle {
                val text = notesView.text as Spanned
                text.getSpans(0, text.length, AsyncDrawableSpan::class.java)
                    .map { it.drawable }.filter { it.hasResult() }.forEach { drawable ->
                        drawable.initWithKnownDimensions(notesView.width, notesView.textSize)
                        assertTrue(drawable.bounds.width() <= notesView.width)
                    }
            }
            compose.runOnIdle { mode = ThemeMode.Dark }
            onView(withText(containsString("New features"))).check { view, _ ->
                assertEquals(colors.textMain.toArgb(), (view as TextView).currentTextColor)
                assertEquals(colors.primary.toArgb(), view.linkTextColors.defaultColor)
            }
            compose.runOnIdle { visible = false }
            onView(withText(containsString("New features"))).check(doesNotExist())
        }
    }
}

/** Local deterministic images; UI tests never depend on GitHub or an external image host. */
private class ImageServer : AutoCloseable {
    private val socket = ServerSocket(0, 0, InetAddress.getByName("127.0.0.1"))
    private val executor = Executors.newSingleThreadExecutor()
    val url = "http://127.0.0.1:${socket.localPort}"

    init {
        val bitmap = Bitmap.createBitmap(1200, 600, Bitmap.Config.ARGB_8888)
        val image = ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            bitmap.recycle()
            output.toByteArray()
        }
        executor.submit {
            while (!socket.isClosed) {
                val client = try { socket.accept() } catch (_: java.io.IOException) { break }
                client.use {
                    it.soTimeout = 2_000
                    val reader = it.getInputStream().bufferedReader()
                    val request = reader.readLine().orEmpty()
                    while (!reader.readLine().isNullOrEmpty()) { }
                    val svg = request.startsWith("GET /image.svg ")
                    val success = svg || request.startsWith("GET /image.png ")
                    val body = when {
                        svg -> """<svg xmlns="http://www.w3.org/2000/svg" width="200" height="100"><rect width="200" height="100"/></svg>""".toByteArray()
                        success -> image
                        else -> byteArrayOf()
                    }
                    val status = if (success) "200 OK" else "404 Not Found"
                    val type = if (svg) "image/svg+xml" else "image/png"
                    it.getOutputStream().apply {
                        write("HTTP/1.1 $status\r\nContent-Type: $type\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
                        write(body)
                        flush()
                    }
                }
            }
        }
    }

    override fun close() {
        socket.close()
        executor.shutdownNow()
    }
}
