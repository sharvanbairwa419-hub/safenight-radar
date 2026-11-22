package com.cityfix.safenight.ui

import androidx.compose.ui.graphics.Color
import com.cityfix.safenight.radar.ZoneLevel
import com.cityfix.safenight.ui.theme.ZoneIsolated
import com.cityfix.safenight.ui.theme.ZoneModerate
import com.cityfix.safenight.ui.theme.ZoneSafe

/** Single source of truth for zone colours across the pill, rings and dots. */
fun zoneColor(zone: ZoneLevel): Color = when (zone) {
    ZoneLevel.SAFE -> ZoneSafe
    ZoneLevel.MODERATE -> ZoneModerate
    ZoneLevel.ISOLATED -> ZoneIsolated
}

fun zoneTitle(zone: ZoneLevel): String = when (zone) {
    ZoneLevel.SAFE -> "SAFE ZONE"
    ZoneLevel.MODERATE -> "MODERATE"
    ZoneLevel.ISOLATED -> "ISOLATED ZONE"
}

fun zoneSubtitle(zone: ZoneLevel, signalCount: Int): String {
    val label = if (signalCount == 1) "SIGNAL" else "SIGNALS"
    return when (zone) {
        ZoneLevel.SAFE -> "$signalCount ACTIVE $label NEARBY"
        ZoneLevel.MODERATE -> "LOW ACTIVITY • $signalCount $label"
        ZoneLevel.ISOLATED -> "0 SIGNALS DETECTED"
    }
}
