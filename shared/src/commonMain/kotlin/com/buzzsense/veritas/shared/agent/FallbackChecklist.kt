package com.buzzsense.veritas.shared.agent

import com.buzzsense.veritas.shared.model.AlertAction
import com.buzzsense.veritas.shared.model.AlertDraft
import com.buzzsense.veritas.shared.model.Language
import com.buzzsense.veritas.shared.model.Urgency

/**
 * The last tier: a fixed checklist written by the team from the guidance corpus,
 * with real chunk ids, so the parent always gets usable guidance even offline or
 * when every LLM answer failed validation.
 *
 * TODO(team): replace the chunk ids with the ids produced by the corpus build script.
 */
class FallbackChecklist(
    private val english: AlertDraft = DEFAULT_EN,
    private val spanish: AlertDraft = DEFAULT_ES,
) {
    fun draft(language: Language): AlertDraft = if (language == Language.ES) spanish else english

    fun chunkIds(language: Language): List<String> = draft(language).actions.map { it.chunkId }.distinct()

    companion object {
        val DEFAULT_EN = AlertDraft(
            summary = "A mosquito was heard indoors. Aedes aegypti can spread dengue, so take these steps today.",
            actions = listOf(
                AlertAction("Empty, scrub and cover anything that holds water inside and around the home.", "cdc-en-breeding-sites-01"),
                AlertAction("Use an EPA-registered insect repellent on exposed skin, following the label.", "cdc-en-repellent-01"),
                AlertAction("Keep window and door screens closed and repair any holes.", "cdc-en-screens-01"),
                AlertAction("Let a child nap under a mosquito net if the room is not screened.", "who-en-bednets-01"),
            ),
            urgency = Urgency.MEDIUM,
        )
        val DEFAULT_ES = AlertDraft(
            summary = "Se escuchó un mosquito dentro de la casa. El Aedes aegypti puede transmitir el dengue; tome estas medidas hoy.",
            actions = listOf(
                AlertAction("Vacíe, restriegue y tape todo lo que acumule agua dentro y alrededor de la casa.", "cdc-es-breeding-sites-01"),
                AlertAction("Use un repelente registrado por la EPA en la piel expuesta, siguiendo la etiqueta.", "cdc-es-repellent-01"),
                AlertAction("Mantenga cerradas las telas metálicas de puertas y ventanas y repare los rotos.", "cdc-es-screens-01"),
                AlertAction("Si el cuarto no tiene telas metálicas, que el niño duerma bajo un mosquitero.", "paho-es-bednets-01"),
            ),
            urgency = Urgency.MEDIUM,
        )
    }
}
