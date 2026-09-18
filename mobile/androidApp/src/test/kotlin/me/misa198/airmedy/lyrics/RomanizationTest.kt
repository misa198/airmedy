package me.misa198.airmedy.lyrics

import androidx.lifecycle.ViewModelStore
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RomanizationTest {
    @Test
    fun offlineDesktopFixtures() = runTest {
        var opens = 0
        val engine = AndroidRomanizationEngine { path ->
            opens++
            val file = if (path.endsWith("characters.tsv")) File("src/main/assets/$path")
            else File("../../internal/infra/romanization/data/${path.substringAfterLast('/')}")
            file.inputStream()
        }
        val service = RomanizeLyrics(engine)
        assertTrue(service.inspect(listOf("你好", "한글")).supported)
        assertEquals(0, opens)
        val fixtures = mapOf(
            "重庆银行" to "chóng qìng yín háng", "重慶銀行" to "chóng qìng yín háng",
            "Hello你好" to "Hello nǐ hǎo", "你好Hello" to "nǐ hǎo Hello",
            "안녕하세요, Hello!" to "annyeonghaseyo, Hello!", "같이 꽃잎" to "gachi kkonnip",
            "世界" to "shì jiè", "Hello 你好、한글!" to "Hello nǐ hǎo、hangeul!",
            "한글" to "hangeul", "𠀀" to "hē",
        )
        assertEquals(fixtures.values.toList(), service(fixtures.keys.toList()).map { it.text })
        assertEquals(3, opens)
        assertFalse(service.inspect(listOf("世界は美しい", "한글カナ", "Hello 😀")).supported)
        assertEquals(RomanizedStatus.Failed, service(listOf("㐂")).single().status)
        service.release()
        service(listOf("你好"))
        assertEquals(6, opens)
    }

    @Test
    fun toggleCancellationAndSessionPreference() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        RomanizationSession.enabled = false
        val store = ViewModelStore()
        var releases = 0
        val old = CompletableDeferred<String>()
        val vm = RomanizationViewModel(RomanizeLyrics(object : RomanizationEngine {
            override val version = "test"
            override fun inspect(line: String) = RomanizationInspection(true)
            override suspend fun romanize(line: String) = if (line == "old") old.await() else "new reading"
            override fun release() { releases++ }
        }), dispatcher)
        store.put("romanization", vm)
        try {
            vm.setInput(listOf("old"), true)
            runCurrent()
            assertFalse(vm.state.value.enabled)
            vm.toggle()
            runCurrent()
            assertTrue(vm.state.value.loading)
            vm.setInput(listOf("new"), true)
            runCurrent()
            old.complete("old reading")
            runCurrent()
            assertEquals(listOf("new reading"), vm.state.value.secondary)
            vm.toggle()
            runCurrent()
            assertTrue(vm.state.value.secondary.isEmpty())
            vm.toggle()
            runCurrent()
            vm.setInput(emptyList(), false)
            advanceTimeBy(120_000)
            runCurrent()
            assertEquals(1, releases)
            assertTrue(RomanizationSession.enabled)
            vm.setInput(listOf("new"), true)
            runCurrent()
            assertEquals(listOf("new reading"), vm.state.value.secondary)
        } finally {
            store.clear()
            RomanizationSession.enabled = false
            Dispatchers.resetMain()
        }
    }

    @Test
    fun disabledGatePreventsRomanization() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val store = ViewModelStore()
        val vm = RomanizationViewModel(RomanizeLyrics(object : RomanizationEngine {
            override val version = "test"
            override fun inspect(line: String) = RomanizationInspection(true)
            override suspend fun romanize(line: String) = "reading"
            override fun release() = Unit
        }), dispatcher)
        store.put("romanization", vm)
        try {
            vm.setInput(listOf("你好"), true)
            runCurrent()
            vm.toggle()
            runCurrent()
            assertTrue(vm.state.value.enabled)
            vm.setAllowed(false)
            assertFalse(vm.state.value.enabled)
            assertFalse(vm.state.value.loading)
            assertTrue(vm.state.value.secondary.isEmpty())
            vm.toggle()
            assertFalse(vm.state.value.enabled)
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }
}
