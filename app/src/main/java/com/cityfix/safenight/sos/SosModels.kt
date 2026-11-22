package com.cityfix.safenight.sos

/**
 * One line in the SOS progress card.
 */
enum class StepStatus { PENDING, RUNNING, DONE, FAILED }

data class SosStep(
    val label: String,
    val detail: String? = null,
    val status: StepStatus = StepStatus.PENDING
)

/**
 * Where the panic action is routed.
 *
 * Production uses the European emergency number with a DTMF pause menu
 * ([ACTION_CALL] + `112,,1`) so the call lands on a live operator instead of an
 * IVR menu. Hackathon test mode routes the exact same flow to a teammate's
 * phone without touching emergency services.
 */
data class SosTarget(
    val callNumber: String,
    val smsNumber: String,
    val dtmfSuffix: String,
    val isTest: Boolean
) {
    companion object {
        const val EMERGENCY_NUMBER = "112"

        fun emergency(): SosTarget = SosTarget(
            callNumber = EMERGENCY_NUMBER,
            smsNumber = EMERGENCY_NUMBER,
            dtmfSuffix = ",,1",
            isTest = false
        )

        fun test(number: String): SosTarget {
            val clean = number.filter { it.isDigit() || it == '+' }
            return SosTarget(
                callNumber = clean,
                smsNumber = clean,
                dtmfSuffix = "",
                isTest = true
            )
        }
    }
}

/**
 * Result of a panic run, rendered in the SOS overlay.
 */
data class SosReport(
    val steps: List<SosStep>,
    val summary: String,
    val audioFilePath: String?
)
