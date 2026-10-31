package me.misa198.airmedy.update

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal class GitHubAppReleaseSource(
    private val fetch: suspend (String) -> String = ::fetchReleaseJson,
) : AppReleaseSource {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun latestAndroidTag(): String =
        json.decodeFromString<ReleaseManifest>(fetch(ManifestUrl)).android.tag

    override suspend fun androidRelease(tag: String): AppUpdate? {
        require(tag.startsWith("mobile-v") && AppVersion.parse(tag.removePrefix("mobile-v")) != null)
        val release = json.decodeFromString<GitHubRelease>(fetch("$ReleasesUrl/$tag"))
        if (release.tag != tag || release.draft || release.publishedAt.isNullOrBlank()) return null
        if (release.assets.none { it.state == "uploaded" && it.name.endsWith(".apk") }) return null
        return AppUpdate(tag.removePrefix("mobile-v"), release.body.orEmpty(), "https://github.com/misa198/airmedy/releases/tag/$tag")
    }

    companion object {
        const val ManifestUrl = "https://raw.githubusercontent.com/misa198/airmedy/master/releases/latest-release.json"
        private const val ReleasesUrl = "https://api.github.com/repos/misa198/airmedy/releases/tags"
    }
}

@Serializable
private data class ReleaseManifest(val android: ReleasePointer)

@Serializable
private data class ReleasePointer(val tag: String)

@Serializable
private data class GitHubRelease(
    @SerialName("tag_name") val tag: String,
    val draft: Boolean,
    @SerialName("published_at") val publishedAt: String? = null,
    val body: String? = null,
    val assets: List<ReleaseAsset>,
)

@Serializable
private data class ReleaseAsset(val name: String, val state: String)

private suspend fun fetchReleaseJson(address: String): String = withContext(Dispatchers.IO) {
    ensureActive()
    val connection = (URL(address).openConnection() as HttpURLConnection).apply {
        connectTimeout = 10_000
        readTimeout = 10_000
        setRequestProperty("Accept", "application/vnd.github+json")
        setRequestProperty("User-Agent", "Airmedy-Android")
    }
    try {
        check(connection.responseCode == HttpURLConnection.HTTP_OK) { "Update check HTTP ${connection.responseCode}" }
        connection.inputStream.bufferedReader().use { it.readText() }.also { ensureActive() }
    } finally {
        connection.disconnect()
    }
}
