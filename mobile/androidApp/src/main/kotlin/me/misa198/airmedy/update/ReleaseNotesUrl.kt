package me.misa198.airmedy.update

import java.net.URI

internal fun releaseNotesUrl(destination: String, version: String, image: Boolean = false): String? =
    runCatching {
        val base = if (image) "https://raw.githubusercontent.com/misa198/airmedy/mobile-v$version/"
            else "https://github.com/misa198/airmedy/blob/mobile-v$version/"
        URI(base).resolve(destination).takeIf {
            it.scheme in setOf("http", "https") && !it.host.isNullOrBlank() && it.userInfo == null
        }?.toString()
    }.getOrNull()
