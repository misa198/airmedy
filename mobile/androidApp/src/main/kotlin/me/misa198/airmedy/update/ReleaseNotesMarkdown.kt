package me.misa198.airmedy.update

import android.net.Uri
import android.graphics.Rect
import android.util.TypedValue
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tables.TableTheme
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.ImageItem
import io.noties.markwon.image.ImagesPlugin
import io.noties.markwon.image.SchemeHandler
import io.noties.markwon.image.AsyncDrawable
import io.noties.markwon.image.ImageSizeResolverDef
import io.noties.markwon.image.destination.ImageDestinationProcessor
import java.io.FilterInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import me.misa198.airmedy.ui.theme.LocalAirmedyColors

@Composable
internal fun ReleaseNotesMarkdown(markdown: String, version: String, onOpenUrl: (String) -> Unit) {
    val context = LocalContext.current
    val colors = LocalAirmedyColors.current
    val openUrl by rememberUpdatedState(onOpenUrl)
    val executor = remember { Executors.newFixedThreadPool(2) }
    DisposableEffect(executor) {
        onDispose { executor.shutdownNow() }
    }
    val renderer = remember(context, colors, version) {
        Markwon.builder(context)
            .usePlugin(HtmlPlugin.create())
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TablePlugin.create(TableTheme.buildWithDefaults(context)
                .tableBorderColor(colors.borderGlass.toArgb())
                .tableOddRowBackgroundColor(colors.glass.toArgb())
                .tableEvenRowBackgroundColor(colors.card.toArgb())
                .tableHeaderRowBackgroundColor(colors.glassElevated.toArgb())
                .build()))
            .usePlugin(TaskListPlugin.create(colors.primary.toArgb(), colors.textMuted.toArgb(), colors.onPrimary.toArgb()))
            .usePlugin(ImagesPlugin.create { plugin ->
                plugin.executorService(executor)
                    .removeSchemeHandler("data")
                    .addSchemeHandler(ReleaseImageHandler())
            })
            .usePlugin(object : AbstractMarkwonPlugin() {
                override fun configureTheme(builder: MarkwonTheme.Builder) {
                    builder.linkColor(colors.primary.toArgb())
                        .codeTextColor(colors.textMain.toArgb())
                        .codeBlockTextColor(colors.textMain.toArgb())
                        .codeBackgroundColor(colors.glass.toArgb())
                        .codeBlockBackgroundColor(colors.glass.toArgb())
                        .blockQuoteColor(colors.borderGlass.toArgb())
                        .headingBreakColor(colors.borderGlass.toArgb())
                        .thematicBreakColor(colors.borderGlass.toArgb())
                }

                override fun configureConfiguration(builder: MarkwonConfiguration.Builder) {
                    builder.linkResolver { _, destination ->
                        releaseNotesUrl(destination, version)?.let(openUrl)
                    }.imageDestinationProcessor(object : ImageDestinationProcessor() {
                        override fun process(destination: String): String =
                            releaseNotesUrl(destination, version, image = true).orEmpty()
                    }).imageSizeResolver(object : ImageSizeResolverDef() {
                        override fun resolveImageSize(drawable: AsyncDrawable): Rect {
                            val bounds = super.resolveImageSize(drawable)
                            val width = drawable.lastKnownCanvasWidth
                            // GitHub's HTML images often specify desktop-sized pixel widths.
                            return if (width > 0 && bounds.width() > width) {
                                Rect(0, 0, width, (bounds.height().toLong() * width / bounds.width()).toInt())
                            } else bounds
                        }
                    })
                }
            })
            .build()
    }
    val rendered = remember(renderer, markdown) { renderer.toMarkdown(markdown) }
    val fontSize = MaterialTheme.typography.bodyMedium.fontSize.value
    AndroidView(
        modifier = Modifier.fillMaxWidth(),
        factory = { TextView(it) },
        update = { view ->
            view.setTextColor(colors.textMain.toArgb())
            view.setLinkTextColor(colors.primary.toArgb())
            view.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSize)
            if (view.tag !== rendered) {
                renderer.setParsedMarkdown(view, rendered)
                view.tag = rendered
            }
        },
        onRelease = { view ->
            // Markwon cancels image loads and table callbacks before replacing the text.
            renderer.setMarkdown(view, "")
        },
    )
}

/** Bounded network waits; closing Markwon's decode stream also releases the connection. */
private class ReleaseImageHandler : SchemeHandler() {
    override fun supportedSchemes() = listOf("http", "https")

    override fun handle(raw: String, uri: Uri): ImageItem {
        val connection = (URL(raw).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
        }
        try {
            check(connection.responseCode in 200..299) { "Release image HTTP ${connection.responseCode}" }
            val stream = object : FilterInputStream(connection.inputStream) {
                override fun close() {
                    try { super.close() } finally { connection.disconnect() }
                }
            }
            return ImageItem.withDecodingNeeded(connection.contentType?.substringBefore(';'), stream)
        } catch (error: Exception) {
            connection.disconnect()
            throw error
        }
    }
}
