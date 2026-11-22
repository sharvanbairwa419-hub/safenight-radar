package com.cityfix.safenight.ui.components

import android.graphics.Point
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.cityfix.safenight.location.UserFix
import com.cityfix.safenight.radar.AmbientSignal
import com.cityfix.safenight.radar.SignalKind
import com.cityfix.safenight.radar.ZoneLevel
import com.cityfix.safenight.ui.theme.BleGlow
import com.cityfix.safenight.ui.theme.UserBlue
import com.cityfix.safenight.ui.theme.WifiGlow
import com.cityfix.safenight.ui.zoneColor
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Radar layers drawn in screen space above the map.
 *
 * Ring radii come straight from the live map projection each frame, so the
 * rings always represent 15 m and 50 m at the current zoom instead of being
 * decorative circles. Signals are placed by their (unknown-direction)
 * pseudorandom angle and their measured radius.
 */
@Composable
fun RadarOverlay(
    mapView: MapView?,
    userFix: UserFix?,
    signals: List<AmbientSignal>,
    zone: ZoneLevel,
    active: Boolean,
    modifier: Modifier = Modifier
) {
    if (!active) return

    val transition = rememberInfiniteTransition(label = "radar")
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(4200, easing = LinearEasing), RepeatMode.Restart),
        label = "sweep"
    )
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2800, easing = LinearEasing), RepeatMode.Restart),
        label = "pulse"
    )

    val accent = zoneColor(zone)
    val centrePoint = remember { Point() }
    val northPoint = remember { Point() }

    Canvas(modifier) {
        val projection = mapView?.projection
        val lat = userFix?.latitude
        val lon = userFix?.longitude

        val centre: Offset
        val inner: Float
        val outer: Float

        if (projection != null && lat != null && lon != null) {
            val anchor = projection.toPixels(GeoPoint(lat, lon), centrePoint)
            centre = Offset(anchor.x.toFloat(), anchor.y.toFloat())
            inner = abs(
                projection.toPixels(
                    GeoPoint(lat + INNER_RING_M / METERS_PER_DEGREE_LAT, lon),
                    northPoint
                ).y - anchor.y
            ).toFloat()
            outer = abs(
                projection.toPixels(
                    GeoPoint(lat + OUTER_RING_M / METERS_PER_DEGREE_LAT, lon),
                    northPoint
                ).y - anchor.y
            ).toFloat()
        } else {
            centre = Offset(size.width / 2f, size.height / 2f)
            inner = size.minDimension * 0.20f
            outer = size.minDimension * 0.40f
        }

        if (inner <= 1f || outer <= 1f) return@Canvas

        // Soft ground glow between the rings.
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    accent.copy(alpha = 0.16f),
                    accent.copy(alpha = 0.04f),
                    Color.Transparent
                ),
                center = centre,
                radius = outer
            ),
            radius = outer,
            center = centre
        )

        // Expanding echo: the "nothing is hidden" pulse.
        val echoRadius = outer * (0.15f + 0.85f * pulse)
        drawCircle(
            color = accent.copy(alpha = (1f - pulse) * 0.22f),
            radius = echoRadius,
            center = centre,
            style = Stroke(width = 1.5.dp.toPx())
        )

        // Inner ring: 0-15 m.
        drawCircle(
            color = accent.copy(alpha = 0.55f),
            radius = inner,
            center = centre,
            style = Stroke(width = 1.4.dp.toPx())
        )

        // Outer ring: 15-50 m.
        drawCircle(
            color = accent.copy(alpha = 0.32f),
            radius = outer,
            center = centre,
            style = Stroke(
                width = 1.2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(
                    floatArrayOf(9.dp.toPx(), 9.dp.toPx())
                )
            )
        )

        // Sweeping sonar beam.
        rotate(degrees = sweep, pivot = centre) {
            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(accent.copy(alpha = 0f), accent.copy(alpha = 0.28f)),
                    center = centre
                ),
                startAngle = -32f,
                sweepAngle = 32f,
                useCenter = true,
                topLeft = Offset(centre.x - outer, centre.y - outer),
                size = Size(outer * 2f, outer * 2f)
            )
        }

        // Ambient signals: pulsing glow dots on their distance ring.
        signals.take(MAX_DOTS).forEach { signal ->
            val radius = if (signal.distanceMeters <= INNER_RING_M) {
                inner
            } else {
                val t = ((signal.distanceMeters - INNER_RING_M) /
                    (OUTER_RING_M - INNER_RING_M)).coerceIn(0f, 1f)
                inner + (outer - inner) * t
            }

            val angle = signal.angleTurn * TWO_PI
            val breathing = 1f + 0.03f * sin(pulse * TWO_PI + signal.angleTurn * 9f)
            val ringRadius = radius * breathing
            val position = Offset(
                centre.x + cos(angle) * ringRadius,
                centre.y + sin(angle) * ringRadius
            )
            val twinkle = 0.55f + 0.45f * ((sin(pulse * TWO_PI * 1.7f + signal.angleTurn * 11f) + 1f) / 2f)
            val dotColor = if (signal.kind == SignalKind.BLE) BleGlow else WifiGlow

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(dotColor.copy(alpha = 0.42f * twinkle), Color.Transparent),
                    center = position,
                    radius = 13.dp.toPx()
                ),
                radius = 13.dp.toPx(),
                center = position
            )
            drawCircle(color = dotColor.copy(alpha = 0.95f * twinkle), radius = 3.4.dp.toPx(), center = position)
            drawCircle(color = Color.White.copy(alpha = 0.85f * twinkle), radius = 1.3.dp.toPx(), center = position)
        }

        // Live user dot sits exactly where the map projects the GPS fix.
        drawCircle(color = UserBlue.copy(alpha = 0.18f), radius = 18.dp.toPx(), center = centre)
        drawCircle(color = UserBlue.copy(alpha = 0.32f), radius = 11.dp.toPx(), center = centre)
        drawCircle(color = Color.White, radius = 6.4.dp.toPx(), center = centre)
        drawCircle(color = UserBlue, radius = 4.6.dp.toPx(), center = centre)
    }
}

private const val INNER_RING_M = 15f
private const val OUTER_RING_M = 50f
private const val METERS_PER_DEGREE_LAT = 111_320.0
private const val MAX_DOTS = 32
private val TWO_PI = (PI * 2.0).toFloat()
