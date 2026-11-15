package com.buzzsense.veritas.shared.rag

import com.buzzsense.veritas.shared.model.DetectionResult
import com.buzzsense.veritas.shared.model.GuidanceChunk
import com.buzzsense.veritas.shared.model.Language
import com.buzzsense.veritas.shared.model.MosquitoGroup
import com.buzzsense.veritas.shared.model.RiskLevel
import com.buzzsense.veritas.shared.model.Weather
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** What the agent's guidance tool needs; [HybridRetriever] is the on-device implementation. */
fun interface GuidanceRetriever {
    suspend fun retrieve(query: String): List<GuidanceChunk>
}

/** Retrieval mode, so eval 7 can compare hybrid against FTS5-only and vector-only. */
enum class RetrievalMode { HYBRID, KEYWORD_ONLY, VECTOR_ONLY }

/**
 * Hybrid search fully on the phone: top [candidates] from FTS5 and from vectors,
 * fused with Reciprocal Rank Fusion (k = [rrfK]), top [topK] to the LLM.
 */
class HybridRetriever(
    private val store: GuidanceStore,
    private val embedder: Embedder,
    private val candidates: Int = 30,
    private val topK: Int = 5,
    private val rrfK: Int = 60,
) : GuidanceRetriever {
    override suspend fun retrieve(query: String): List<GuidanceChunk> = retrieve(query, RetrievalMode.HYBRID)

    suspend fun retrieve(query: String, mode: RetrievalMode): List<GuidanceChunk> = coroutineScope {
        val keyword = async {
            if (mode == RetrievalMode.VECTOR_ONLY) emptyList() else store.keywordSearch(query, candidates)
        }
        val vector = async {
            if (mode == RetrievalMode.KEYWORD_ONLY) emptyList()
            else store.vectorSearch(embedder.embed(query, isQuery = true), candidates)
        }
        val fused = reciprocalRankFusion(listOf(keyword.await(), vector.await()), rrfK).take(topK)
        store.chunks(fused)
    }

    companion object {
        /** score(d) = sum over lists of 1 / (k + rank(d)). Returns chunk ids, best first. */
        fun reciprocalRankFusion(lists: List<List<RankedHit>>, k: Int = 60): List<String> {
            val scores = HashMap<String, Double>()
            lists.forEach { hits -> hits.forEach { h -> scores[h.chunkId] = (scores[h.chunkId] ?: 0.0) + 1.0 / (k + h.rank) } }
            return scores.entries.sortedWith(compareByDescending<Map.Entry<String, Double>> { it.value }.thenBy { it.key })
                .map { it.key }
        }
    }
}

/**
 * Builds the search query from the detection result and weather — never from
 * free text typed by anyone — so retrieval cannot be steered by user input.
 */
object GuidanceQueryBuilder {
    fun build(detection: DetectionResult, weather: Weather?, risk: RiskLevel?, language: Language): String {
        val es = language == Language.ES
        val parts = mutableListOf<String>()
        when (detection.group) {
            MosquitoGroup.AEDES ->
                parts += if (es) "Aedes aegypti mosquito dentro de la casa dengue prevención" else "Aedes aegypti mosquito indoors dengue prevention"
            // Culex bites at night but does not carry malaria: nets and screens, no fever testing.
            MosquitoGroup.CULEX ->
                parts += if (es) "mosquito Culex dentro de la casa pica de noche dormir bajo mosquitero mallas en ventanas"
                else "Culex mosquito indoors night biting sleep under bed net window screens"
            // Possibly a night-biting Anopheles (malaria) mosquito: nets and fever testing too.
            MosquitoGroup.ANOPHELES_OR_OTHER -> {
                parts += if (es) "mosquito dentro de la casa prevención picaduras" else "mosquito indoors prevent bites"
                parts += if (es) "malaria Anopheles dormir bajo mosquitero tratado de noche prueba de malaria si hay fiebre"
                else "malaria Anopheles sleep under treated bed net at night malaria test for fever"
            }
        }
        parts += if (es) "eliminar criaderos agua estancada recipientes mosquiteros repelente"
        else "eliminate breeding sites standing water containers screens repellent"
        if (weather != null && weather.precipitationMm > 0.5) {
            parts += if (es) "después de la lluvia vaciar recipientes" else "after rain empty containers"
        }
        if (weather != null && weather.temperatureC >= 30) parts += if (es) "calor" else "hot weather"
        if (risk == RiskLevel.HIGH) parts += if (es) "brote" else "outbreak"
        return parts.joinToString(" ")
    }
}
