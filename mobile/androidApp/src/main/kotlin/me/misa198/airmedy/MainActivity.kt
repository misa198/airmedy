package me.misa198.airmedy

import android.os.Bundle
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.tooling.preview.Preview
import android.view.WindowInsetsController
import me.misa198.airmedy.settings.ThemeMode
import me.misa198.airmedy.settings.ThemePreferences

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels {
        MainViewModel.Factory(ThemePreferences(applicationContext))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val darkTheme = isDarkTheme(uiState.themeMode)
            SideEffect {
                val lightSystemBars = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                    WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                window.insetsController?.setSystemBarsAppearance(
                    if (darkTheme) 0 else lightSystemBars,
                    lightSystemBars,
                )
            }
            App(
                uiState = uiState,
                onDestinationSelected = viewModel::selectDestination,
                onThemeModeSelected = viewModel::setThemeMode,
            )
        }
    }

    private fun isDarkTheme(themeMode: ThemeMode): Boolean = when (themeMode) {
        ThemeMode.System -> {
            resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        }
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
