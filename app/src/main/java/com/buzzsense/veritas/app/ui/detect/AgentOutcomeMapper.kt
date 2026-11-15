package com.buzzsense.veritas.app.ui.detect

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector
import com.buzzsense.veritas.app.ui.components.ResultKind
import com.buzzsense.veritas.app.ui.screens.ChecklistEntry
import com.buzzsense.veritas.app.ui.theme.BuzzIcons
import com.buzzsense.veritas.shared.model.AgentOutcome
import com.buzzsense.veritas.shared.model.Alert
import com.buzzsense.veritas.shared.model.ProviderTierId

/** The agent's alert, ready for the "What to do now" sheet. Holds text only, never audio. */
@Immutable
data class AlertUi(
    val summary: String,
    val entries: List<ChecklistEntry>,
    /** First cited guidance page, opened by "Open the cited guidance"; null when none is citable. */
    val officialUrl: String?,
    val provider: ProviderTierId?,
)

/** What the result screen shows for one agent run. */
@Immutable
data class DetectionUi(val kind: ResultKind, val confidence: Float?, val alert: AlertUi?)

/** Maps a finished [AgentOutcome] onto the existing result and checklist screens. */
object AgentOutcomeMapper {

    fun map(outcome: AgentOutcome): DetectionUi {
        val detection = outcome.trace.detection
        // No detection in the trace means the speech gate deleted the clip before the detector ran.
        val kind = detection?.let(ResultKind::from) ?: ResultKind.VoiceHeard
        val confidence = when (kind) {
            ResultKind.LikelyAedes -> detection?.aedesScore
            ResultKind.NotDengue -> detection?.aedesScore?.let { 1f - it }
            else -> null
        }?.coerceIn(0f, 1f)
        val alert = (outcome as? AgentOutcome.AlertReady)?.let { toAlertUi(it.alert, it.trace.provider) }
        return DetectionUi(kind, confidence, alert)
    }

    fun toAlertUi(alert: Alert, provider: ProviderTierId?): AlertUi {
        val entries = alert.draft.actions.mapIndexed { i, action ->
            val chunk = alert.citations[action.chunkId]
            ChecklistEntry(
                // Two actions may cite the same chunk, so the row id includes the position.
                id = "$i:${action.chunkId}",
                text = action.text,
                source = chunk?.let { "${it.source} · ${it.title}" },
                icon = iconFor(action.text),
            )
        }
        val firstUrl = alert.draft.actions.firstNotNullOfOrNull { alert.citations[it.chunkId]?.url }
        return AlertUi(alert.draft.summary, entries, firstUrl, provider)
    }

    /** Picks a design-system icon from the action text (English and Spanish keywords). */
    fun iconFor(text: String): ImageVector {
        val t = text.lowercase()
        return when {
            RECYCLE.any { it in t } -> BuzzIcons.Recycling
            WATER.any { it in t } -> BuzzIcons.WaterDrop
            HEALTH.any { it in t } -> BuzzIcons.MedicalServices
            else -> BuzzIcons.Shield // nets, screens, repellent, clothing
        }
    }

    private val RECYCLE = listOf("recycl", "recicl", "tyre", "tire", "llanta", "neumático", "bottle", "botella", "can ")
    private val WATER = listOf("water", "agua", "container", "recipiente", "drain", "vac", "scrub", "puddle", "pool", "charco")
    private val HEALTH = listOf("test", "prueba", "fever", "fiebre", "clinic", "clínica", "doctor", "médico", "health worker", "puskesmas")
}
