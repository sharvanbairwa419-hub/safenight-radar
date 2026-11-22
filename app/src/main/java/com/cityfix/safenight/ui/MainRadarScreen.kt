package com.cityfix.safenight.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cityfix.safenight.ui.components.ActionDock
import com.cityfix.safenight.ui.components.NightMapView
import com.cityfix.safenight.ui.components.RadarOverlay
import com.cityfix.safenight.ui.components.SosOverlay
import com.cityfix.safenight.ui.components.StatusPill
import com.cityfix.safenight.ui.theme.NightVoid
import com.cityfix.safenight.ui.theme.TextMuted
import com.cityfix.safenight.ui.theme.TextPrimary
import com.cityfix.safenight.ui.theme.ZoneModerate
import kotlinx.coroutines.flow.Flow
import org.osmdroid.views.MapView

/**
 * The single screen of SafeNight Radar.
 *
 * Layer order (bottom -> top):
 *  1. OSMDroid map with a night colour matrix
 *  2. Animated radar rings, sweep, signal dots and the live user dot
 *  3. Readability vignette
 *  4. Status pill + notice + action dock
 *  5. SOS report overlay
 */
@Composable
fun MainRadarScreen(
    state: RadarUiState,
    events: Flow<RadarEvent>,
    onActivate: () -> Unit,
    onStopRadar: () -> Unit,
    onSos: () -> Unit,
    onShare: () -> Unit,
    onToggleTestMode: (Boolean) -> Unit,
    onTestNumberChange: (String) -> Unit,
    onDismissSos: () -> Unit,
    onDismissNotice: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var mapView by remember { mutableStateOf<MapView?>(null) }

    LaunchedEffect(events) {
        events.collect { event ->
            when (event) {
                is RadarEvent.ShareRadar -> {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "SafeNight Radar — live location")
                        putExtra(Intent.EXTRA_TEXT, event.text)
                    }
                    try {
                        context.startActivity(
                            Intent.createChooser(send, "Share live radar")
                        )
                    } catch (_: ActivityNotFoundException) {
                        Toast.makeText(
                            context,
                            "No app available to share the radar.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }

    Box(modifier = modifier.fillMaxSize().background(NightVoid)) {

        NightMapView(
            userFix = state.userFix,
            followUser = state.isRadarActive,
            onMapReady = { mapView = it },
            modifier = Modifier.fillMaxSize()
        )

        RadarOverlay(
            mapView = mapView,
            userFix = state.userFix,
            signals = state.signals,
            zone = state.zone,
            active = state.isRadarActive,
            modifier = Modifier.fillMaxSize()
        )

        // Vignette keeps the pill and dock legible over bright map tiles.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to NightVoid.copy(alpha = 0.72f),
                        0.28f to Color.Transparent,
                        0.72f to Color.Transparent,
                        1f to NightVoid.copy(alpha = 0.92f)
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = "SAFENIGHT RADAR",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                letterSpacing = 3.sp
            )
            Spacer(Modifier.height(8.dp))

            StatusPill(
                zone = state.zone,
                signalCount = state.nearbyCount,
                active = state.isRadarActive
            )

            AnimatedVisibility(
                visible = state.notice != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                NoticeChip(
                    text = state.notice.orEmpty(),
                    onDismiss = onDismissNotice,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }

            Spacer(Modifier.weight(1f))

            ActionDock(
                state = state,
                onActivate = onActivate,
                onStopRadar = onStopRadar,
                onSos = onSos,
                onShare = onShare,
                onToggleTestMode = onToggleTestMode,
                onTestNumberChange = onTestNumberChange,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(14.dp))
        }

        SosOverlay(
            visible = state.sosRunning || state.sosSteps.isNotEmpty() || state.sosSummary != null,
            running = state.sosRunning,
            steps = state.sosSteps,
            summary = state.sosSummary,
            testMode = state.testMode,
            onDismiss = onDismissSos
        )
    }
}

@Composable
private fun NoticeChip(
    text: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xEE17203A))
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(14.dp))
            .clickable(onClick = onDismiss)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(ZoneModerate)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = TextPrimary,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.Filled.Close,
            contentDescription = "Dismiss",
            tint = TextMuted,
            modifier = Modifier.size(14.dp)
        )
    }
}
