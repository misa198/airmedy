package me.misa198.airmedy.lyrics

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.map

private val Context.lyricsDataStore by preferencesDataStore("lyrics")
private val LrclibKey = booleanPreferencesKey("enable_lrclib")
private val KugouKey = booleanPreferencesKey("enable_kugou")
private val PreferredSourceKey = stringPreferencesKey("preferred_source")

internal enum class LyricsSource(val storageValue: String) {
    Desktop("desktop"),
    AutoFetch("auto_fetch"),
    ;

    companion object {
        fun fromStorage(value: String?) = entries.firstOrNull { it.storageValue == value } ?: Desktop
    }
}

internal data class LyricsSettings(
    val preferredSource: LyricsSource = LyricsSource.Desktop,
    val lrclib: Boolean = true,
    val kugou: Boolean = true,
)

internal fun preferredLyrics(source: LyricsSource, desktop: String?, provider: String?): String? = when (source) {
    LyricsSource.Desktop -> desktop ?: provider
    LyricsSource.AutoFetch -> provider ?: desktop
}

internal class LyricsPreferences(private val context: Context) {
    val settings = context.lyricsDataStore.data.map {
        LyricsSettings(
            preferredSource = LyricsSource.fromStorage(it[PreferredSourceKey]),
            lrclib = it[LrclibKey] ?: true,
            kugou = it[KugouKey] ?: true,
        )
    }
    suspend fun setPreferredSource(source: LyricsSource) = context.lyricsDataStore.edit { it[PreferredSourceKey] = source.storageValue }
    suspend fun setLrclib(enabled: Boolean) = context.lyricsDataStore.edit { it[LrclibKey] = enabled }
    suspend fun setKugou(enabled: Boolean) = context.lyricsDataStore.edit { it[KugouKey] = enabled }
}
