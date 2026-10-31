package me.misa198.airmedy.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal class AppUpdateViewModel(
    private val checker: CheckForAppUpdate,
    private val installedVersion: String,
    private val logFailure: (Exception) -> Unit,
) : ViewModel() {
    private var started = false
    private val pending = MutableStateFlow<AppUpdate?>(null)
    val update = pending.asStateFlow()

    fun checkOnce() {
        if (started) return
        started = true
        viewModelScope.launch {
            try {
                val release = checker.check(installedVersion) ?: return@launch
                if (checker.markShown(release)) pending.value = release
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                logFailure(error)
            }
        }
    }

    fun dismiss() {
        pending.value = null
    }

    class Factory(
        private val checker: CheckForAppUpdate,
        private val installedVersion: String,
        private val logFailure: (Exception) -> Unit,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            check(modelClass.isAssignableFrom(AppUpdateViewModel::class.java))
            return AppUpdateViewModel(checker, installedVersion, logFailure) as T
        }
    }
}
