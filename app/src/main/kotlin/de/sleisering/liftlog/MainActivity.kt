package de.sleisering.liftlog

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import de.sleisering.liftlog.ui.LiftLogApp
import de.sleisering.liftlog.ui.theme.LiftLogTheme
import de.sleisering.liftlog.ui.theme.resolveDarkTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themePreference by viewModel.themePreference.collectAsStateWithLifecycle()
            val darkTheme = resolveDarkTheme(themePreference, isSystemInDarkTheme())
            // System-bar icon contrast must follow the manual theme override:
            // the default enableEdgeToEdge() detectDarkMode reads only the
            // resource configuration (device setting). Safe to re-invoke.
            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(
                        Color.TRANSPARENT, Color.TRANSPARENT,
                    ) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(
                        LIGHT_NAV_SCRIM, DARK_NAV_SCRIM,
                    ) { darkTheme },
                )
                onDispose {}
            }
            LiftLogTheme(themePreference = themePreference) {
                LiftLogApp()
            }
        }
    }

    private companion object {
        // androidx.activity's default scrims (private in the library).
        // Inert on minSdk 31: SystemBarStyle.auto resolves to transparent on
        // API 29+ and the system enforces button-nav contrast itself; kept to
        // match the canonical edge-to-edge pattern.
        val LIGHT_NAV_SCRIM: Int = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        val DARK_NAV_SCRIM: Int = Color.argb(0x80, 0x1B, 0x1B, 0x1B)
    }
}
