package me.misa198.airmedy.player

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID
import kotlin.math.round

internal data class EqualizerPreset(val key: String, val name: String, val gainsDb: List<Float>)
internal data class EqualizerProfile(val key: String, val name: String, val gainsDb: List<Float>, val isDefault: Boolean)

internal val EqualizerFrequenciesHz = listOf(32, 64, 125, 250, 500, 1_000, 2_000, 4_000, 8_000, 16_000)

/** Desktop EQ preset catalog. Keep keys, order, and curves in sync with internal/app/eq. */
internal val EqualizerPresets = listOf(
    EqualizerPreset("flat", "Flat", listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)),
    EqualizerPreset("classical", "Classical", listOf(0f, 0f, 0f, 0f, 0f, 0f, -7f, -7f, -7f, -9.5f)),
    EqualizerPreset("club", "Club", listOf(0f, 0f, 8f, 5.5f, 5.5f, 5.5f, 3f, 0f, 0f, 0f)),
    EqualizerPreset("dance", "Dance", listOf(9.5f, 7f, 2.5f, 0f, 0f, -5.5f, -7f, -7f, 0f, 0f)),
    EqualizerPreset("full_bass", "Full Bass", listOf(-8f, 9.5f, 9.5f, 5.5f, 1.5f, -4f, -8f, -10.5f, -11f, -11f)),
    EqualizerPreset("full_treble", "Full Treble", listOf(-9.5f, -9.5f, -9.5f, -4f, 2.5f, 11f, 12f, 12f, 12f, 12f)),
    EqualizerPreset("full_bass_treble", "Full Bass & Treble", listOf(7f, 5.5f, 0f, -7f, -5f, 1.5f, 8f, 11f, 12f, 12f)),
    EqualizerPreset("headphones", "Headphones", listOf(5f, 11f, 5.5f, -3f, -2.5f, 1.5f, 5f, 9.5f, 12f, 12f)),
    EqualizerPreset("large_hall", "Large Hall", listOf(10.5f, 10.5f, 5.5f, 5.5f, 0f, -5f, -5f, -5f, 0f, 0f)),
    EqualizerPreset("live", "Live", listOf(-5f, 0f, 4f, 5.5f, 5.5f, 5.5f, 4f, 2.5f, 2.5f, 2.5f)),
    EqualizerPreset("party", "Party", listOf(7f, 7f, 0f, 0f, 0f, 0f, 0f, 0f, 7f, 7f)),
    EqualizerPreset("pop", "Pop", listOf(-1.5f, 5f, 7f, 8f, 5.5f, 0f, -2.5f, -2.5f, -1.5f, -1.5f)),
    EqualizerPreset("reggae", "Reggae", listOf(0f, 0f, 0f, -5.5f, 0f, 6.5f, 6.5f, 0f, 0f, 0f)),
    EqualizerPreset("rock", "Rock", listOf(8f, 5f, -5.5f, -8f, -3f, 4f, 9f, 11f, 11f, 11f)),
    EqualizerPreset("jazz", "Jazz", listOf(3f, 2f, 1f, 2f, -1f, -1f, 0f, 1f, 2f, 3f)),
    EqualizerPreset("hip_hop", "Hip-Hop", listOf(5f, 4f, 3f, 1f, -1f, -1f, 0f, -1f, 1f, 2f)),
    EqualizerPreset("ska", "Ska", listOf(-2.5f, -5f, -4f, 0f, 4f, 5.5f, 9f, 9.5f, 11f, 9.5f)),
    EqualizerPreset("soft", "Soft", listOf(5f, 1.5f, 0f, -2.5f, 0f, 4f, 8f, 9.5f, 11f, 12f)),
    EqualizerPreset("soft_rock", "Soft Rock", listOf(4f, 4f, 2.5f, 0f, -4f, -5.5f, -3f, 0f, 2.5f, 9f)),
    EqualizerPreset("techno", "Techno", listOf(8f, 5.5f, 0f, -5.5f, -5f, 0f, 8f, 9.5f, 9.5f, 9f)),
    EqualizerPreset("bass_booster", "Bass Booster", listOf(5.5f, 4.5f, 3f, 1.5f, 0f, 0f, 0f, 0f, 0f, 0f)),
    EqualizerPreset("treble_booster", "Treble Booster", listOf(0f, 0f, 0f, 0f, 0f, 0f, 1.5f, 3f, 4.5f, 5.5f)),
    EqualizerPreset("acoustic_vocal", "Acoustic / Vocal", listOf(2.5f, 1.5f, 0.5f, 0f, 1f, 2f, 2.5f, 2f, 1.5f, 1f)),
    EqualizerPreset("electronic_dance", "Electronic / Dance", listOf(4.5f, 3.5f, 1.5f, -0.5f, -1.5f, 0f, 1.5f, 2.5f, 3.5f, 4f)),
    EqualizerPreset("rnb_soul", "R&B / Soul", listOf(4.5f, 3.5f, 1.5f, 0f, 1f, 1.5f, 2f, 1.5f, 2.5f, 3f)),
    EqualizerPreset("vocal_booster", "Vocal Booster", listOf(-2f, -1f, 0f, 1f, 2f, 3f, 2.5f, 1.5f, 1f, 0f)),
    EqualizerPreset("loudness", "Loudness", listOf(5f, 3.5f, 1.5f, 0f, -1f, 0f, 1f, 2f, 3.5f, 4.5f)),
    EqualizerPreset("spoken_word_podcast", "Spoken Word / Podcast", listOf(-3f, -2f, -1f, 1f, 2.5f, 3.5f, 3f, 2f, 1f, 0f)),
    EqualizerPreset("harman_target", "Harman Target", listOf(3.5f, 3f, 1.5f, 0f, 0f, 0.5f, 1.5f, 2.5f, 3.5f, 4f)),
    EqualizerPreset("sony_excited", "Sony Excited", listOf(4f, 3f, 1f, 0f, 0f, 1f, 2f, 3f, 4f, 5f)),
    EqualizerPreset("sony_mellow", "Sony Mellow", listOf(2f, 1.5f, 1f, 0f, -1f, -1f, 0f, 1f, 1.5f, 2f)),
)

internal data class EqualizerSettings(
    val enabled: Boolean = false,
    val presetKey: String = "flat",
    val editedGainsDb: Map<String, List<Float>> = emptyMap(),
    val userProfiles: List<EqualizerProfile> = emptyList(),
    val legacyOverrideGainsDb: List<Float>? = null,
) {
    val profiles: List<EqualizerProfile> get() = EqualizerPresets.map { preset ->
        val gains = editedGainsDb[preset.key] ?: if (preset.key == presetKey) legacyOverrideGainsDb ?: preset.gainsDb else preset.gainsDb
        EqualizerProfile(preset.key, preset.name, gains, true)
    } + userProfiles
    val selectedProfile: EqualizerProfile get() = profiles.firstOrNull { it.key == presetKey } ?: profiles.first()
    val gainsDb: List<Float> get() = selectedProfile.gainsDb
}

private val EqualizerEnabledKey = booleanPreferencesKey("equalizer_enabled")
private val EqualizerPresetKey = stringPreferencesKey("equalizer_preset")
private val EqualizerOverrideKey = stringPreferencesKey("equalizer_override_gains")
private val EqualizerProfileEditsKey = stringPreferencesKey("equalizer_profile_edits")
private val EqualizerUserProfilesKey = stringPreferencesKey("equalizer_user_profiles")

internal class EqualizerPreferences(private val context: Context) {
    val settings: Flow<EqualizerSettings> = context.playbackPreferencesDataStore.data.map { values ->
        val userProfiles = parseUserProfiles(values[EqualizerUserProfilesKey])
        val preset = values[EqualizerPresetKey].orEmpty().takeIf { key -> EqualizerPresets.any { it.key == key } || userProfiles.any { it.key == key } } ?: "flat"
        EqualizerSettings(
            enabled = values[EqualizerEnabledKey] ?: false,
            presetKey = preset,
            editedGainsDb = parseProfileEdits(values[EqualizerProfileEditsKey]),
            userProfiles = userProfiles,
            legacyOverrideGainsDb = parseGains(values[EqualizerOverrideKey]),
        )
    }

    suspend fun setEnabled(enabled: Boolean) = context.playbackPreferencesDataStore.edit { it[EqualizerEnabledKey] = enabled }

    suspend fun selectPreset(key: String) = context.playbackPreferencesDataStore.edit { values ->
        val userProfiles = parseUserProfiles(values[EqualizerUserProfilesKey])
        val currentKey = values[EqualizerPresetKey].orEmpty().takeIf { it in EqualizerPresets.map(EqualizerPreset::key) || userProfiles.any { profile -> profile.key == it } } ?: "flat"
        val edits = parseProfileEdits(values[EqualizerProfileEditsKey]).toMutableMap()
        parseGains(values[EqualizerOverrideKey])?.let { edits[currentKey] = it }
        values[EqualizerProfileEditsKey] = encodeProfileEdits(edits)
        values[EqualizerPresetKey] = key.takeIf { it in EqualizerPresets.map(EqualizerPreset::key) || userProfiles.any { profile -> profile.key == it } } ?: "flat"
        values.remove(EqualizerOverrideKey)
    }

    suspend fun setBand(settings: EqualizerSettings, index: Int, gainDb: Float) {
        if (index !in 0 until EqualizerFrequenciesHz.size) return
        context.playbackPreferencesDataStore.edit { values ->
            val userProfiles = parseUserProfiles(values[EqualizerUserProfilesKey])
            val selectedUserProfile = userProfiles.firstOrNull { it.key == settings.presetKey }
            if (selectedUserProfile != null) {
                val gains = selectedUserProfile.gainsDb.toMutableList().also { it[index] = normalizeEqGain(gainDb) }
                values[EqualizerUserProfilesKey] = encodeUserProfiles(userProfiles.map { profile ->
                    if (profile.key == selectedUserProfile.key) profile.copy(gainsDb = gains) else profile
                })
                values[EqualizerPresetKey] = selectedUserProfile.key
                values.remove(EqualizerOverrideKey)
                return@edit
            }
            val edits = parseProfileEdits(values[EqualizerProfileEditsKey]).toMutableMap()
            val gains = (edits[settings.presetKey] ?: settings.gainsDb).toMutableList()
            gains[index] = normalizeEqGain(gainDb)
            values[EqualizerPresetKey] = presetFor(settings.presetKey).key
            edits[settings.presetKey] = gains
            values[EqualizerProfileEditsKey] = encodeProfileEdits(edits)
            values.remove(EqualizerOverrideKey)
        }
    }

    suspend fun createProfile(name: String) {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) return
        context.playbackPreferencesDataStore.edit { values ->
            val profile = EqualizerProfile("user_${UUID.randomUUID()}", trimmedName, List(EqualizerFrequenciesHz.size) { 0f }, false)
            values[EqualizerUserProfilesKey] = encodeUserProfiles(parseUserProfiles(values[EqualizerUserProfilesKey]) + profile)
            values[EqualizerPresetKey] = profile.key
            values.remove(EqualizerOverrideKey)
        }
    }

    suspend fun resetDefault(key: String) = context.playbackPreferencesDataStore.edit { values ->
        if (EqualizerPresets.none { it.key == key }) return@edit
        val edits = parseProfileEdits(values[EqualizerProfileEditsKey]).toMutableMap()
        edits.remove(key)
        values[EqualizerProfileEditsKey] = encodeProfileEdits(edits)
        if (values[EqualizerPresetKey] == key) values.remove(EqualizerOverrideKey)
    }

    suspend fun deleteProfile(key: String) = context.playbackPreferencesDataStore.edit { values ->
        if (EqualizerPresets.any { it.key == key }) return@edit
        values[EqualizerUserProfilesKey] = encodeUserProfiles(parseUserProfiles(values[EqualizerUserProfilesKey]).filterNot { it.key == key })
        if (values[EqualizerPresetKey] == key) values[EqualizerPresetKey] = "flat"
    }
}

internal fun presetFor(key: String): EqualizerPreset = EqualizerPresets.firstOrNull { it.key == key } ?: EqualizerPresets.first()
internal fun normalizeEqGain(gainDb: Float): Float = (round(gainDb.coerceIn(-12f, 12f) * 2f) / 2f).let { if (it == 0f) 0f else it }
internal fun equalizerDspConfig(settings: EqualizerSettings) = GlobalDspConfig(
    eqBandGainsDb = (if (settings.enabled) settings.gainsDb else List(EqualizerFrequenciesHz.size) { 0f }).toFloatArray(),
)

private fun parseGains(value: String?): List<Float>? = value?.split(',')?.mapNotNull(String::toFloatOrNull)
    ?.takeIf { it.size == EqualizerFrequenciesHz.size }?.map(::normalizeEqGain)

private fun parseProfileEdits(value: String?): Map<String, List<Float>> = value.orEmpty().split(';').mapNotNull { entry ->
    val (key, gains) = entry.split('=', limit = 2).let { it.firstOrNull().orEmpty() to it.getOrNull(1) }
    parseGains(gains)?.let { key to it }
}.filter { (key, _) -> EqualizerPresets.any { it.key == key } }.toMap()

private fun encodeProfileEdits(edits: Map<String, List<Float>>): String = edits.entries
    .filter { (key, gains) -> EqualizerPresets.any { it.key == key } && gains.size == EqualizerFrequenciesHz.size }
    .joinToString(";") { (key, gains) -> "$key=${gains.joinToString(",")}" }

internal fun parseUserProfiles(value: String?): List<EqualizerProfile> = runCatching {
    Json.parseToJsonElement(value.orEmpty()).jsonArray.mapNotNull { element ->
        val profile = element.jsonObject
        val key = profile["key"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val name = profile["name"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        val gains = profile["gains"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull?.toFloatOrNull() }
            ?.takeIf { it.size == EqualizerFrequenciesHz.size }?.map(::normalizeEqGain)
        EqualizerProfile(key, name, gains.orEmpty(), false).takeIf { key.startsWith("user_") && name.isNotEmpty() && gains != null }
    }
}.getOrDefault(emptyList())

internal fun encodeUserProfiles(profiles: List<EqualizerProfile>): String = JsonArray(profiles.map { profile ->
    JsonObject(mapOf(
        "key" to JsonPrimitive(profile.key),
        "name" to JsonPrimitive(profile.name),
        "gains" to JsonArray(profile.gainsDb.map(::JsonPrimitive)),
    ))
}).toString()
