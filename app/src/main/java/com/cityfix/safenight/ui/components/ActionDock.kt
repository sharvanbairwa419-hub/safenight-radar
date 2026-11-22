package com.cityfix.safenight.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cityfix.safenight.ui.RadarUiState
import com.cityfix.safenight.ui.theme.DangerRed
import com.cityfix.safenight.ui.theme.PanelBottom
import com.cityfix.safenight.ui.theme.PanelTop
import com.cityfix.safenight.ui.theme.RadarTeal
import com.cityfix.safenight.ui.theme.TextMuted
import com.cityfix.safenight.ui.theme.TextPrimary
import com.cityfix.safenight.ui.theme.ZoneModerate

/**
 * Bottom dock. One primary action at a time keeps the night UI clutter-free:
 * inactive shows the big ACTIVATE RADAR button, active swaps it for PANIC SOS
 * plus a small pause control.
 */
@Composable
fun ActionDock(
    state: RadarUiState,
    onActivate: () -> Unit,
    onStopRadar: () -> Unit,
    onSos: () -> Unit,
    onShare: () -> Unit,
    onToggleTestMode: (Boolean) -> Unit,
    onTestNumberChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.verticalGradient(listOf(PanelTop, PanelBottom)))
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.04f))
                ),
                shape = RoundedCornerShape(28.dp)
            )
            .padding(horizontal = 14.dp, vertical = 14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ShareRadarChip(onClick = onShare, modifier = Modifier.weight(1f))
            TestModeToggle(checked = state.testMode, onCheckedChange = onToggleTestMode)
        }

        AnimatedVisibility(
            visible = state.testMode,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(Modifier.padding(top = 10.dp)) {
                OutlinedTextField(
                    value = state.testNumber,
                    onValueChange = onTestNumberChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Test phone number", fontSize = 12.sp) },
                    placeholder = {
                        Text(
                            "+91 90000 00000",
                            fontSize = 14.sp,
                            color = TextMuted.copy(alpha = 0.7f)
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Call,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    shape = RoundedCornerShape(14.dp),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = RadarTeal,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                        focusedContainerColor = Color.White.copy(alpha = 0.03f),
                        unfocusedContainerColor = Color.White.copy(alpha = 0.03f),
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = RadarTeal,
                        focusedLabelColor = RadarTeal,
                        unfocusedLabelColor = TextMuted
                    )
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "SOS will call and text this number instead of 112.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                    fontSize = 10.sp
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        if (state.isRadarActive) {
            SensorStrip(state)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlowActionButton(
                    text = "PANIC SOS",
                    caption = if (state.testMode) "TEST MODE • CALL + SMS" else "1-TAP CALL + GPS ALERT",
                    brush = Brush.horizontalGradient(
                        listOf(Color(0xFFFF5567), Color(0xFFD6102B))
                    ),
                    glowColor = DangerRed,
                    pulsing = true,
                    enabled = !state.sosRunning,
                    onClick = onSos,
                    modifier = Modifier.weight(1f)
                )
                StopRadarButton(onClick = onStopRadar)
            }
        } else {
            GlowActionButton(
                text = "ACTIVATE RADAR",
                caption = "PASSIVE BLE + WI-FI • NO LOGIN",
                brush = Brush.horizontalGradient(listOf(RadarTeal, Color(0xFF139C8B))),
                glowColor = RadarTeal,
                pulsing = true,
                onClick = onActivate,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Pieces
// ---------------------------------------------------------------------------

@Composable
private fun ShareRadarChip(onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            Color.White.copy(alpha = 0.14f)
        ),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.Share,
            contentDescription = null,
            tint = RadarTeal,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(7.dp))
        Text(
            text = "Share Live Radar",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

@Composable
private fun TestModeToggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .padding(start = 10.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "TEST",
            fontSize = 10.sp,
            letterSpacing = 1.2.sp,
            fontWeight = FontWeight.Bold,
            color = if (checked) ZoneModerate else TextMuted
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier
                .scale(0.72f)
                .width(40.dp),
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF07130F),
                checkedTrackColor = ZoneModerate,
                checkedBorderColor = ZoneModerate.copy(alpha = 0.6f),
                uncheckedThumbColor = TextMuted,
                uncheckedTrackColor = Color.White.copy(alpha = 0.12f),
                uncheckedBorderColor = Color.White.copy(alpha = 0.18f)
            )
        )
    }
}

@Composable
private fun SensorStrip(state: RadarUiState) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        SensorBadge(label = "BLE", active = state.bleScanning)
        SensorBadge(label = "WI-FI", active = state.wifiScanning)
        Spacer(Modifier.weight(1f))
        SensorBadge(label = "AUDIO 10s", active = state.audioBufferReady)
    }
}

@Composable
private fun SensorBadge(label: String, active: Boolean) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (active) RadarTeal.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.04f)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(if (active) RadarTeal else TextMuted.copy(alpha = 0.5f))
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            fontSize = 9.sp,
            letterSpacing = 1.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (active) RadarTeal else TextMuted
        )
    }
}

@Composable
private fun StopRadarButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Close,
            contentDescription = "Pause radar",
            tint = TextMuted,
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * Shared primary button: gradient fill, coloured glow that breathes, caption.
 */
@Composable
fun GlowActionButton(
    text: String,
    caption: String,
    brush: Brush,
    glowColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 64.dp,
    pulsing: Boolean = false,
    enabled: Boolean = true
) {
    val transition = rememberInfiniteTransition(label = "glow")
    val glow by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow-alpha"
    )

    Box(
        modifier = modifier
            .height(height)
            .shadow(
                elevation = 18.dp,
                shape = RoundedCornerShape(20.dp),
                ambientColor = glowColor.copy(alpha = if (pulsing) glow else 0.4f),
                spotColor = glowColor.copy(alpha = if (pulsing) glow else 0.4f)
            )
            .clip(RoundedCornerShape(20.dp))
            .background(brush)
            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(20.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = Color.White
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = caption,
                fontSize = 9.sp,
                letterSpacing = 1.1.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.78f)
            )
        }
    }
}
