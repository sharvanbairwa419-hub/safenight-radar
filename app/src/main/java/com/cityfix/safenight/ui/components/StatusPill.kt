package com.cityfix.safenight.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cityfix.safenight.radar.ZoneLevel
import com.cityfix.safenight.ui.theme.TextMuted
import com.cityfix.safenight.ui.zoneColor
import com.cityfix.safenight.ui.zoneSubtitle
import com.cityfix.safenight.ui.zoneTitle

/**
 * Floating glass pill with the live zone verdict.
 *
 * Glass effect: layered translucent white gradient over a coloured border,
 * plus a coloured shadow matching the zone so the state reads peripherally.
 */
@Composable
fun StatusPill(
    zone: ZoneLevel,
    signalCount: Int,
    active: Boolean,
    modifier: Modifier = Modifier
) {
    val accent by animateColorAsState(
        targetValue = zoneColor(zone),
        animationSpec = tween(700),
        label = "zone-colour"
    )

    val transition = rememberInfiniteTransition(label = "zone-blink")
    val blink by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Reverse),
        label = "blink"
    )

    val shape = RoundedCornerShape(50)
    val title = if (active) zoneTitle(zone) else "RADAR OFF"
    val subtitle = when {
        !active -> "TAP ACTIVATE TO SCAN YOUR SURROUNDINGS"
        else -> zoneSubtitle(zone, signalCount)
    }

    Row(
        modifier = modifier
            .shadow(
                elevation = 18.dp,
                shape = shape,
                ambientColor = accent.copy(alpha = 0.35f),
                spotColor = accent.copy(alpha = 0.35f)
            )
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.04f))
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(accent.copy(alpha = 0.45f), Color.White.copy(alpha = 0.06f))
                ),
                shape = shape
            )
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = if (active) blink else 0.35f))
        )
        Spacer(Modifier.width(11.dp))
        Column {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp,
                color = accent
            )
            Text(
                text = subtitle,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.9.sp,
                color = TextMuted
            )
        }
    }
}
