package me.misa198.airmedy.update

class AppVersion private constructor(val major: Int, val minor: Int, val patch: Int) : Comparable<AppVersion> {
    override fun compareTo(other: AppVersion): Int =
        compareValuesBy(this, other, AppVersion::major, AppVersion::minor, AppVersion::patch)

    companion object {
        fun parse(value: String): AppVersion? {
            if (!Regex("[0-9]+\\.[0-9]+\\.[0-9]+").matches(value)) return null
            val parts = value.split('.').map { it.toIntOrNull() ?: return null }
            return AppVersion(parts[0], parts[1], parts[2])
        }
    }
}

data class AppUpdate(val version: String, val notes: String, val releaseUrl: String)

interface AppReleaseSource {
    suspend fun latestAndroidTag(): String
    suspend fun androidRelease(tag: String): AppUpdate?
}

interface UpdateNoticeStore {
    suspend fun wasShown(version: String): Boolean

    /** Atomically persists the version before presentation; false if already recorded. */
    suspend fun markShown(version: String): Boolean
}

class CheckForAppUpdate(
    private val releases: AppReleaseSource,
    private val notices: UpdateNoticeStore,
) {
    suspend fun check(installedVersion: String): AppUpdate? {
        val installed = AppVersion.parse(installedVersion) ?: return null
        val tag = releases.latestAndroidTag()
        if (!tag.startsWith("mobile-v")) return null
        val version = tag.removePrefix("mobile-v")
        val latest = AppVersion.parse(version) ?: return null
        if (latest <= installed || notices.wasShown(version)) return null
        return releases.androidRelease(tag)?.takeIf { it.version == version }
    }

    suspend fun markShown(update: AppUpdate): Boolean = notices.markShown(update.version)
}
