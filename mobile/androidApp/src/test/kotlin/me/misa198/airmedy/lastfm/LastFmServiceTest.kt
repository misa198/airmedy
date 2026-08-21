package me.misa198.airmedy.lastfm

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LastFmServiceTest {
    @Test
    fun `signature sorts parameters and excludes response-only fields`() {
        val signature = lastFmSignature(
            mapOf(
                "token" to "token",
                "format" to "json",
                "method" to "auth.getSession",
                "api_key" to "key",
                "api_sig" to "ignored",
            ),
            "secret",
        )

        assertEquals("9ac306496295a8866c4a8673395540eb", signature)
    }

    @Test
    fun `auth callback requires the registered route and a token`() {
        assertTrue(isLastFmAuthCallback("airmedy", "lastfm", "/auth", "token"))
        assertFalse(isLastFmAuthCallback("https", "lastfm", "/auth", "token"))
        assertFalse(isLastFmAuthCallback("airmedy", "lastfm", "/auth", ""))
    }

    @Test
    fun `avatar selects the largest non-empty profile image`() {
        val response = Json.parseToJsonElement(
            """{"user":{"image":[{"#text":"","size":"small"},{"#text":"https://img/large.jpg","size":"large"},{"#text":"https://img/mega.jpg","size":"mega"}]}}""",
        ).jsonObject

        assertEquals("https://img/mega.jpg", lastFmAvatarUrl(response))
        assertEquals(null, lastFmAvatarUrl(Json.parseToJsonElement("""{"user":{"image":[]}}""").jsonObject))
    }
}
