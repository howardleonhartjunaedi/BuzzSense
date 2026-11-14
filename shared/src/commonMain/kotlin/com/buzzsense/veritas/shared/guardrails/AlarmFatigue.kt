package com.buzzsense.veritas.shared.guardrails

import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/** Where the app records when an alert was last shared (DataStore on Android). */
interface ShareLedger {
    suspend fun lastSharedAt(): Instant?
    suspend fun recordShare(at: Instant)
}

class InMemoryShareLedger : ShareLedger {
    private var last: Instant? = null
    override suspend fun lastSharedAt() = last
    override suspend fun recordShare(at: Instant) { last = at }
}

/**
 * Guardrail "Alarm fatigue": suggest at most one shared alert per day. Repeats are
 * folded into the first; the Share button explains why via [ShareAdvice.reason].
 */
class AlarmFatigueGuard(
    private val ledger: ShareLedger,
    private val window: Duration = 24.hours,
    private val clock: Clock = Clock.System,
) {
    data class ShareAdvice(val suggestShare: Boolean, val reason: String?)

    suspend fun advice(): ShareAdvice {
        val last = ledger.lastSharedAt() ?: return ShareAdvice(true, null)
        val since = clock.now() - last
        return if (since >= window) ShareAdvice(true, null)
        else ShareAdvice(false, "You already shared an alert in the last ${window.inWholeHours} hours. This one is added to it.")
    }

    suspend fun onShared() = ledger.recordShare(clock.now())
}
