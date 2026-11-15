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
    /** false when written without retrieved guidance (bare LLM mode): no row has a source. */
    val cited: Boolean = true,
)

/** What the result screen shows for one agent run. */
@Immutable
data class DetectionUi(
    val kind: ResultKind,
    val confidence: Float?,
    val alert: AlertUi?,
    /** Wingbeat fundamental from the detector, shown as "Peak" on the result panel. */
    val peakHz: Float? = null,
)

/** Maps a finished [AgentOutcome] onto the existing result and checklist screens. */
object AgentOutcomeMapper {

    fun map(outcome: AgentOutcome): DetectionUi {
        val detection = outcome.trace.detection
        // No detection in the trace means the speech gate deleted the clip before the detector ran.
        val kind = detection?.let(ResultKind::from) ?: ResultKind.VoiceHeard
        // Only a positive Aedes vote is shown as a percentage. A low Aedes vote is not evidence of
        // "not Aedes": on recordings unlike the training sites (a new room or phone) the neighbours
        // vote by recording setup, so 1 - aedesScore would read as false certainty (e.g. 91%).
        val confidence = when (kind) {
            ResultKind.LikelyAedes -> detection?.aedesScore
            else -> null
        }?.coerceIn(0f, 1f)
        val alert = (outcome as? AgentOutcome.AlertReady)?.let { toAlertUi(it.alert, it.trace.provider) }
        // The peak is only meaningful when a wingbeat was heard.
        val peak = detection?.dominantFrequencyHz?.takeIf { kind == ResultKind.LikelyAedes || kind == ResultKind.NotDengue }
        return DetectionUi(kind, confidence, alert, peak)
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
        return AlertUi(alert.draft.summary, entries, firstUrl, provider, cited = alert.citations.isNotEmpty())
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
