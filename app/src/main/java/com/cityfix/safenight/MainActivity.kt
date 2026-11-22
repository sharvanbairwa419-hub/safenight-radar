package com.cityfix.safenight

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cityfix.safenight.ui.MainRadarScreen
import com.cityfix.safenight.ui.RadarViewModel
import com.cityfix.safenight.ui.theme.SafeNightTheme
import com.cityfix.safenight.util.PermissionUtils

/**
 * Single-activity host. No onboarding, no login: the map is on screen
 * immediately and permissions are requested at the moment they matter
 * (first radar activation), not on launch.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val nightArgb = 0xFF05070D.toInt()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(nightArgb),
            navigationBarStyle = SystemBarStyle.dark(nightArgb)
        )

        setContent {
            SafeNightTheme {
                val radarViewModel: RadarViewModel = viewModel()
                val state by radarViewModel.state.collectAsStateWithLifecycle()

                // One dialog chain covering location, SOS and ambient radio.
                val permissionsLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { result ->
                    radarViewModel.onPermissionResult(result)
                }

                LaunchedEffect(Unit) {
                    radarViewModel.syncPermissions(PermissionUtils.hasLocation(this@MainActivity))
                }

                MainRadarScreen(
                    state = state,
                    events = radarViewModel.events,
                    onActivate = {
                        if (PermissionUtils.hasLocation(this@MainActivity)) {
                            radarViewModel.startRadar()
                        } else {
                            permissionsLauncher.launch(
                                PermissionUtils.requiredRuntimePermissions()
                            )
                        }
                    },
                    onStopRadar = radarViewModel::stopRadar,
                    onSos = radarViewModel::triggerSos,
                    onShare = radarViewModel::shareRadar,
                    onToggleTestMode = radarViewModel::setTestMode,
                    onTestNumberChange = radarViewModel::setTestNumber,
                    onDismissSos = radarViewModel::dismissSos,
                    onDismissNotice = radarViewModel::dismissNotice
                )
            }
        }
    }
}
