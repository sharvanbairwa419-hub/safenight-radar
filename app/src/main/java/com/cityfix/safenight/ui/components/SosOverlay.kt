package com.cityfix.safenight.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cityfix.safenight.sos.SosStep
import com.cityfix.safenight.sos.StepStatus
import com.cityfix.safenight.ui.theme.DangerRed
import com.cityfix.safenight.ui.theme.TextMuted
import com.cityfix.safenight.ui.theme.TextPrimary
import com.cityfix.safenight.ui.theme.ZoneModerate
import com.cityfix.safenight.ui.theme.ZoneSafe

/**
 * Full-screen SOS report. Blocks interaction with the map while the panic
 * pipeline runs, then stays until dismissed so the user can see what was sent.
 */
@Composable
fun SosOverlay(
    visible: Boolean,
    running: Boolean,
    steps: List<SosStep>,
    summary: String?,
    testMode: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "sos")
    val pulse by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "sos-pulse"
    )

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(180)),
        exit = fadeOut(tween(220)),
        modifier = modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.78f))
                // Swallow taps so the dock underneath cannot fire mid-SOS.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {},
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF1B0A10), Color(0xFF08050A))
                        )
                    )
                    .border(1.dp, DangerRed.copy(alpha = 0.45f), RoundedCornerShape(28.dp))
                    .padding(22.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(DangerRed.copy(alpha = 0.16f * pulse))
                            .border(1.dp, DangerRed.copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Call,
                            contentDescription = null,
                            tint = DangerRed,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(
                            text = if (running) "PANIC SOS ACTIVE" else "SOS REPORT",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            letterSpacing = 1.6.sp
                        )
                        Text(
                            text = if (testMode) {
                                "Hackathon test mode • test number only"
                            } else {
                                "Emergency line + instant GPS alert"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))

                if (steps.isEmpty()) {
                    Text(
                        text = "Preparing SOS…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted
                    )
                } else {
                    steps.forEach { step ->
                        SosStepRow(step)
                        Spacer(Modifier.height(10.dp))
                    }
                }

                summary?.let { text ->
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(6.dp))
                }

                Spacer(Modifier.height(14.dp))

                val buttonBrush = if (running) {
                    Brush.horizontalGradient(
                        listOf(Color.White.copy(alpha = 0.07f), Color.White.copy(alpha = 0.07f))
                    )
                } else {
                    Brush.horizontalGradient(listOf(Color(0xFFFF5567), Color(0xFFD6102B)))
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(buttonBrush)
                        .clickable(enabled = !running, onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (running) "WORKING…" else "DISMISS",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (running) TextMuted else Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun SosStepRow(step: SosStep) {
    val color = when (step.status) {
        StepStatus.PENDING -> TextMuted.copy(alpha = 0.55f)
        StepStatus.RUNNING -> ZoneModerate
        StepStatus.DONE -> ZoneSafe
        StepStatus.FAILED -> DangerRed
    }

    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .padding(top = 5.dp)
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = step.label,
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary
            )
            step.detail?.let { detail ->
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
        }
        Text(
            text = step.status.name,
            fontSize = 9.sp,
            letterSpacing = 1.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}
