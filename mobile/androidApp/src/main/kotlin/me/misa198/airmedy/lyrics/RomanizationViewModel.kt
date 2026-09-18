package me.misa198.airmedy.lyrics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class RomanizationUiState(
    val input: List<String> = emptyList(),
    val supported: Boolean = false,
    val enabled: Boolean = false,
    val loading: Boolean = false,
    val error: Boolean = false,
    val mandarinDefault: Boolean = false,
    val secondary: List<String?> = emptyList(),
)

/** Only the preference survives Activity recreation; no lyric text is retained globally. */
internal object RomanizationSession { var enabled = false }

internal class RomanizationViewModel(
    private val romanize: RomanizeLyrics,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val mutableState = MutableStateFlow(RomanizationUiState(enabled = RomanizationSession.enabled))
    val state = mutableState.asStateFlow()
    private var input: List<String> = emptyList()
    private var active = false
    private var allowed = true
    private var generation = 0L
    private var conversion: Job? = null
    private var idle: Job? = null

    fun setInput(lines: List<String>, visible: Boolean) {
        if (input == lines && active == visible) return
        input = lines.toList()
        active = visible
        refresh()
    }

    fun toggle() {
        if (!allowed) return
        RomanizationSession.enabled = !mutableState.value.enabled
        mutableState.value = mutableState.value.copy(enabled = RomanizationSession.enabled)
        refresh()
    }

    fun setAllowed(value: Boolean) {
        if (allowed == value) return
        allowed = value
        if (!allowed) {
            generation++
            conversion?.cancel()
            RomanizationSession.enabled = false
            mutableState.value = RomanizationUiState(input = input)
            scheduleRelease()
        } else {
            refresh()
        }
    }

    private fun refresh() {
        if (!allowed) return
        val request = ++generation
        conversion?.cancel()
        idle?.cancel()
        val lines = input
        val enabled = mutableState.value.enabled
        val previous = mutableState.value
        val sameInput = active && previous.input == lines
        mutableState.value = RomanizationUiState(
            input = lines, enabled = enabled,
            supported = sameInput && previous.supported,
            mandarinDefault = sameInput && previous.mandarinDefault,
            loading = sameInput && previous.supported && enabled,
        )
        if (!active) {
            scheduleRelease()
            return
        }
        conversion = viewModelScope.launch {
            try {
                val inspection = withContext(dispatcher) { romanize.inspect(lines) }
                if (request != generation) return@launch
                mutableState.value = mutableState.value.copy(
                    supported = inspection.supported,
                    mandarinDefault = inspection.mandarinDefault,
                    loading = enabled && inspection.supported,
                )
                if (enabled && inspection.supported) {
                    val result = withContext(dispatcher) { romanize(lines) }
                    if (request != generation) return@launch
                    mutableState.value = mutableState.value.copy(
                        loading = false,
                        error = result.any { it.status == RomanizedStatus.Failed },
                        secondary = result.map { line -> line.text.takeIf { line.status == RomanizedStatus.Converted } },
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (request == generation) mutableState.value = mutableState.value.copy(loading = false, error = true)
            } finally {
                if (request == generation) scheduleRelease()
            }
        }
    }

    private fun scheduleRelease() {
        idle?.cancel()
        idle = viewModelScope.launch {
            delay(120_000)
            withContext(dispatcher) { romanize.release() }
        }
    }

    class Factory(private val engine: RomanizationEngine) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = RomanizationViewModel(RomanizeLyrics(engine)) as T
    }
}
