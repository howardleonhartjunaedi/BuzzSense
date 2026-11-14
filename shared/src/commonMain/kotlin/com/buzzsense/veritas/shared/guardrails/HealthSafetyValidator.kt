package com.buzzsense.veritas.shared.guardrails

import com.buzzsense.veritas.shared.model.AlertDraft
import com.buzzsense.veritas.shared.model.GuidanceChunk

sealed interface ValidationIssue {
    val reason: String

    data class Schema(override val reason: String) : ValidationIssue
    data class UnknownCitation(val chunkId: String) : ValidationIssue {
        override val reason get() = "cites chunk '$chunkId' that was not retrieved"
    }
    data class Diagnosis(val phrase: String) : ValidationIssue {
        override val reason get() = "diagnoses the user ('$phrase')"
    }
    data class Medicine(val phrase: String) : ValidationIssue {
        override val reason get() = "mentions medicine or dosing ('$phrase')"
    }
    data object PromptLeak : ValidationIssue {
        override val reason get() = "canary token leaked"
    }
}

/**
 * Guardrail "Health-safety check" after the LLM: schema, citations inside the
 * retrieved set, no diagnosis, no medicine or dosing. The disclaimer is appended
 * by the agent after validation, so the model never has to produce it.
 */
class HealthSafetyValidator(
    private val minActions: Int = 3,
    private val maxActions: Int = 5,
    private val maxActionLength: Int = 220,
) {
    fun validate(draft: AlertDraft, retrieved: List<GuidanceChunk>, canary: CanaryToken): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()

        if (draft.summary.isBlank()) issues += ValidationIssue.Schema("empty summary")
        if (draft.actions.size !in minActions..maxActions) {
            issues += ValidationIssue.Schema("expected $minActions-$maxActions actions, got ${draft.actions.size}")
        }
        draft.actions.forEach { a ->
            if (a.text.isBlank()) issues += ValidationIssue.Schema("blank action")
            if (a.text.length > maxActionLength) issues += ValidationIssue.Schema("action too long")
        }

        val ids = retrieved.mapTo(HashSet()) { it.id }
        draft.actions.map { it.chunkId }.filterNot { it in ids }.distinct()
            .forEach { issues += ValidationIssue.UnknownCitation(it) }

        val allText = buildString {
            append(draft.summary)
            draft.actions.forEach { append('\n').append(it.text) }
        }
        DIAGNOSIS.firstOrNull { it.containsMatchIn(allText) }?.let {
            issues += ValidationIssue.Diagnosis(it.find(allText)!!.value)
        }
        MEDICINE.firstOrNull { it.containsMatchIn(allText) }?.let {
            issues += ValidationIssue.Medicine(it.find(allText)!!.value)
        }
        if (canary.leakedIn(allText)) issues += ValidationIssue.PromptLeak

        return issues
    }

    private companion object {
        private val I = RegexOption.IGNORE_CASE

        // English + Spanish. Deliberately broad: a false block falls back to the fixed checklist.
        val DIAGNOSIS = listOf(
            Regex("""\b(you|your (child|son|daughter|kid))\s+(have|has|got|probably have)\s+(dengue|zika|chikungunya)""", I),
            Regex("""\b(tienes|tiene|tu hij[oa] tiene)\s+(dengue|zika|chikungu[ny]a)""", I),
            Regex("""\bdiagnos(is|e|ed|tic)\b|\bdiagn[oó]stic""", I),
        )
        val MEDICINE = listOf(
            Regex("""\b(acetaminophen|paracetamol|ibuprofen|ibuprofeno|aspirin|aspirina|naproxen|tylenol|advil|motrin)\b""", I),
            Regex("""\b\d+\s?(mg|ml|mcg)\b""", I),
            Regex("""\b(dose|dosage|dosis|tablet|pill|pastilla|antibiotic|antibi[oó]tico|medication|medicamento|medicine|medicina)s?\b""", I),
        )
    }
}
